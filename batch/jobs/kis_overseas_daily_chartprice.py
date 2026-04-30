#python jobs\kis_overseas_daily_chartprice.py --market-div N --iscd PCOMP --from-date 2025-04-01 --to-date 2025-04-29 #조회

#python jobs\kis_overseas_daily_chartprice.py --market-div N --iscd COMP --from-date 2025-01-01 --to-date 2026-04-29 --save-index-code COMP --sleep-ms 100  적재 

#jobs/kis_overseas_daily_chartprice.py --help

from __future__ import annotations

import argparse
import csv
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
logger = logging.getLogger("kis_overseas_daily_chartprice")


DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")
DEFAULT_KIS_CHART_PATH = os.getenv(
    "KIS_OVERSEAS_DAILY_CHARTPRICE_PATH",
    "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice",
)
DEFAULT_KIS_CHART_TR_ID = os.getenv("KIS_OVERSEAS_DAILY_CHARTPRICE_TR_ID", "FHKST03030100")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")
DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "0"))
DEFAULT_MIN_INTERVAL_MS = int(os.getenv("BOOTSTRAP_MIN_INTERVAL_MS", "300"))
DEFAULT_RATE_LIMIT_BACKOFF_SEC = int(os.getenv("BOOTSTRAP_RATE_LIMIT_BACKOFF_SEC", "60"))
DEFAULT_UPSERT_BATCH_SIZE = int(os.getenv("INDEX_OHLCV_UPSERT_BATCH_SIZE", "300"))
DEFAULT_MAX_DAILY_RANGE_DAYS = int(os.getenv("KIS_MAX_OVERSEAS_CHART_RANGE_DAYS", "100"))

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")
DEFAULT_FREQ_ONE_D = int(os.getenv("PRICE_OHLCV_FREQ_ONE_D", "4"))


@dataclass
class CandleRow:
    trade_date: date
    open_price: Optional[Decimal]
    high_price: Optional[Decimal]
    low_price: Optional[Decimal]
    close_price: Optional[Decimal]
    volume: Optional[Decimal]


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


def parse_iso_date(value: str) -> date:
    return datetime.strptime(value.strip(), "%Y-%m-%d").date()


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


class KisOverseasChartClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        chart_path: str = DEFAULT_KIS_CHART_PATH,
        chart_tr_id: str = DEFAULT_KIS_CHART_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.chart_path = chart_path
        self.chart_tr_id = chart_tr_id
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

        max_attempts = 5
        for attempt in range(1, max_attempts + 1):
            resp = self.session.post(url, json=payload, headers=headers, timeout=self.timeout_sec)
            if resp.status_code == 403 and attempt < max_attempts:
                body_preview = resp.text[:1000] if resp.text else ""
                if "EGW00133" in body_preview or "접근토큰" in body_preview:
                    logger.warning("KIS token rate-limited attempt=%d/%d body=%s", attempt, max_attempts, body_preview)
                    time.sleep(DEFAULT_RATE_LIMIT_BACKOFF_SEC)
                    continue
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

        raise RuntimeError("KIS token issuance failed after maximum retry attempts")

    def _auth_headers(self) -> Dict[str, str]:
        if not self.access_token:
            self.issue_token()
        elif self.token_issued_at is not None:
            expire_before = 5
            if time.time() - self.token_issued_at >= (self.token_ttl_sec - expire_before):
                self.issue_token()

        return {
            "content-type": "application/json; charset=utf-8",
            "authorization": f"Bearer {self.access_token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": self.chart_tr_id,
            "custtype": self.custtype,
        }

    def fetch_chart(
        self,
        market_div_code: str,
        input_iscd: str,
        from_date: date,
        to_date: date,
        period: str,
    ) -> Tuple[Dict[str, Any], List[CandleRow]]:
        url = f"{self.base_url}{self.chart_path}"
        headers = self._auth_headers()
        params = {
            "FID_COND_MRKT_DIV_CODE": market_div_code,
            "FID_INPUT_ISCD": input_iscd,
            "FID_INPUT_DATE_1": yyyymmdd(from_date),
            "FID_INPUT_DATE_2": yyyymmdd(to_date),
            "FID_PERIOD_DIV_CODE": period,
        }

        resp = self.session.get(url, headers=headers, params=params, timeout=self.timeout_sec)
        body_preview = resp.text[:2000] if resp.text else ""
        if not resp.ok:
            raise RuntimeError(
                f"KIS overseas chart HTTP error market_div={market_div_code} iscd={input_iscd} "
                f"from={from_date} to={to_date} status={resp.status_code} body={body_preview}"
            )

        data = resp.json()
        output2 = data.get("output2")
        output2_size = len(output2) if isinstance(output2, list) else None
        logger.info(
            "KIS overseas chart response market_div=%s iscd=%s from=%s to=%s period=%s rt_cd=%s msg_cd=%s msg1=%s output1=%s output2_size=%s",
            market_div_code,
            input_iscd,
            from_date,
            to_date,
            period,
            data.get("rt_cd"),
            data.get("msg_cd"),
            data.get("msg1"),
            data.get("output1"),
            output2_size,
        )

        if str(data.get("rt_cd", "")).strip() != "0":
            raise RuntimeError(
                f"KIS overseas chart request failed market_div={market_div_code} iscd={input_iscd} "
                f"rt_cd={data.get('rt_cd')} msg_cd={data.get('msg_cd')} msg1={data.get('msg1')}"
            )

        if not isinstance(output2, list):
            return data, []

        rows: List[CandleRow] = []
        for item in output2:
            trade_date = parse_yyyymmdd(item.get("stck_bsop_date"))
            close_price = to_decimal(item.get("ovrs_nmix_prpr"))
            if trade_date is None or close_price is None:
                continue

            rows.append(
                CandleRow(
                    trade_date=trade_date,
                    open_price=to_decimal(item.get("ovrs_nmix_oprc")),
                    high_price=to_decimal(item.get("ovrs_nmix_hgpr")),
                    low_price=to_decimal(item.get("ovrs_nmix_lwpr")),
                    close_price=close_price,
                    volume=to_decimal(item.get("acml_vol")),
                )
            )

        rows.sort(key=lambda x: x.trade_date)
        return data, rows

    def fetch_chart_full(
        self,
        market_div_code: str,
        input_iscd: str,
        from_date: date,
        to_date: date,
        period: str,
        sleep_ms_between_pages: int,
    ) -> Tuple[Dict[str, Any], List[CandleRow]]:
        if period != "D":
            raw, rows = self.fetch_chart(
                market_div_code=market_div_code,
                input_iscd=input_iscd,
                from_date=from_date,
                to_date=to_date,
                period=period,
            )
            return raw, rows

        all_rows: List[CandleRow] = []
        last_raw: Dict[str, Any] = {}
        current_from = from_date
        page_no = 0

        while current_from <= to_date:
            page_no += 1
            current_to = min(current_from + timedelta(days=DEFAULT_MAX_DAILY_RANGE_DAYS - 1), to_date)
            logger.info(
                "KIS overseas chart page=%d market_div=%s iscd=%s from=%s to=%s",
                page_no,
                market_div_code,
                input_iscd,
                current_from,
                current_to,
            )

            raw, rows = self.fetch_chart(
                market_div_code=market_div_code,
                input_iscd=input_iscd,
                from_date=current_from,
                to_date=current_to,
                period=period,
            )
            last_raw = raw
            all_rows.extend(rows)

            current_from = current_to + timedelta(days=1)
            if current_from <= to_date and sleep_ms_between_pages > 0:
                time.sleep(sleep_ms_between_pages / 1000.0)

        filtered = [row for row in dedupe_candles(all_rows) if from_date <= row.trade_date <= to_date]
        filtered.sort(key=lambda x: x.trade_date)
        return last_raw, filtered


class IndustryIndexOhlcvRepository:
    def __init__(self, conn: MySQLConnection) -> None:
        self.conn = conn

    def get_index_id(self, index_code: str) -> Optional[int]:
        sql = "SELECT index_id FROM industry_index WHERE code = %s"
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, (index_code,))
            row = cur.fetchone()
            return int(row["index_id"]) if row else None
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
                    params.append(
                        (
                            index_id,
                            datetime.combine(r.trade_date, datetime.min.time()),
                            freq,
                            r.open_price,
                            r.high_price,
                            r.low_price,
                            r.close_price,
                            r.volume,
                        )
                    )
                cur.executemany(sql, params)
                self.conn.commit()
                total += len(chunk)
            return total
        finally:
            cur.close()


