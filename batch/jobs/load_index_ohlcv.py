from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import mysql.connector
import requests
from mysql.connector.connection import MySQLConnection
from mysql.connector.cursor import MySQLCursorDict


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("load_index_ohlcv")


DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")
DEFAULT_KIS_INDEX_CHART_PATH = os.getenv(
    "KIS_INDEX_CHART_PATH",
    "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice",
)
DEFAULT_KIS_INDEX_CHART_TR_ID = os.getenv("KIS_INDEX_CHART_TR_ID", "FHKUP03500100")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "0"))
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")

DEFAULT_FREQ_ONE_D = int(os.getenv("PRICE_OHLCV_FREQ_ONE_D", "4"))
DEFAULT_LOOKBACK_DAYS = int(os.getenv("INDEX_OHLCV_LOOKBACK_DAYS", "1095"))
DEFAULT_REFRESH_TAIL_DAYS = int(os.getenv("INDEX_OHLCV_REFRESH_TAIL_DAYS", "10"))
DEFAULT_LIMIT = int(os.getenv("INDEX_OHLCV_LIMIT", "0"))
DEFAULT_MAX_DAILY_RANGE_DAYS = int(os.getenv("KIS_MAX_INDEX_RANGE_DAYS", "100"))
DEFAULT_MIN_INTERVAL_MS = int(os.getenv("BOOTSTRAP_MIN_INTERVAL_MS", "300"))
DEFAULT_RATE_LIMIT_BACKOFF_SEC = int(os.getenv("BOOTSTRAP_RATE_LIMIT_BACKOFF_SEC", "30"))
DEFAULT_UPSERT_BATCH_SIZE = int(os.getenv("INDEX_OHLCV_UPSERT_BATCH_SIZE", "300"))


@dataclass
class CandleRow:
    trade_date: date
    open_price: Optional[Decimal]
    high_price: Optional[Decimal]
    low_price: Optional[Decimal]
    close_price: Optional[Decimal]
    volume: Optional[Decimal]


@dataclass
class BootstrapResult:
    total: int
    success: int
    partial: int
    hard_fail: int
    elapsed_sec: float
    partial_codes: List[Tuple[str, str]]
    hard_failed_codes: List[Tuple[str, str]]


def required_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if not value:
        raise ValueError(f"Missing required environment variable: {name}")
    return value


def parse_yyyymmdd(value: Any) -> Optional[date]:
    if value is None:
        return None
    s = str(value).strip()
    if not s or s in {"0", "00000000", "nan", "None"}:
        return None
    try:
        return datetime.strptime(s, "%Y%m%d").date()
    except ValueError:
        return None


def to_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    if not s or s.lower() in {"nan", "none"}:
        return None
    try:
        return Decimal(s)
    except Exception:
        return None


def yyyymmdd(d: date) -> str:
    return d.strftime("%Y%m%d")


def dedupe_candles(rows: List[CandleRow]) -> List[CandleRow]:
    by_date: Dict[date, CandleRow] = {}
    for row in rows:
        by_date[row.trade_date] = row
    result = list(by_date.values())
    result.sort(key=lambda x: x.trade_date)
    return result


class KisClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        index_chart_path: str = DEFAULT_KIS_INDEX_CHART_PATH,
        index_chart_tr_id: str = DEFAULT_KIS_INDEX_CHART_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.index_chart_path = index_chart_path
        self.index_chart_tr_id = index_chart_tr_id
        self.custtype = custtype
        self.timeout_sec = timeout_sec
        self.token_cache_file = token_cache_file

        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.token_issued_at: Optional[float] = None
        self.token_ttl_sec: int = 3600

        self._load_cached_token()

    def _load_cached_token(self) -> None:
        try:
            if not os.path.exists(self.token_cache_file):
                return

            with open(self.token_cache_file, "r", encoding="utf-8") as fp:
                data = json.load(fp)

            access_token = data.get("access_token")
            issued_at = data.get("issued_at")
            expires_at = data.get("expires_at")

            if access_token and expires_at and time.time() < float(expires_at):
                token_value = str(access_token).strip()
                if token_value.lower().startswith("bearer "):
                    token_value = token_value[7:].strip()
                self.access_token = token_value
                self.token_issued_at = float(issued_at) if issued_at else time.time()
                self.token_ttl_sec = max(60, int(float(expires_at) - self.token_issued_at))
        except Exception:
            logger.warning("Failed to load cached KIS token; will issue a new one")

    def _save_cached_token(self, access_token: str, token_ttl_sec: int) -> None:
        try:
            issued_at = time.time()
            expires_at = issued_at + token_ttl_sec
            with open(self.token_cache_file, "w", encoding="utf-8") as fp:
                json.dump(
                    {
                        "access_token": access_token,
                        "issued_at": issued_at,
                        "expires_at": expires_at,
                    },
                    fp,
                )
        except Exception as e:
            logger.warning("Failed to save cached KIS token: %s", e)

    def issue_token(self) -> str:
        url = f"{self.base_url}{self.token_path}"
        payload = {
            "grant_type": "client_credentials",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
        }
        headers = {"content-type": "application/json; charset=UTF-8"}

        resp = self.session.post(url, json=payload, headers=headers, timeout=self.timeout_sec)
        resp.raise_for_status()
        data = resp.json()

        access_token = data.get("access_token")
        if not access_token:
            raise RuntimeError(f"KIS token issuance failed: {data}")

        expires_in = data.get("expires_in")
        try:
            self.token_ttl_sec = max(60, int(expires_in)) if expires_in is not None else 3600
        except Exception:
            self.token_ttl_sec = 3600

        self.access_token = access_token
        self.token_issued_at = time.time()
        self._save_cached_token(access_token, self.token_ttl_sec)
        return access_token

    def _auth_headers(self) -> Dict[str, str]:
        if not self.access_token:
            self.issue_token()
        elif self.token_issued_at is not None:
            expire_before = 5
            if time.time() - self.token_issued_at >= (self.token_ttl_sec - expire_before):
                self.issue_token()

        return {
            "content-type": "application/json; charset=UTF-8",
            "authorization": f"Bearer {self.access_token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": self.index_chart_tr_id,
            "custtype": self.custtype,
        }

    def fetch_index_page(
        self,
        index_code: str,
        from_date: date,
        to_date: date,
        period: str = "D",
    ) -> List[CandleRow]:
        if len(index_code) < 2:
            raise ValueError(f"Invalid index_code: {index_code}")

        iscd = index_code[1:]
        url = f"{self.base_url}{self.index_chart_path}"
        headers = self._auth_headers()
        params = {
            "FID_COND_MRKT_DIV_CODE": "U",
            "FID_INPUT_ISCD": iscd,
            "FID_INPUT_DATE_1": yyyymmdd(from_date),
            "FID_INPUT_DATE_2": yyyymmdd(to_date),
            "FID_PERIOD_DIV_CODE": period,
        }

        max_attempts = 3
        for attempt in range(1, max_attempts + 1):
            resp = self.session.get(url, headers=headers, params=params, timeout=self.timeout_sec)
            body_preview = resp.text[:2000] if resp.text else ""

            if not resp.ok:
                logger.error(
                    "KIS index HTTP error index_code=%s from=%s to=%s status=%s body=%s",
                    index_code,
                    from_date,
                    to_date,
                    resp.status_code,
                    body_preview,
                )
                if "거래건수" in body_preview and attempt < max_attempts:
                    time.sleep(DEFAULT_RATE_LIMIT_BACKOFF_SEC)
                    continue
                resp.raise_for_status()

            data = resp.json()
            rt_cd = str(data.get("rt_cd", "")).strip()
            if rt_cd == "0":
                break

            msg1 = str(data.get("msg1", "") or "")
            if "거래건수" in msg1 and attempt < max_attempts:
                logger.warning(
                    "KIS index rate limit hit index_code=%s from=%s to=%s attempt=%d/%d msg1=%s",
                    index_code,
                    from_date,
                    to_date,
                    attempt,
                    max_attempts,
                    msg1,
                )
                time.sleep(DEFAULT_RATE_LIMIT_BACKOFF_SEC)
                continue
            raise RuntimeError(
                f"KIS index request failed index_code={index_code} rt_cd={data.get('rt_cd')} "
                f"msg_cd={data.get('msg_cd')} msg1={data.get('msg1')}"
            )
        else:
            raise RuntimeError(f"KIS index request failed after retries for {index_code}")

        output2 = data.get("output2")
        if not isinstance(output2, list):
            return []

        rows: List[CandleRow] = []
        for item in output2:
            trade_date = parse_yyyymmdd(item.get("stck_bsop_date"))
            if trade_date is None:
                continue

            close_price = to_decimal(item.get("bstp_nmix_prpr"))
            if close_price is None:
                continue

            rows.append(
                CandleRow(
                    trade_date=trade_date,
                    open_price=to_decimal(item.get("bstp_nmix_oprc")),
                    high_price=to_decimal(item.get("bstp_nmix_hgpr")),
                    low_price=to_decimal(item.get("bstp_nmix_lwpr")),
                    close_price=close_price,
                    volume=to_decimal(item.get("acml_vol")),
                )
            )

        rows.sort(key=lambda x: x.trade_date)
        return rows

    def fetch_index_full(
        self,
        index_code: str,
        from_date: date,
        to_date: date,
        sleep_ms_between_pages: int = 0,
    ) -> List[CandleRow]:
        all_rows: List[CandleRow] = []
        current_from = from_date
        while current_from <= to_date:
            current_to = min(
                current_from + timedelta(days=DEFAULT_MAX_DAILY_RANGE_DAYS - 1),
                to_date,
            )
            rows = self.fetch_index_page(
                index_code=index_code,
                from_date=current_from,
                to_date=current_to,
                period="D",
            )
            all_rows.extend(rows)
            current_from = current_to + timedelta(days=1)
            if sleep_ms_between_pages > 0:
                time.sleep(sleep_ms_between_pages / 1000.0)
        filtered = [r for r in dedupe_candles(all_rows) if from_date <= r.trade_date <= to_date]
        filtered.sort(key=lambda x: x.trade_date)
        return filtered


