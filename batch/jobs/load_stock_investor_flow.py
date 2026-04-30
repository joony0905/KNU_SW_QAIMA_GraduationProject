#외국인/기관 투자자별 주식 순매수량/금액 일별 데이터 적재 스크립트

#단일 종목:

#  python jobs/load_stock_investor_flow.py --stock-code 005930 --from 2026-04-01 --to 2026-04-30

#  아직 수급 데이터가 없는 종목 100개:

#  python jobs/load_stock_investor_flow.py --only-missing --limit 100 --from 2026-04-01 --to 2026-04-30

#  전체 종목:

#  python jobs/load_stock_investor_flow.py --from 2026-04-01 --to 2026-04-30


##jobs/load_stock_investor_flow.py --help

from __future__ import annotations

import argparse
import json
import logging
import os
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

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
logger = logging.getLogger("load_stock_investor_flow")


DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")
DEFAULT_KIS_INVESTOR_FLOW_PATH = os.getenv(
    "KIS_INVESTOR_FLOW_PATH",
    "/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily",
)
DEFAULT_KIS_INVESTOR_FLOW_TR_ID = os.getenv("KIS_INVESTOR_FLOW_TR_ID", "FHPTJ04160001")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")
DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "300"))
DEFAULT_LIMIT = int(os.getenv("STOCK_INVESTOR_FLOW_LIMIT", "0"))
DEFAULT_REFRESH_TAIL_DAYS = int(os.getenv("STOCK_INVESTOR_FLOW_REFRESH_TAIL_DAYS", "10"))

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

SOURCE = "KIS"
SOURCE_TR_ID = "FHPTJ04160001"
MARKET_DIV_CODE = "J"
BACKFILL_STEP_DAYS = 21

UPSERT_SQL = """
INSERT INTO stock_investor_flow (
    stock_id,
    stock_code,
    trade_date,
    market_div_code,
    close_price,
    accumulated_volume,
    accumulated_trading_value_million,
    foreign_net_buy_qty,
    foreign_net_buy_value_million,
    individual_net_buy_qty,
    individual_net_buy_value_million,
    institution_net_buy_qty,
    institution_net_buy_value_million,
    securities_net_buy_qty,
    securities_net_buy_value_million,
    investment_trust_net_buy_qty,
    investment_trust_net_buy_value_million,
    private_fund_net_buy_qty,
    private_fund_net_buy_value_million,
    bank_net_buy_qty,
    bank_net_buy_value_million,
    insurance_net_buy_qty,
    insurance_net_buy_value_million,
    fund_net_buy_qty,
    fund_net_buy_value_million,
    other_net_buy_qty,
    other_net_buy_value_million,
    source,
    source_tr_id,
    created_at,
    updated_at
) VALUES (
    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
)
ON DUPLICATE KEY UPDATE
    market_div_code = VALUES(market_div_code),
    close_price = VALUES(close_price),
    accumulated_volume = VALUES(accumulated_volume),
    accumulated_trading_value_million = VALUES(accumulated_trading_value_million),
    foreign_net_buy_qty = VALUES(foreign_net_buy_qty),
    foreign_net_buy_value_million = VALUES(foreign_net_buy_value_million),
    individual_net_buy_qty = VALUES(individual_net_buy_qty),
    individual_net_buy_value_million = VALUES(individual_net_buy_value_million),
    institution_net_buy_qty = VALUES(institution_net_buy_qty),
    institution_net_buy_value_million = VALUES(institution_net_buy_value_million),
    securities_net_buy_qty = VALUES(securities_net_buy_qty),
    securities_net_buy_value_million = VALUES(securities_net_buy_value_million),
    investment_trust_net_buy_qty = VALUES(investment_trust_net_buy_qty),
    investment_trust_net_buy_value_million = VALUES(investment_trust_net_buy_value_million),
    private_fund_net_buy_qty = VALUES(private_fund_net_buy_qty),
    private_fund_net_buy_value_million = VALUES(private_fund_net_buy_value_million),
    bank_net_buy_qty = VALUES(bank_net_buy_qty),
    bank_net_buy_value_million = VALUES(bank_net_buy_value_million),
    insurance_net_buy_qty = VALUES(insurance_net_buy_qty),
    insurance_net_buy_value_million = VALUES(insurance_net_buy_value_million),
    fund_net_buy_qty = VALUES(fund_net_buy_qty),
    fund_net_buy_value_million = VALUES(fund_net_buy_value_million),
    other_net_buy_qty = VALUES(other_net_buy_qty),
    other_net_buy_value_million = VALUES(other_net_buy_value_million),
    source_tr_id = VALUES(source_tr_id),
    updated_at = VALUES(updated_at)
"""