def rows_to_dicts(rows: List[CandleRow]) -> List[Dict[str, Any]]:
    return [
        {
            "date": yyyymmdd(row.trade_date),
            "open": str(row.open_price) if row.open_price is not None else None,
            "high": str(row.high_price) if row.high_price is not None else None,
            "low": str(row.low_price) if row.low_price is not None else None,
            "close": str(row.close_price) if row.close_price is not None else None,
            "volume": str(row.volume) if row.volume is not None else None,
        }
        for row in rows
    ]


def write_json(raw_response: Dict[str, Any], rows: List[CandleRow], path: str) -> None:
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(
            {
                "raw_response": raw_response,
                "rows": rows_to_dicts(rows),
            },
            fp,
            ensure_ascii=False,
            indent=2,
        )


def write_csv(rows: List[CandleRow], path: str) -> None:
    with open(path, "w", encoding="utf-8-sig", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=["date", "open", "high", "low", "close", "volume"])
        writer.writeheader()
        for row in rows_to_dicts(rows):
            writer.writerow(row)


def parse_args() -> argparse.Namespace:
    today = date.today()
    default_from = today - timedelta(days=60)

    parser = argparse.ArgumentParser(description="Fetch KIS overseas stock/index/fx daily chartprice.")
    parser.add_argument(
        "--market-div",
        default="N",
        help="FID_COND_MRKT_DIV_CODE. N=overseas index, X=fx, I=bond, S=gold future",
    )
    parser.add_argument("--iscd", default="PCOMP", help="FID_INPUT_ISCD. Example: PCOMP")
    parser.add_argument("--from-date", default=default_from.isoformat(), help="Start date YYYY-MM-DD")
    parser.add_argument("--to-date", default=today.isoformat(), help="End date YYYY-MM-DD")
    parser.add_argument("--period", default="D", choices=["D", "W", "M", "Y"], help="D/W/M/Y")
    parser.add_argument("--json-out", default=None, help="Optional JSON output path")
    parser.add_argument("--csv-out", default=None, help="Optional CSV output path")
    parser.add_argument("--sleep-ms", type=int, default=DEFAULT_SLEEP_MS, help="Sleep milliseconds between paged requests")
    parser.add_argument(
        "--save-index-code",
        default=None,
        help="Optional industry_index.code to save rows into industry_index_ohlcv, e.g. PCOMP",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    app_key = required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY)
    app_secret = required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET)

    client = KisOverseasChartClient(app_key=app_key, app_secret=app_secret)

    try:
        raw_response, rows = client.fetch_chart_full(
            market_div_code=str(args.market_div).strip().upper(),
            input_iscd=str(args.iscd).strip(),
            from_date=parse_iso_date(args.from_date),
            to_date=parse_iso_date(args.to_date),
            period=str(args.period).strip().upper(),
            sleep_ms_between_pages=max(args.sleep_ms, DEFAULT_MIN_INTERVAL_MS),
        )

        for row in rows:
            print(
                f"{yyyymmdd(row.trade_date)}\t{row.open_price}\t{row.high_price}\t"
                f"{row.low_price}\t{row.close_price}\t{row.volume}"
            )

        if args.json_out:
            write_json(raw_response, rows, args.json_out)
            logger.info("Wrote JSON output: %s", args.json_out)

        if args.csv_out:
            write_csv(rows, args.csv_out)
            logger.info("Wrote CSV output: %s", args.csv_out)

        if args.save_index_code:
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
                repo = IndustryIndexOhlcvRepository(conn)
                index_id = repo.get_index_id(str(args.save_index_code).strip())
                if index_id is None:
                    raise RuntimeError(f"industry_index master is missing for code={args.save_index_code}")
                saved = repo.upsert_rows(index_id=index_id, rows=rows, freq=DEFAULT_FREQ_ONE_D)
                logger.info("Saved %d rows to industry_index_ohlcv index_code=%s", saved, args.save_index_code)
            finally:
                conn.close()

        logger.info("Fetched %d rows", len(rows))
        return 0
    except Exception:
        logger.exception("KIS overseas daily chartprice lookup failed")
        return 1


if __name__ == "__main__":
    sys.exit(main())