class IndustryIndexOhlcvRepository:
    def __init__(self, conn: MySQLConnection) -> None:
        self.conn = conn

    def list_target_indexes(self, index_code: Optional[str], only_missing: bool, limit: int) -> List[Dict[str, Any]]:
        where_clauses = ["1 = 1"]
        params: List[Any] = []

        if index_code:
            where_clauses.append("ii.code = %s")
            params.append(index_code)

        if only_missing:
            where_clauses.append("""
            NOT EXISTS (
                SELECT 1
                FROM industry_index_ohlcv o
                WHERE o.index_id = ii.index_id
                  AND o.freq = %s
            )
            """)
            params.append(DEFAULT_FREQ_ONE_D)

        sql = f"""
        SELECT
            ii.index_id,
            ii.code,
            ii.name,
            ii.provider,
            ii.currency
        FROM industry_index ii
        WHERE {' AND '.join(where_clauses)}
        ORDER BY ii.code
        """
        if limit > 0:
            sql += " LIMIT %s"
            params.append(limit)

        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, tuple(params))
            return cur.fetchall()
        finally:
            cur.close()

    def get_latest_ts(self, index_id: int, freq: int = DEFAULT_FREQ_ONE_D) -> Optional[datetime]:
        sql = """
        SELECT MAX(ts) AS latest_ts
        FROM industry_index_ohlcv
        WHERE index_id = %s
          AND freq = %s
        """
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, (index_id, freq))
            row = cur.fetchone()
            return row.get("latest_ts") if row else None
        finally:
            cur.close()

    def upsert_rows(self, index_id: int, rows: List[CandleRow], freq: int = DEFAULT_FREQ_ONE_D) -> int:
        if not rows:
            return 0

        sql = """
        INSERT INTO industry_index_ohlcv(
            index_id,
            ts,
            freq,
            open,
            high,
            low,
            close,
            volume
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            open = VALUES(open),
            high = VALUES(high),
            low = VALUES(low),
            close = VALUES(close),
            volume = VALUES(volume)
        """

        total = 0
        cur = self.conn.cursor()
        try:
            for start in range(0, len(rows), DEFAULT_UPSERT_BATCH_SIZE):
                chunk = rows[start:start + DEFAULT_UPSERT_BATCH_SIZE]
                params = []
                for r in chunk:
                    ts = datetime.combine(r.trade_date, datetime.min.time())
                    params.append(
                        (index_id, ts, freq, r.open_price, r.high_price, r.low_price, r.close_price, r.volume)
                    )
                cur.executemany(sql, params)
                self.conn.commit()
                total += len(chunk)
            return total
        finally:
            cur.close()


def decide_fetch_range(
    latest_ts: Optional[datetime],
    today: date,
    lookback_days: int,
    refresh_tail_days: int,
) -> Tuple[date, date, str]:
    if latest_ts is None:
        return today - timedelta(days=lookback_days), today, "BACKFILL"

    latest_date = latest_ts.date()
    if latest_date >= today:
        return latest_date, today, "SKIP"

    start_date = max(today - timedelta(days=refresh_tail_days), latest_date - timedelta(days=2))
    return start_date, today, "REFRESH"