@dataclass(frozen=True)
class StockRef:
    stock_id: int
    stock_code: str
    company_name: str = ""
    exchange_code: str = "KOSPI"


@dataclass
class BootstrapResult:
    total: int
    success: int
    partial: int
    hard_fail: int
    elapsed_sec: float
    partial_codes: List[Tuple[str, str]]
    hard_failed_codes: List[Tuple[str, str]]


class KisClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        investor_flow_path: str = DEFAULT_KIS_INVESTOR_FLOW_PATH,
        investor_flow_tr_id: str = DEFAULT_KIS_INVESTOR_FLOW_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.investor_flow_path = investor_flow_path
        self.investor_flow_tr_id = investor_flow_tr_id
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
            access_token = data.get("access_token")
            expires_at = data.get("expires_at")
            if access_token and expires_at and time.time() < float(expires_at):
                token_value = str(access_token).removeprefix("Bearer ").strip()
                self.access_token = token_value
                self.token_issued_at = float(data.get("issued_at") or time.time())
                self.token_ttl_sec = max(60, int(float(expires_at) - self.token_issued_at))
        except Exception:
            logger.warning("Failed to load cached KIS token; will issue a new one")

    def _save_cached_token(self, access_token: str, token_ttl_sec: int) -> None:
        try:
            issued_at = time.time()
            with open(self.token_cache_file, "w", encoding="utf-8") as fp:
                json.dump(
                    {"access_token": access_token, "issued_at": issued_at, "expires_at": issued_at + token_ttl_sec},
                    fp,
                )
        except Exception as exc:
            logger.warning("Failed to save KIS token cache: %s", exc)

    def issue_token(self) -> str:
        url = f"{self.base_url}{self.token_path}"
        resp = self.session.post(
            url,
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
            "tr_id": self.investor_flow_tr_id,
            "custtype": self.custtype,
        }

    def fetch_investor_flow(self, stock_code: str, base_date: date) -> List[dict]:
        url = f"{self.base_url}{self.investor_flow_path}"
        params = {
            "FID_COND_MRKT_DIV_CODE": MARKET_DIV_CODE,
            "FID_INPUT_ISCD": normalize_stock_code(stock_code),
            "FID_INPUT_DATE_1": yyyymmdd(base_date),
            "FID_ORG_ADJ_PRC": "",
            "FID_ETC_CLS_CODE": "1",
        }
        resp = self.session.get(url, headers=self.auth_headers(), params=params, timeout=self.timeout_sec)
        resp.raise_for_status()
        data = resp.json()
        if str(data.get("rt_cd")) != "0":
            raise RuntimeError(f"KIS investor flow error: {data}")
        return data.get("output2") or []


def parse_iso_date(value: str) -> date:
    return datetime.strptime(value, "%Y-%m-%d").date()


def yyyymmdd(value: date) -> str:
    return value.strftime("%Y%m%d")


def normalize_stock_code(stock_code: str) -> str:
    value = stock_code.strip().replace(".XKRX", "").replace(".XKOS", "")
    return f"{int(value):06d}" if value.isdigit() else value


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
    raw = str(value).strip()
    if not raw:
        return None
    try:
        return datetime.strptime(raw, "%Y%m%d").date()
    except ValueError:
        return None


def backfill_base_dates(from_date: date, to_date: date) -> Iterable[date]:
    cursor = to_date
    seen = set()
    while cursor >= from_date:
        if cursor not in seen:
            seen.add(cursor)
            yield cursor
        cursor = cursor - timedelta(days=BACKFILL_STEP_DAYS)
    if from_date not in seen:
        yield from_date


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


