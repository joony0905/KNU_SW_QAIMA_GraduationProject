# KIS 시장별 투자자별 매매동향 데이터 적재 스크립트

#python jobs/load_market_investor_flow.py --from 2026-04-01 --to 2026-04-30

#jobs/load_market_investor_flow.py --help

from __future__ import annotations

import argparse
import json
import logging
import os
import time
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, List, Optional, Sequence, Tuple

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import requests

try:
    import mysql.connector
    from mysql.connector.connection import MySQLConnection
except ImportError:
    mysql = None
    MySQLConnection = Any


logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_market_investor_flow")


DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")
DEFAULT_KIS_MARKET_FLOW_PATH = os.getenv(
    "KIS_MARKET_FLOW_PATH",
    "/uapi/domestic-stock/v1/quotations/inquire-investor-daily-by-market",
)
DEFAULT_KIS_MARKET_FLOW_TR_ID = os.getenv("KIS_MARKET_FLOW_TR_ID", "FHPTJ04040000")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")
DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "300"))

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

SOURCE = "KIS"
SOURCE_TR_ID = "FHPTJ04040000"
DEFAULT_INDUSTRY_CODE = "0000"

UPSERT_SQL = """
INSERT INTO market_investor_flow (
    market_code,
    industry_code,
    trade_date,
    foreign_net_buy_qty,
    foreign_net_buy_value_million,
    individual_net_buy_qty,
    individual_net_buy_value_million,
    institution_net_buy_qty,
    institution_net_buy_value_million,
    source,
    source_tr_id,
    created_at,
    updated_at
) VALUES (
    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
)
ON DUPLICATE KEY UPDATE
    foreign_net_buy_qty = VALUES(foreign_net_buy_qty),
    foreign_net_buy_value_million = VALUES(foreign_net_buy_value_million),
    individual_net_buy_qty = VALUES(individual_net_buy_qty),
    individual_net_buy_value_million = VALUES(individual_net_buy_value_million),
    institution_net_buy_qty = VALUES(institution_net_buy_qty),
    institution_net_buy_value_million = VALUES(institution_net_buy_value_million),
    source_tr_id = VALUES(source_tr_id),
    updated_at = VALUES(updated_at)
"""


class KisClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        market_flow_path: str = DEFAULT_KIS_MARKET_FLOW_PATH,
        market_flow_tr_id: str = DEFAULT_KIS_MARKET_FLOW_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.market_flow_path = market_flow_path
        self.market_flow_tr_id = market_flow_tr_id
        self.custtype = custtype
        self.timeout_sec = timeout_sec
        self.token_cache_file = token_cache_file
        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.token_issued_at: Optional[float] = None
        self.token_ttl_sec = 3600
        self._load_cached_token()

    def _load_cached_token(self) -> None:
        try:
            if not os.path.exists(self.token_cache_file):
                return
            with open(self.token_cache_file, "r", encoding="utf-8") as fp:
                data = json.load(fp)
            token = data.get("access_token")
            expires_at = data.get("expires_at")
            if token and expires_at and time.time() < float(expires_at):
                self.access_token = str(token).removeprefix("Bearer ").strip()
                self.token_issued_at = float(data.get("issued_at") or time.time())
                self.token_ttl_sec = max(60, int(float(expires_at) - self.token_issued_at))
        except Exception:
            logger.warning("Failed to load cached KIS token; will issue a new one")

    def _save_cached_token(self, access_token: str, token_ttl_sec: int) -> None:
        try:
            issued_at = time.time()
            with open(self.token_cache_file, "w", encoding="utf-8") as fp:
                json.dump({"access_token": access_token, "issued_at": issued_at, "expires_at": issued_at + token_ttl_sec}, fp)
        except Exception as exc:
            logger.warning("Failed to save KIS token cache: %s", exc)

    def issue_token(self) -> str:
        resp = self.session.post(
            f"{self.base_url}{self.token_path}",
            json={"grant_type": "client_credentials", "appkey": self.app_key, "appsecret": self.app_secret},
            headers={"content-type": "application/json; charset=UTF-8"},
            timeout=self.timeout_sec,
        )
        resp.raise_for_status()
        data = resp.json()
        token = data.get("access_token")
        if not token:
            raise RuntimeError(f"KIS token issuance failed: {data}")
        ttl = int(data.get("expires_in") or 3600)
        self.access_token = token
        self.token_issued_at = time.time()
        self.token_ttl_sec = ttl
        self._save_cached_token(token, ttl)
        return token

    def auth_headers(self) -> Dict[str, str]:
        if not self.access_token:
            self.issue_token()
        elif self.token_issued_at and time.time() - self.token_issued_at >= self.token_ttl_sec - 5:
            self.issue_token()
        return {
            "content-type": "application/json; charset=UTF-8",
            "authorization": f"Bearer {self.access_token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": self.market_flow_tr_id,
            "custtype": self.custtype,
        }

    def fetch_market_flow(self, market_code: str, industry_code: str, from_date: date, to_date: date) -> List[dict]:
        params = {
            "FID_COND_MRKT_DIV_CODE": "U",
            "FID_INPUT_ISCD": industry_code,
            "FID_INPUT_DATE_1": yyyymmdd(from_date),
            "FID_INPUT_ISCD_1": normalize_market_code(market_code),
            "FID_INPUT_DATE_2": yyyymmdd(to_date),
            "FID_INPUT_ISCD_2": industry_code,
        }
        resp = self.session.get(
            f"{self.base_url}{self.market_flow_path}",
            headers=self.auth_headers(),
            params=params,
            timeout=self.timeout_sec,
        )
        resp.raise_for_status()
        data = resp.json()
        if str(data.get("rt_cd")) != "0":
            raise RuntimeError(f"KIS market investor flow error: {data}")
        return data.get("output") or []


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load KIS market investor flow into market_investor_flow")
    parser.add_argument("--market-code", action="append", default=[], help="KSP or KSQ. Can be repeated. Default: both")
    parser.add_argument("--industry-code", default=DEFAULT_INDUSTRY_CODE)
    parser.add_argument("--from", dest="from_date", required=True, type=parse_iso_date)
    parser.add_argument("--to", dest="to_date", required=True, type=parse_iso_date)
    parser.add_argument("--sleep-ms", type=int, default=DEFAULT_SLEEP_MS)
    return parser.parse_args()


