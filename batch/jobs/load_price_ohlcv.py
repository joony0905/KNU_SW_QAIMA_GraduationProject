from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, date, timedelta
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
logger = logging.getLogger(__name__)


# ============================================================
# ENV / CONST
# ============================================================

DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")

DEFAULT_KIS_DAILY_CHART_PATH = os.getenv(
    "KIS_DAILY_CHART_PATH",
    "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
)
DEFAULT_KIS_DAILY_CHART_TR_ID = os.getenv("KIS_DAILY_CHART_TR_ID", "FHKST03010100")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "120"))
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")

# Freq enum:
# 0=ONE_MIN, 1=FIVE_MIN, 2=FIFTEEN_MIN, 3=ONE_H, 4=ONE_D, 5=ONE_W, 6=ONE_M
DEFAULT_FREQ_ONE_D = int(os.getenv("PRICE_OHLCV_FREQ_ONE_D", "4"))

DEFAULT_LOOKBACK_DAYS = int(os.getenv("PRICE_OHLCV_LOOKBACK_DAYS", "400"))
DEFAULT_REFRESH_TAIL_DAYS = int(os.getenv("PRICE_OHLCV_REFRESH_TAIL_DAYS", "10"))
DEFAULT_LIMIT = int(os.getenv("PRICE_OHLCV_LIMIT", "0"))

# KIS 일봉 응답이 보통 최대 100건 수준이므로 paging 기준
DEFAULT_PAGE_SIZE_HINT = int(os.getenv("KIS_DAILY_CHART_PAGE_SIZE_HINT", "100"))
DEFAULT_MIN_INTERVAL_MS = int(os.getenv("BOOTSTRAP_MIN_INTERVAL_MS", "150"))


# ============================================================
# DATA CLASSES
# ============================================================

@dataclass
class CandleRow:
    trade_date: date
    open_price: Optional[float]
    high_price: Optional[float]
    low_price: Optional[float]
    close_price: Optional[float]
    volume: Optional[float]


@dataclass
class BootstrapResult:
    total: int
    success: int
    partial: int
    hard_fail: int
    elapsed_sec: float
    partial_codes: List[Tuple[str, str]]
    hard_failed_codes: List[Tuple[str, str]]


# ============================================================
# HELPERS
# ============================================================

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


def to_float(value: Any) -> Optional[float]:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    if not s or s.lower() in {"nan", "none"}:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def yyyymmdd(d: date) -> str:
    return d.strftime("%Y%m%d")


def choose_market_div(exchange_code: str) -> str:
    code = (exchange_code or "").strip().upper()
    if code == "KOSDAQ":
        return "Q"
    if code == "KONEX":
        return "K"
    return "J"


def dedupe_candles(rows: List[CandleRow]) -> List[CandleRow]:
    by_date: Dict[date, CandleRow] = {}
    for row in rows:
        by_date[row.trade_date] = row
    result = list(by_date.values())
    result.sort(key=lambda x: x.trade_date)
    return result


# ============================================================
# KIS CLIENT
# ============================================================

class KisClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        daily_chart_path: str = DEFAULT_KIS_DAILY_CHART_PATH,
        daily_chart_tr_id: str = DEFAULT_KIS_DAILY_CHART_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.daily_chart_path = daily_chart_path
        self.daily_chart_tr_id = daily_chart_tr_id
        self.custtype = custtype
        self.timeout_sec = timeout_sec
        self.token_cache_file = token_cache_file

        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.token_issued_at: Optional[float] = None
        self.token_ttl_sec: int = 3600

        self._load_cached_token()

        logger.info("KIS base_url=%s", self.base_url)
        logger.info("KIS daily_chart_path=%s", self.daily_chart_path)
        logger.info("KIS daily_chart_tr_id=%s", self.daily_chart_tr_id)

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
                self.access_token = access_token
                self.token_issued_at = float(issued_at) if issued_at else time.time()
                self.token_ttl_sec = max(60, int(float(expires_at) - self.token_issued_at))
                logger.info("Loaded cached KIS token from file")
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

        logger.info("Issuing KIS access token...")

        max_attempts = 5
        for attempt in range(1, max_attempts + 1):
            resp = self.session.post(url, json=payload, headers=headers, timeout=self.timeout_sec)

            if resp.status_code == 403:
                err_data: Dict[str, Any] = {}
                try:
                    err_data = resp.json()
                except Exception:
                    pass

                err_code = err_data.get("error_code")
                err_desc = err_data.get("error_description")

                if err_code == "EGW00133":
                    logger.warning(
                        "KIS token issuance rate-limited (attempt %d/%d): %s",
                        attempt,
                        max_attempts,
                        err_desc,
                    )
                    if attempt < max_attempts:
                        time.sleep(60)
                        continue

                resp.raise_for_status()

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

            self.access_token = f"Bearer {access_token}"
            self.token_issued_at = time.time()
            self._save_cached_token(self.access_token, self.token_ttl_sec)

            logger.info("KIS access token issued successfully (ttl=%s sec)", self.token_ttl_sec)
            return self.access_token

        raise RuntimeError("KIS token issuance failed after maximum retry attempts")

    def _auth_headers(self) -> Dict[str, str]:
        if not self.access_token:
            self.issue_token()
        elif self.token_issued_at is not None:
            expire_before = 5
            if time.time() - self.token_issued_at >= (self.token_ttl_sec - expire_before):
                logger.info("KIS access token near expiry; re-issuing")
                self.issue_token()

        return {
            "content-type": "application/json; charset=UTF-8",
            "authorization": self.access_token,
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": self.daily_chart_tr_id,
            "custtype": self.custtype,
        }

    def fetch_daily_candles_page(
        self,
        stock_code: str,
        market_div_code: str,
        from_date: date,
        to_date: date,
    ) -> List[CandleRow]:
        """
        KIS 단일 호출 1페이지.
        보통 최근 최대 100건 수준만 반환.
        """
        url = f"{self.base_url}{self.daily_chart_path}"
        headers = self._auth_headers()
        params = {
            "FID_COND_MRKT_DIV_CODE": market_div_code,
            "FID_INPUT_ISCD": stock_code,
            "FID_PERIOD_DIV_CODE": "D",
            "FID_INPUT_DATE_1": yyyymmdd(from_date),
            "FID_INPUT_DATE_2": yyyymmdd(to_date),
            "FID_ORG_ADJ_PRC": "0",
        }

        resp = self.session.get(url, headers=headers, params=params, timeout=self.timeout_sec)
        resp.raise_for_status()

        data = resp.json()
        rt_cd = str(data.get("rt_cd", "")).strip()
        if rt_cd != "0":
            raise RuntimeError(f"KIS candle request failed for {stock_code}: {data}")

        output2 = data.get("output2")
        if not isinstance(output2, list):
            return []

        candles: List[CandleRow] = []
        for item in output2:
            trade_date = parse_yyyymmdd(item.get("stck_bsop_date"))
            if trade_date is None:
                continue

            open_price = to_float(item.get("stck_oprc"))
            high_price = to_float(item.get("stck_hgpr"))
            low_price = to_float(item.get("stck_lwpr"))
            close_price = to_float(item.get("stck_clpr"))
            volume = to_float(item.get("acml_vol"))

            if close_price is None:
                continue

            candles.append(
                CandleRow(
                    trade_date=trade_date,
                    open_price=open_price,
                    high_price=high_price,
                    low_price=low_price,
                    close_price=close_price,
                    volume=volume,
                )
            )

        candles.sort(key=lambda x: x.trade_date)
        return candles

    def fetch_daily_candles_full(
        self,
        stock_code: str,
        market_div_code: str,
        from_date: date,
        to_date: date,
        sleep_ms_between_pages: int = 120,
    ) -> List[CandleRow]:
        """
        KIS 100건 제한을 고려해 과거 방향으로 반복 조회.
        """
        all_rows: List[CandleRow] = []
        current_to = to_date
        page_no = 0

        while True:
            page_no += 1
            rows = self.fetch_daily_candles_page(
                stock_code=stock_code,
                market_div_code=market_div_code,
                from_date=from_date,
                to_date=current_to,
            )

            if not rows:
                logger.info(
                    "paging stop stock_code=%s reason=empty page page_no=%d from=%s to=%s",
                    stock_code,
                    page_no,
                    from_date,
                    current_to,
                )
                break

            all_rows.extend(rows)

            oldest = rows[0].trade_date
            newest = rows[-1].trade_date

            logger.info(
                "paging stock_code=%s page_no=%d page_rows=%d oldest=%s newest=%s target_from=%s target_to=%s",
                stock_code,
                page_no,
                len(rows),
                oldest,
                newest,
                from_date,
                current_to,
            )

            # 이미 원하는 시작점 이하까지 내려왔으면 종료
            if oldest <= from_date:
                break

            # 100보다 적게 왔으면 더 과거 데이터가 없을 가능성이 큼
            if len(rows) < DEFAULT_PAGE_SIZE_HINT:
                break

            next_to = oldest - timedelta(days=1)
            if next_to >= current_to:
                break

            current_to = next_to

            if sleep_ms_between_pages > 0:
                time.sleep(sleep_ms_between_pages / 1000.0)

        deduped = dedupe_candles(all_rows)

        # from_date ~ to_date 범위 최종 필터
        filtered = [r for r in deduped if from_date <= r.trade_date <= to_date]
        filtered.sort(key=lambda x: x.trade_date)
        return filtered