def to_params(stock: StockRef, row: dict, from_date: date, to_date: date, now_sql: str) -> Optional[Tuple]:
    trade_date = parse_kis_date(row.get("stck_bsop_date"))
    if trade_date is None or trade_date < from_date or trade_date > to_date:
        return None
    return (
        stock.stock_id,
        stock.stock_code,
        trade_date.isoformat(),
        MARKET_DIV_CODE,
        parse_decimal(row.get("stck_clpr")),
        parse_decimal(row.get("acml_vol")),
        parse_decimal(row.get("acml_tr_pbmn")),
        parse_decimal(row.get("frgn_ntby_qty")),
        parse_decimal(row.get("frgn_ntby_tr_pbmn")),
        parse_decimal(row.get("prsn_ntby_qty")),
        parse_decimal(row.get("prsn_ntby_tr_pbmn")),
        parse_decimal(row.get("orgn_ntby_qty")),
        parse_decimal(row.get("orgn_ntby_tr_pbmn")),
        parse_decimal(row.get("scrt_ntby_qty")),
        parse_decimal(row.get("scrt_ntby_tr_pbmn")),
        parse_decimal(row.get("ivtr_ntby_qty")),
        parse_decimal(row.get("ivtr_ntby_tr_pbmn")),
        parse_decimal(row.get("pe_fund_ntby_vol")),
        parse_decimal(row.get("pe_fund_ntby_tr_pbmn")),
        parse_decimal(row.get("bank_ntby_qty")),
        parse_decimal(row.get("bank_ntby_tr_pbmn")),
        parse_decimal(row.get("insu_ntby_qty")),
        parse_decimal(row.get("insu_ntby_tr_pbmn")),
        parse_decimal(row.get("fund_ntby_qty")),
        parse_decimal(row.get("fund_ntby_tr_pbmn")),
        parse_decimal(row.get("etc_ntby_qty")),
        parse_decimal(row.get("etc_ntby_tr_pbmn")),
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


class StockInvestorFlowRepository:
    def __init__(self, conn: MySQLConnection) -> None:
        self.conn = conn

    def list_target_stocks(
        self,
        stock_code: Optional[str] = None,
        only_missing: bool = False,
        limit: int = 0,
    ) -> List[StockRef]:
        where_clauses = [
            "s.delisted_at IS NULL",
            "s.asset_type = 'EQUITY'",
        ]
        params: List[Any] = []

        if stock_code:
            where_clauses.append("s.stock_code = %s")
            params.append(normalize_stock_code(stock_code))

        if only_missing:
            where_clauses.append("""
            NOT EXISTS (
                SELECT 1
                FROM stock_investor_flow f
                WHERE f.stock_id = s.stock_id
            )
            """)

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

        cur = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, tuple(params))
            return [
                StockRef(
                    stock_id=int(row["stock_id"]),
                    stock_code=str(row["stock_code"]),
                    company_name=str(row.get("company_name") or ""),
                    exchange_code=str(row.get("exchange_code") or "KOSPI"),
                )
                for row in cur.fetchall()
            ]
        finally:
            cur.close()

    def get_latest_trade_date(self, stock_id: int) -> Optional[date]:
        sql = """
        SELECT MAX(trade_date) AS latest_trade_date
        FROM stock_investor_flow
        WHERE stock_id = %s
        """
        cur = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, (stock_id,))
            row = cur.fetchone()
            if not row:
                return None
            latest = row.get("latest_trade_date")
            if isinstance(latest, datetime):
                return latest.date()
            return latest
        finally:
            cur.close()

    def upsert_flow_rows(self, stock: StockRef, rows: Sequence[dict], from_date: date, to_date: date) -> int:
        now_sql = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")
        params = [
            p for p in (to_params(stock, row, from_date, to_date, now_sql) for row in rows)
            if p is not None
        ]
        return upsert_rows(self.conn, params)


def decide_fetch_range(
    latest_trade_date: Optional[date],
    requested_from: date,
    requested_to: date,
    refresh_tail_days: int,
) -> Tuple[date, date, str]:
    if latest_trade_date is None:
        return requested_from, requested_to, "BACKFILL"
    if latest_trade_date >= requested_to:
        return latest_trade_date, requested_to, "SKIP"
    start = max(requested_from, latest_trade_date - timedelta(days=2), requested_to - timedelta(days=refresh_tail_days))
    return start, requested_to, "REFRESH"