def bootstrap_index_ohlcv(
    kis: KisClient,
    repo: IndustryIndexOhlcvRepository,
    index_code: Optional[str],
    only_missing: bool,
    limit: int,
    sleep_ms: int,
    lookback_days: int,
    refresh_tail_days: int,
) -> BootstrapResult:
    indexes = repo.list_target_indexes(index_code=index_code, only_missing=only_missing, limit=limit)
    total = len(indexes)

    logger.info("Loaded %d target indexes", total)

    success = 0
    partial = 0
    hard_fail = 0
    partial_codes: List[Tuple[str, str]] = []
    hard_failed_codes: List[Tuple[str, str]] = []

    start_ts = time.time()
    last_request_ts = 0.0
    min_interval_sec = DEFAULT_MIN_INTERVAL_MS / 1000.0
    today = date.today()

    for idx, row in enumerate(indexes):
        index_id = int(row["index_id"])
        code = str(row["code"])
        name = str(row.get("name") or "")

        now = time.time()
        wait = min_interval_sec - (now - last_request_ts)
        if wait > 0:
            time.sleep(wait)

        try:
            latest_ts = repo.get_latest_ts(index_id, DEFAULT_FREQ_ONE_D)
            fetch_from, fetch_to, mode = decide_fetch_range(
                latest_ts=latest_ts,
                today=today,
                lookback_days=lookback_days,
                refresh_tail_days=refresh_tail_days,
            )

            if mode == "SKIP":
                partial += 1
                partial_codes.append((code, "already up-to-date"))
                continue

            candles = kis.fetch_index_full(
                index_code=code,
                from_date=fetch_from,
                to_date=fetch_to,
                sleep_ms_between_pages=0,
            )
            last_request_ts = time.time()

            if not candles:
                partial += 1
                partial_codes.append((code, f"empty candles mode={mode}"))
                logger.warning(
                    "[%d/%d] PARTIAL index_code=%s name=%s mode=%s reason=empty candles",
                    idx + 1,
                    total,
                    code,
                    name,
                    mode,
                )
            else:
                repo.upsert_rows(index_id=index_id, rows=candles, freq=DEFAULT_FREQ_ONE_D)
                success += 1

        except Exception as e:
            hard_fail += 1
            hard_failed_codes.append((code, str(e)))
            logger.exception(
                "[%d/%d] HARD-FAIL index_code=%s name=%s",
                idx + 1,
                total,
                code,
                name,
            )

        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000.0)

    elapsed = time.time() - start_ts

    logger.info("===================================================")
    logger.info("Industry Index OHLCV bootstrap finished")
    logger.info("total      = %d", total)
    logger.info("success    = %d", success)
    logger.info("partial    = %d", partial)
    logger.info("hard_fail  = %d", hard_fail)
    logger.info("elapsed    = %.2f sec", elapsed)

    if partial_codes:
        logger.info("Partial index codes:")
        for code, reason in partial_codes:
            logger.info(" - %s :: %s", code, reason)

    if hard_failed_codes:
        logger.info("Hard failed index codes:")
        for code, reason in hard_failed_codes:
            logger.info(" - %s :: %s", code, reason)

    return BootstrapResult(
        total=total,
        success=success,
        partial=partial,
        hard_fail=hard_fail,
        elapsed_sec=elapsed,
        partial_codes=partial_codes,
        hard_failed_codes=hard_failed_codes,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Bootstrap QAIMA ONE_D industry_index_ohlcv from KIS index API.")
    parser.add_argument("--index-code", default=None, help="Single index code to load")
    parser.add_argument(
        "--only-missing",
        action="store_true",
        help="Load only indexes with no ONE_D rows in industry_index_ohlcv",
    )
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT, help="Max number of indexes to process")
    parser.add_argument("--sleep-ms", type=int, default=DEFAULT_SLEEP_MS, help="Sleep milliseconds between requests")
    parser.add_argument(
        "--lookback-days",
        type=int,
        default=DEFAULT_LOOKBACK_DAYS,
        help="Initial ONE_D backfill lookback days (default: 1095 = about 3 years)",
    )
    parser.add_argument(
        "--refresh-tail-days",
        type=int,
        default=DEFAULT_REFRESH_TAIL_DAYS,
        help="Refresh tail days when latest row already exists",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    app_key = required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY)
    app_secret = required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET)

    conn = mysql.connector.connect(
        host=DEFAULT_DB_HOST,
        port=DEFAULT_DB_PORT,
        user=DEFAULT_DB_USER,
        password=DEFAULT_DB_PASSWORD,
        database=DEFAULT_DB_NAME,
        charset="utf8mb4",
        use_unicode=True,
        autocommit=False,
    )

    try:
        kis = KisClient(
            app_key=app_key,
            app_secret=app_secret,
            base_url=DEFAULT_KIS_BASE_URL,
            token_path=DEFAULT_KIS_TOKEN_PATH,
            index_chart_path=DEFAULT_KIS_INDEX_CHART_PATH,
            index_chart_tr_id=DEFAULT_KIS_INDEX_CHART_TR_ID,
            custtype=DEFAULT_KIS_CUSTTYPE,
            timeout_sec=DEFAULT_REQUEST_TIMEOUT,
            token_cache_file=DEFAULT_KIS_TOKEN_CACHE_FILE,
        )
        repo = IndustryIndexOhlcvRepository(conn)
        bootstrap_index_ohlcv(
            kis=kis,
            repo=repo,
            index_code=args.index_code,
            only_missing=args.only_missing,
            limit=args.limit,
            sleep_ms=args.sleep_ms,
            lookback_days=args.lookback_days,
            refresh_tail_days=args.refresh_tail_days,
        )
        return 0
    except Exception:
        logger.exception("Industry index OHLCV bootstrap failed")
        return 1
    finally:
        try:
            conn.close()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