# ============================================================
# DB LAYER
# ============================================================

class PriceOhlcvBootstrapRepository:
    def __init__(self, conn: MySQLConnection) -> None:
        self.conn = conn

    def list_target_stocks(
        self,
        stock_code: Optional[str] = None,
        only_missing: bool = False,
        limit: int = 0,
    ) -> List[Dict[str, Any]]:
        where_clauses = [
            "s.delisted_at IS NULL",
            "s.asset_type = 'EQUITY'",
        ]
        params: List[Any] = []

        if stock_code:
            where_clauses.append("s.stock_code = %s")
            params.append(stock_code)

        if only_missing:
            where_clauses.append("""
            NOT EXISTS (
                SELECT 1
                FROM price_ohlcv p
                WHERE p.stock_id = s.stock_id
                  AND p.freq = %s
            )
            """)
            params.append(DEFAULT_FREQ_ONE_D)

        sql = f"""
        SELECT
            s.stock_id,
            s.stock_code,
            s.company_name,
            e.code AS exchange_code
        FROM stock s
        JOIN exchange e ON s.exchange_id = e.exchange_id
        WHERE {' AND '.join(where_clauses)}
        ORDER BY s.stock_code
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

    def get_latest_ts(self, stock_id: int, freq: int = DEFAULT_FREQ_ONE_D) -> Optional[datetime]:
        sql = """
        SELECT MAX(ts) AS latest_ts
        FROM price_ohlcv
        WHERE stock_id = %s
          AND freq = %s
        """
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, (stock_id, freq))
            row = cur.fetchone()
            if not row:
                return None
            return row.get("latest_ts")
        finally:
            cur.close()

    def upsert_price_rows(
        self,
        stock_id: int,
        rows: List[CandleRow],
        freq: int = DEFAULT_FREQ_ONE_D,
    ) -> int:
        if not rows:
            return 0

        sql = """
        INSERT INTO price_ohlcv(
            stock_id,
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

        params: List[Tuple[Any, ...]] = []
        for r in rows:
            ts = datetime.combine(r.trade_date, datetime.min.time())
            params.append(
                (
                    stock_id,
                    ts,
                    freq,
                    r.open_price,
                    r.high_price,
                    r.low_price,
                    r.close_price,
                    r.volume,
                )
            )

        cur = self.conn.cursor()
        try:
            cur.executemany(sql, params)
            self.conn.commit()
            return cur.rowcount
        finally:
            cur.close()


# ============================================================
# BUSINESS LOGIC
# ============================================================

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


def bootstrap_price_ohlcv(
    kis: KisClient,
    repo: PriceOhlcvBootstrapRepository,
    stock_code: Optional[str],
    only_missing: bool,
    limit: int,
    sleep_ms: int,
    lookback_days: int,
    refresh_tail_days: int,
) -> BootstrapResult:
    stocks = repo.list_target_stocks(
        stock_code=stock_code,
        only_missing=only_missing,
        limit=limit,
    )
    total = len(stocks)

    logger.info("Loaded %d target stocks", total)

    success = 0
    partial = 0
    hard_fail = 0

    partial_codes: List[Tuple[str, str]] = []
    hard_failed_codes: List[Tuple[str, str]] = []

    start_ts = time.time()
    last_request_ts = 0.0
    min_interval_sec = DEFAULT_MIN_INTERVAL_MS / 1000.0
    today = date.today()

    for idx, row in enumerate(stocks):
        stock_id = int(row["stock_id"])
        stock_code_value = str(row["stock_code"])
        company_name = str(row.get("company_name") or "")
        exchange_code = str(row.get("exchange_code") or "KOSPI")
        market_div_code = choose_market_div(exchange_code)

        now = time.time()
        wait = min_interval_sec - (now - last_request_ts)
        if wait > 0:
            time.sleep(wait)

        try:
            latest_ts = repo.get_latest_ts(stock_id, DEFAULT_FREQ_ONE_D)
            fetch_from, fetch_to, mode = decide_fetch_range(
                latest_ts=latest_ts,
                today=today,
                lookback_days=lookback_days,
                refresh_tail_days=refresh_tail_days,
            )

            if mode == "SKIP":
                partial += 1
                partial_codes.append((stock_code_value, "already up-to-date"))
                logger.info(
                    "[%d/%d] SKIP stock_code=%s company=%s latest_ts=%s",
                    idx + 1,
                    total,
                    stock_code_value,
                    company_name,
                    latest_ts,
                )
                continue

            candles = kis.fetch_daily_candles_full(
                stock_code=stock_code_value,
                market_div_code=market_div_code,
                from_date=fetch_from,
                to_date=fetch_to,
                sleep_ms_between_pages=max(50, sleep_ms),
            )
            last_request_ts = time.time()

            if not candles:
                partial += 1
                partial_codes.append((stock_code_value, f"empty candles mode={mode}"))
                logger.warning(
                    "[%d/%d] PARTIAL stock_code=%s company=%s exchange=%s mode=%s reason=empty candles",
                    idx + 1,
                    total,
                    stock_code_value,
                    company_name,
                    exchange_code,
                    mode,
                )
            else:
                affected = repo.upsert_price_rows(
                    stock_id=stock_id,
                    rows=candles,
                    freq=DEFAULT_FREQ_ONE_D,
                )
                success += 1
                logger.info(
                    "[%d/%d] OK stock_code=%s company=%s exchange=%s mode=%s candles=%d affected=%d from=%s to=%s",
                    idx + 1,
                    total,
                    stock_code_value,
                    company_name,
                    exchange_code,
                    mode,
                    len(candles),
                    affected,
                    fetch_from,
                    fetch_to,
                )

        except Exception as e:
            hard_fail += 1
            hard_failed_codes.append((stock_code_value, str(e)))
            logger.exception(
                "[%d/%d] HARD-FAIL stock_code=%s company=%s exchange=%s",
                idx + 1,
                total,
                stock_code_value,
                company_name,
                exchange_code,
            )

        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000.0)

    elapsed = time.time() - start_ts

    logger.info("===================================================")
    logger.info("Price OHLCV bootstrap finished")
    logger.info("total      = %d", total)
    logger.info("success    = %d", success)
    logger.info("partial    = %d", partial)
    logger.info("hard_fail  = %d", hard_fail)
    logger.info("elapsed    = %.2f sec", elapsed)

    if partial_codes:
        logger.info("Partial stock codes:")
        for code, reason in partial_codes:
            logger.info(" - %s :: %s", code, reason)

    if hard_failed_codes:
        logger.info("Hard failed stock codes:")
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