def load_stock_investor_flow(
    kis: KisClient,
    repo: StockInvestorFlowRepository,
    stock_code: Optional[str],
    from_date: date,
    to_date: date,
    only_missing: bool,
    limit: int,
    sleep_ms: int,
    refresh_tail_days: int,
) -> BootstrapResult:
    stocks = repo.list_target_stocks(stock_code=stock_code, only_missing=only_missing, limit=limit)
    total = len(stocks)
    logger.info("Loaded %d target stocks", total)

    success = 0
    partial = 0
    hard_fail = 0
    partial_codes: List[Tuple[str, str]] = []
    hard_failed_codes: List[Tuple[str, str]] = []
    start_ts = time.time()

    for idx, stock in enumerate(stocks):
        try:
            latest_trade_date = repo.get_latest_trade_date(stock.stock_id)
            fetch_from, fetch_to, mode = decide_fetch_range(
                latest_trade_date=latest_trade_date,
                requested_from=from_date,
                requested_to=to_date,
                refresh_tail_days=refresh_tail_days,
            )
            if mode == "SKIP":
                partial += 1
                partial_codes.append((stock.stock_code, "already up-to-date"))
                continue

            raw_count = 0
            saved_count = 0
            for base_date in backfill_base_dates(fetch_from, fetch_to):
                rows = kis.fetch_investor_flow(stock.stock_code, base_date)
                raw_count += len(rows)
                saved_count += repo.upsert_flow_rows(stock, rows, fetch_from, fetch_to)
                if sleep_ms > 0:
                    time.sleep(sleep_ms / 1000.0)

            if saved_count <= 0:
                partial += 1
                partial_codes.append((stock.stock_code, f"empty investor flow mode={mode} rawRows={raw_count}"))
                logger.warning(
                    "[%d/%d] PARTIAL stock_code=%s company=%s exchange=%s mode=%s rawRows=%d",
                    idx + 1,
                    total,
                    stock.stock_code,
                    stock.company_name,
                    stock.exchange_code,
                    mode,
                    raw_count,
                )
            else:
                success += 1
                logger.info(
                    "[%d/%d] SUCCESS stock_code=%s company=%s exchange=%s mode=%s upserted=%d",
                    idx + 1,
                    total,
                    stock.stock_code,
                    stock.company_name,
                    stock.exchange_code,
                    mode,
                    saved_count,
                )
        except Exception as exc:
            hard_fail += 1
            hard_failed_codes.append((stock.stock_code, str(exc)))
            logger.exception(
                "[%d/%d] HARD-FAIL stock_code=%s company=%s exchange=%s",
                idx + 1,
                total,
                stock.stock_code,
                stock.company_name,
                stock.exchange_code,
            )

        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000.0)

    elapsed = time.time() - start_ts
    logger.info("===================================================")
    logger.info("Stock investor flow load finished")
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Load KIS stock investor flow into stock_investor_flow")
    parser.add_argument("--stock-code", default=None, help="Single stock code to load")
    parser.add_argument("--from", dest="from_date", required=True, type=parse_iso_date)
    parser.add_argument("--to", dest="to_date", required=True, type=parse_iso_date)
    parser.add_argument(
        "--only-missing",
        action="store_true",
        help="Load only stocks with no stock_investor_flow rows",
    )
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT, help="Max number of stocks to process")
    parser.add_argument("--sleep-ms", type=int, default=DEFAULT_SLEEP_MS, help="Sleep milliseconds between requests")
    parser.add_argument(
        "--refresh-tail-days",
        type=int,
        default=DEFAULT_REFRESH_TAIL_DAYS,
        help="Refresh tail days when latest row already exists",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.from_date > args.to_date:
        raise SystemExit("--from must be before or equal to --to")

    kis = KisClient(
        app_key=required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY),
        app_secret=required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET),
    )
    conn = connect_db()
    try:
        repo = StockInvestorFlowRepository(conn)
        load_stock_investor_flow(
            kis=kis,
            repo=repo,
            stock_code=args.stock_code,
            from_date=args.from_date,
            to_date=args.to_date,
            only_missing=args.only_missing,
            limit=args.limit,
            sleep_ms=args.sleep_ms,
            refresh_tail_days=args.refresh_tail_days,
        )
    finally:
        conn.close()


if __name__ == "__main__":
    main()