def parse_iso_date(value: str) -> date:
    return datetime.strptime(value, "%Y-%m-%d").date()


def yyyymmdd(value: date) -> str:
    return value.strftime("%Y%m%d")


def normalize_market_code(value: str) -> str:
    normalized = value.strip().upper()
    if normalized == "KOSPI":
        return "KSP"
    if normalized == "KOSDAQ":
        return "KSQ"
    return normalized


def parse_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    raw = str(value).strip().replace(",", "")
    if not raw:
        return None
    try:
        return Decimal(raw)
    except InvalidOperation:
        return None


def parse_kis_date(value: Any) -> Optional[date]:
    if value is None:
        return None
    try:
        return datetime.strptime(str(value).strip(), "%Y%m%d").date()
    except ValueError:
        return None


def connect_db() -> MySQLConnection:
    if mysql is None:
        raise SystemExit("Missing Python dependency: mysql-connector-python")
    return mysql.connector.connect(
        host=DEFAULT_DB_HOST,
        port=DEFAULT_DB_PORT,
        user=DEFAULT_DB_USER,
        password=DEFAULT_DB_PASSWORD,
        database=DEFAULT_DB_NAME,
        autocommit=False,
    )


def to_params(market_code: str, industry_code: str, row: dict, now_sql: str) -> Optional[Tuple]:
    trade_date = parse_kis_date(row.get("stck_bsop_date"))
    if trade_date is None:
        return None
    return (
        market_code,
        industry_code,
        trade_date.isoformat(),
        parse_decimal(row.get("frgn_ntby_qty")),
        parse_decimal(row.get("frgn_ntby_tr_pbmn")),
        parse_decimal(row.get("prsn_ntby_qty")),
        parse_decimal(row.get("prsn_ntby_tr_pbmn")),
        parse_decimal(row.get("orgn_ntby_qty")),
        parse_decimal(row.get("orgn_ntby_tr_pbmn")),
        SOURCE,
        SOURCE_TR_ID,
        now_sql,
        now_sql,
    )


def upsert_rows(conn: MySQLConnection, params: Sequence[Tuple]) -> int:
    if not params:
        return 0
    cur = conn.cursor()
    try:
        cur.executemany(UPSERT_SQL, list(params))
        conn.commit()
        return len(params)
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()


def required_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def main() -> None:
    args = parse_args()
    if args.from_date > args.to_date:
        raise SystemExit("--from must be before or equal to --to")

    market_codes = [normalize_market_code(code) for code in args.market_code] or ["KSP", "KSQ"]
    industry_code = args.industry_code.strip() or DEFAULT_INDUSTRY_CODE
    kis = KisClient(
        app_key=required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY),
        app_secret=required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET),
    )
    conn = connect_db()
    try:
        total = 0
        for market_code in market_codes:
            rows = kis.fetch_market_flow(market_code, industry_code, args.from_date, args.to_date)
            now_sql = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")
            params = [p for p in (to_params(market_code, industry_code, row, now_sql) for row in rows) if p is not None]
            saved = upsert_rows(conn, params)
            total += saved
            logger.info("marketCode=%s industryCode=%s rawRows=%d upserted=%d total=%d",
                        market_code, industry_code, len(rows), saved, total)
            if args.sleep_ms > 0:
                time.sleep(args.sleep_ms / 1000.0)
        logger.info("market investor flow import done. totalUpserted=%d", total)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