# ============================================================
# MAIN
# ============================================================

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Bootstrap QAIMA price_ohlcv from KIS candle API.")
    parser.add_argument("--stock-code", default=None, help="Single stock code to load")
    parser.add_argument(
        "--only-missing",
        action="store_true",
        help="Load only stocks with no ONE_D rows in price_ohlcv",
    )
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT, help="Max number of stocks to process")
    parser.add_argument("--sleep-ms", type=int, default=DEFAULT_SLEEP_MS, help="Sleep milliseconds between requests")
    parser.add_argument(
        "--lookback-days",
        type=int,
        default=DEFAULT_LOOKBACK_DAYS,
        help="Initial backfill lookback days",
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
            daily_chart_path=DEFAULT_KIS_DAILY_CHART_PATH,
            daily_chart_tr_id=DEFAULT_KIS_DAILY_CHART_TR_ID,
            custtype=DEFAULT_KIS_CUSTTYPE,
            timeout_sec=DEFAULT_REQUEST_TIMEOUT,
        )
        repo = PriceOhlcvBootstrapRepository(conn)

        bootstrap_price_ohlcv(
            kis=kis,
            repo=repo,
            stock_code=args.stock_code,
            only_missing=args.only_missing,
            limit=args.limit,
            sleep_ms=args.sleep_ms,
            lookback_days=args.lookback_days,
            refresh_tail_days=args.refresh_tail_days,
        )
        return 0

    except Exception:
        logger.exception("Price OHLCV bootstrap failed")
        return 1

    finally:
        try:
            conn.close()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())