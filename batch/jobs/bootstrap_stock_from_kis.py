# batch/jobs/bootstrap_stock_from_kis.py
from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, date
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import mysql.connector
import pandas as pd
import requests
from mysql.connector.errors import IntegrityError
from mysql.connector.connection import MySQLConnection
from mysql.connector.cursor import MySQLCursorDict


# ============================================================
# Logging
# ============================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("bootstrap_stock_from_kis")


# ============================================================
# Config
# ============================================================

DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")

# Java KrStockClient 기준
DEFAULT_KIS_SEARCH_INFO_PATH = os.getenv(
    "KIS_SEARCH_INFO_PATH",
    "/uapi/domestic-stock/v1/quotations/search-stock-info",
)

# 실전 TR_ID는 환경별로 반드시 맞춰야 함
DEFAULT_KIS_SEARCH_INFO_TR_ID = os.getenv("KIS_SEARCH_INFO_TR_ID", "CTPF1002R")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_SLEEP_MS = int(os.getenv("BOOTSTRAP_SLEEP_MS", "120"))
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")
PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SEED_CSV_CANDIDATES = [
    PROJECT_ROOT / "data" / "financials_kospi.csv",
    PROJECT_ROOT / "data" / "financials_kosdaq.csv",
]
CSV_ENCODING_CANDIDATES = ("utf-8-sig", "utf-8", "cp949")

EXCHANGE_SEED = [
    ("KOSPI", "KOSPI", "Asia/Seoul", "KR"),
    ("KOSDAQ", "KOSDAQ", "Asia/Seoul", "KR"),
    ("KONEX", "KONEX", "Asia/Seoul", "KR"),
]

SECTOR_SCHEME = "KRX_BZTP_M"
INDUSTRY_SCHEME = "KRX_BZTP_S"


# ============================================================
# Data classes
# ============================================================

@dataclass
class KisStockInfo:
    stock_code: str
    company_name: str
    isin: Optional[str]
    listed_at: Optional[date]
    exchange_code: str
    sector_code: Optional[str]
    sector_name: Optional[str]
    industry_code: Optional[str]
    industry_name: Optional[str]


@dataclass
class BootstrapResult:
    total: int
    success_full: int
    success_partial: int
    hard_fail: int
    elapsed_sec: float
    missing_classification_codes: List[Tuple[str, str]]
    hard_failed_codes: List[Tuple[str, str]]


# ============================================================
# Helpers
# ============================================================

def required_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if not value:
        raise ValueError(f"Missing required environment variable: {name}")
    return value


def normalize_stock_code(value: Any) -> Optional[str]:
    if value is None:
        return None
    s = str(value).strip()
    if not s or s.lower() == "nan":
        return None

    if s.endswith(".0"):
        s = s[:-2]

    digits = "".join(ch for ch in s if ch.isdigit())
    if not digits:
        return None

    return digits.zfill(6)


def normalize_market_div(value: Any) -> Optional[str]:
    if value is None:
        return None
    s = str(value).strip().upper()
    if not s:
        return None
    if s in {"J", "Q", "K"}:
        return s
    return s


def infer_market_div_from_path(csv_path: str) -> Optional[str]:
    name = Path(csv_path).name.lower()
    if "kosdaq" in name:
        return "Q"
    if "konex" in name:
        return "K"
    if "kospi" in name:
        return "J"
    return None


def read_csv_with_fallback(csv_path: str) -> pd.DataFrame:
    last_error: Optional[Exception] = None
    for encoding in CSV_ENCODING_CANDIDATES:
        try:
            return pd.read_csv(csv_path, dtype=str, encoding=encoding)
        except UnicodeDecodeError as e:
            last_error = e
    raise RuntimeError(f"Failed to read CSV with supported encodings: {csv_path}") from last_error


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


def pick_first_non_empty(d: Dict[str, Any], keys: List[str]) -> Optional[str]:
    for key in keys:
        value = d.get(key)
        if value is None:
            continue
        s = str(value).strip()
        if s and s.lower() != "nan":
            return s
    return None


def extract_raw_classification_codes(kis_output: Dict[str, Any]) -> Tuple[Optional[str], Optional[str]]:
    sector_code = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_mcls_cd",
            "idx_bztp_mcls_code",
            "mcls_code",
            "mcls_cd",
        ],
    )
    industry_code = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_scls_cd",
            "idx_bztp_scls_code",
            "scls_code",
            "scls_cd",
        ],
    )
    return sector_code, industry_code


def infer_exchange_code(kis_output: Dict[str, Any], csv_market_div: Optional[str]) -> str:
    if csv_market_div:
        mapping = {
            "J": "KOSPI",
            "Q": "KOSDAQ",
            "K": "KONEX",
        }
        if csv_market_div in mapping:
            return mapping[csv_market_div]

    market_name = pick_first_non_empty(
        kis_output,
        [
            "rprs_mrkt_kor_name",
            "market_name",
            "market_nm",
            "mket_name",
            "mket_name_kor",
            "scts_mket_lstg_dt_name",
        ],
    )
    if market_name:
        upper = market_name.upper()
        if "KOSDAQ" in upper:
            return "KOSDAQ"
        if "KONEX" in upper:
            return "KONEX"
        if "KOSPI" in upper:
            return "KOSPI"

        if "코스닥" in market_name:
            return "KOSDAQ"
        if "코넥스" in market_name:
            return "KONEX"
        if "코스피" in market_name or "유가" in market_name:
            return "KOSPI"

    if pick_first_non_empty(kis_output, ["kosdaq_mket_lstg_dt"]):
        return "KOSDAQ"

    if pick_first_non_empty(kis_output, ["konex_mket_lstg_dt", "konex_lstg_dt", "nxt_mket_lstg_dt"]):
        return "KONEX"

    return "KOSPI"


def company_prefix(stock_code: str) -> str:
    return stock_code[:5]


def normalize_company_name_for_backfill(name: Optional[str]) -> str:
    if not name:
        return ""

    s = str(name).strip()

    # 공백 제거
    s = re.sub(r"\s+", "", s)

    # 보통주 표기 제거
    s = s.replace("보통주", "")
    s = s.replace("보통", "")

    # 우선주 계열 제거
    patterns = [
        r"\d+우B$",
        r"\d+우C$",
        r"\d+우$",
        r"우B$",
        r"우C$",
        r"우선주$",
        r"우선$",
        r"우$",
    ]
    for p in patterns:
        s = re.sub(p, "", s)

    return s.strip()


# ============================================================
# KIS Client
# ============================================================

class KisClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        search_info_path: str = DEFAULT_KIS_SEARCH_INFO_PATH,
        search_info_tr_id: str = DEFAULT_KIS_SEARCH_INFO_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.search_info_path = search_info_path
        self.search_info_tr_id = search_info_tr_id
        self.custtype = custtype
        self.timeout_sec = timeout_sec

        self.session = requests.Session()
        self.access_token: Optional[str] = None
        self.token_issued_at: Optional[float] = None
        self.token_ttl_sec: int = 55
        self.token_cache_file = token_cache_file

        self._load_cached_token()

        logger.info("KIS base_url=%s", self.base_url)
        logger.info("KIS search_info_path=%s", self.search_info_path)
        logger.info("KIS search_info_tr_id=%s", self.search_info_tr_id)

    def _load_cached_token(self) -> None:
        try:
            if os.path.exists(self.token_cache_file):
                with open(self.token_cache_file, "r", encoding="utf-8") as fp:
                    data = json.load(fp)
                access_token = data.get("access_token")
                expires_at = data.get("expires_at")
                if access_token and expires_at and time.time() < expires_at:
                    self.access_token = access_token
                    self.token_issued_at = data.get("issued_at", time.time())
                    self.token_ttl_sec = max(60, int(expires_at - self.token_issued_at))
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
            logger.info("Saved cached KIS token to %s", self.token_cache_file)
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
        attempt = 0
        while attempt < max_attempts:
            attempt += 1
            resp = self.session.post(url, json=payload, headers=headers, timeout=self.timeout_sec)

            if resp.status_code == 403:
                err_data = {}
                try:
                    err_data = resp.json()
                except Exception:
                    pass

                err_code = err_data.get("error_code")
                err_desc = err_data.get("error_description")

                if err_code == "EGW00133":
                    logger.warning(
                        "KIS token limit reached (1분당 1회) on attempt %d/%d. retry after wait: %s",
                        attempt,
                        max_attempts,
                        err_desc,
                    )
                    if attempt < max_attempts:
                        time.sleep(60)
                        continue

                resp.raise_for_status()

            try:
                resp.raise_for_status()
                data = resp.json()
            except requests.exceptions.RequestException as e:
                logger.error("KIS token request failed (attempt %d/%d): %s", attempt, max_attempts, str(e))
                if attempt < max_attempts:
                    time.sleep(5)
                    continue
                raise

            access_token = data.get("access_token")
            if not access_token:
                logger.error("KIS token response did not contain access_token: %s", data)
                if attempt < max_attempts:
                    time.sleep(5)
                    continue
                raise RuntimeError(f"KIS token issuance failed: {data}")

            expires_in = data.get("expires_in")
            if expires_in is not None:
                try:
                    self.token_ttl_sec = max(60, int(expires_in))
                except Exception:
                    self.token_ttl_sec = 3600
            else:
                self.token_ttl_sec = 3600

            self.access_token = access_token
            self.token_issued_at = time.time()
            self._save_cached_token(access_token, self.token_ttl_sec)

            logger.info("KIS access token issued successfully (ttl=%s sec)", self.token_ttl_sec)
            return access_token

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
            "authorization": f"Bearer {self.access_token}",
            "appkey": self.app_key,
            "appsecret": self.app_secret,
            "tr_id": self.search_info_tr_id,
            "custtype": self.custtype,
        }

    def search_stock_info(self, stock_code: str, market_div_code: str = "J") -> Dict[str, Any]:
        url = f"{self.base_url}{self.search_info_path}"
        params = {
            "FID_COND_MRKT_DIV_CODE": market_div_code,
            "PDNO": stock_code,
            "PRDT_TYPE_CD": "300",
        }
        headers = self._auth_headers()

        resp = self.session.get(
            url,
            headers=headers,
            params=params,
            timeout=self.timeout_sec,
        )
        resp.raise_for_status()

        data = resp.json()
        rt_cd = str(data.get("rt_cd", "")).strip()
        if rt_cd != "0":
            raise RuntimeError(f"KIS search-stock-info failed for {stock_code}: {data}")

        output = data.get("output")
        if not isinstance(output, dict) or not output:
            raise RuntimeError(f"KIS search-stock-info empty output for {stock_code}: {data}")

        return output


# ============================================================
# DB Layer
# ============================================================

class StockMasterBootstrapRepository:
    def __init__(self, conn: MySQLConnection) -> None:
        self.conn = conn

    def seed_exchanges(self) -> None:
        sql = """
        INSERT INTO exchange(code, name, timezone, country)
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            timezone = VALUES(timezone),
            country = VALUES(country)
        """
        cur = self.conn.cursor()
        try:
            cur.executemany(sql, EXCHANGE_SEED)
            self.conn.commit()
        finally:
            cur.close()

    def get_exchange_id_map(self) -> Dict[str, int]:
        sql = "SELECT exchange_id, code FROM exchange"
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql)
            rows = cur.fetchall()
            return {row["code"]: int(row["exchange_id"]) for row in rows}
        finally:
            cur.close()

    def upsert_sector(self, exchange_id: int, scheme: str, code: str, name: str) -> Optional[int]:
        if not code:
            return None

        sql_check = """
        SELECT sector_id
        FROM sector
        WHERE exchange_id = %s AND scheme = %s AND code = %s
        LIMIT 1
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql_check, (exchange_id, scheme, code))
            row = cur.fetchone()
            if row:
                return int(row[0])
        finally:
            cur.close()

        sql_insert = """
        INSERT INTO sector(exchange_id, scheme, code, name)
        VALUES (%s, %s, %s, %s)
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql_insert, (exchange_id, scheme, code, name if name else code))
            self.conn.commit()
            return int(cur.lastrowid)
        except IntegrityError:
            # Recover only on canonical identity collisions.
            cur.execute(sql_check, (exchange_id, scheme, code))
            row = cur.fetchone()
            if row:
                return int(row[0])
            raise
        finally:
            cur.close()

    def upsert_industry(
        self,
        exchange_id: int,
        sector_id: Optional[int],
        scheme: str,
        code: str,
        name: str,
    ) -> Optional[int]:
        if not code or sector_id is None:
            return None

        sql_check = """
        SELECT industry_id
        FROM industry
        WHERE exchange_id = %s AND sector_id = %s AND scheme = %s AND code = %s
        LIMIT 1
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql_check, (exchange_id, sector_id, scheme, code))
            row = cur.fetchone()
            if row:
                return int(row[0])
        finally:
            cur.close()

        sql_insert = """
        INSERT INTO industry(exchange_id, sector_id, scheme, code, name)
        VALUES (%s, %s, %s, %s, %s)
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql_insert, (exchange_id, sector_id, scheme, code, name if name else code))
            self.conn.commit()
            return int(cur.lastrowid)
        except IntegrityError:
            # Recover only on canonical identity collisions.
            cur.execute(sql_check, (exchange_id, sector_id, scheme, code))
            row = cur.fetchone()
            if row:
                return int(row[0])
            raise
        finally:
            cur.close()

    def upsert_stock(
        self,
        exchange_id: int,
        sector_id: Optional[int],
        industry_id: Optional[int],
        stock_code: str,
        company_name: str,
        isin: Optional[str],
        listed_at: Optional[date],
        asset_type: str = "EQUITY",
        currency: str = "KRW",
    ) -> int:
        sql = """
        INSERT INTO stock(
            exchange_id,
            sector_id,
            industry_id,
            stock_code,
            company_name,
            isin,
            listed_at,
            asset_type,
            currency
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            sector_id = VALUES(sector_id),
            industry_id = VALUES(industry_id),
            company_name = VALUES(company_name),
            isin = VALUES(isin),
            listed_at = VALUES(listed_at),
            asset_type = VALUES(asset_type),
            currency = VALUES(currency),
            stock_id = LAST_INSERT_ID(stock_id)
        """
        cur = self.conn.cursor()
        try:
            cur.execute(
                sql,
                (
                    exchange_id,
                    sector_id,
                    industry_id,
                    stock_code,
                    company_name,
                    isin,
                    listed_at,
                    asset_type,
                    currency,
                ),
            )
            stock_id = int(cur.lastrowid)
            self.conn.commit()
            return stock_id
        finally:
            cur.close()

    def get_stock_by_code(self, stock_code: str) -> Optional[Dict[str, Any]]:
        sql = """
        SELECT s.stock_id, s.stock_code, s.company_name, s.sector_id, s.industry_id, i.sector_id AS industry_sector_id
        FROM stock s
        LEFT JOIN industry i ON s.industry_id = i.industry_id
        WHERE s.stock_code = %s
        LIMIT 1
        """
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql, (stock_code,))
            return cur.fetchone()
        finally:
            cur.close()

    def get_resolved_classification_rows(self) -> List[Dict[str, Any]]:
        sql = """
        SELECT s.stock_code, s.company_name, s.sector_id, s.industry_id, COALESCE(s.sector_id, i.sector_id) AS resolved_sector_id
        FROM stock s
        LEFT JOIN industry i ON s.industry_id = i.industry_id
        WHERE s.industry_id IS NOT NULL
          AND COALESCE(s.sector_id, i.sector_id) IS NOT NULL
        """
        cur: MySQLCursorDict = self.conn.cursor(dictionary=True)
        try:
            cur.execute(sql)
            return cur.fetchall()
        finally:
            cur.close()

    def update_stock_industry(self, stock_id: int, industry_id: int, sector_id: Optional[int] = None) -> None:
        sql = """
        UPDATE stock
        SET sector_id = COALESCE(sector_id, %s),
            industry_id = %s
        WHERE stock_id = %s
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql, (sector_id, industry_id, stock_id))
            self.conn.commit()
        finally:
            cur.close()

    def get_industry_with_sector(self, industry_id: int) -> Optional[Tuple[int, int]]:
        sql = """
        SELECT industry_id, sector_id
        FROM industry
        WHERE industry_id = %s
        LIMIT 1
        """
        cur = self.conn.cursor()
        try:
            cur.execute(sql, (industry_id,))
            row = cur.fetchone()
            if not row:
                return None
            return int(row[0]), int(row[1]) if row[1] is not None else None
        finally:
            cur.close()


# ============================================================
# Business logic
# ============================================================

def build_kis_stock_info(
    stock_code: str,
    csv_market_div: Optional[str],
    kis_output: Dict[str, Any],
) -> KisStockInfo:
    company_name = pick_first_non_empty(
        kis_output,
        [
            "prdt_abrv_name",
            "prdt_name",
            "hts_kor_isnm",
            "prdt_eng_abrv_name",
            "issu_abrd_name",
            "std_pdno_name",
        ],
    )
    if not company_name:
        raise RuntimeError(f"company_name missing for {stock_code}: {kis_output}")

    isin = pick_first_non_empty(
        kis_output,
        [
            "std_pdno",
            "isin_cd",
            "pprc_isin_cd",
            "std_isin_cd",
        ],
    )

    listed_at = parse_yyyymmdd(
        pick_first_non_empty(
            kis_output,
            [
                "scts_mket_lstg_dt",
                "kosdaq_mket_lstg_dt",
                "frbd_mket_lstg_dt",
                "lstg_dt",
                "list_dt",
            ],
        )
    )

    exchange_code = infer_exchange_code(kis_output, csv_market_div)

    sector_code = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_mcls_cd",
            "idx_bztp_mcls_code",
            "mcls_code",
            "mcls_cd",
        ],
    )
    sector_name = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_mcls_cd_name",
            "idx_bztp_mcls_nm",
            "mcls_name",
            "mcls_nm",
            "idx_bztp_mcls_name",
        ],
    )

    industry_code = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_scls_cd",
            "idx_bztp_scls_code",
            "scls_code",
            "scls_cd",
        ],
    )
    industry_name = pick_first_non_empty(
        kis_output,
        [
            "idx_bztp_scls_cd_name",
            "idx_bztp_scls_nm",
            "scls_name",
            "scls_nm",
            "idx_bztp_scls_name",
        ],
    )

    return KisStockInfo(
        stock_code=stock_code,
        company_name=company_name,
        isin=isin,
        listed_at=listed_at,
        exchange_code=exchange_code,
        sector_code=sector_code,
        sector_name=sector_name,
        industry_code=industry_code,
        industry_name=industry_name,
    )


def resolve_csv_paths(csv_args: Optional[List[str]]) -> List[str]:
    if csv_args:
        resolved: List[str] = []
        for raw in csv_args:
            if raw is None:
                continue
            for item in str(raw).split(","):
                candidate = item.strip()
                if candidate:
                    resolved.append(candidate)
        if resolved:
            return resolved

    defaults = [str(path) for path in DEFAULT_SEED_CSV_CANDIDATES if path.exists()]
    if defaults:
        return defaults

    raise ValueError(
        "No seed CSV provided. Pass --csv or place data/financials_kospi.csv and/or data/financials_kosdaq.csv."
    )


def load_seed_stock_rows(csv_paths: List[str], stock_code_col: str, market_div_col: Optional[str]) -> pd.DataFrame:
    frames: List[pd.DataFrame] = []

    for csv_path in csv_paths:
        logger.info("Loading seed CSV: %s", csv_path)
        df = read_csv_with_fallback(csv_path)

        if stock_code_col not in df.columns:
            raise ValueError(f"CSV missing required column: {stock_code_col} :: {csv_path}")

        work = pd.DataFrame()
        work["stock_code"] = df[stock_code_col].map(normalize_stock_code)

        if market_div_col and market_div_col in df.columns:
            work["market_div"] = df[market_div_col].map(normalize_market_div)
        else:
            work["market_div"] = infer_market_div_from_path(csv_path)

        frames.append(work)

    if not frames:
        raise ValueError("No seed rows loaded from CSV input")

    work = pd.concat(frames, ignore_index=True)
    work = work.dropna(subset=["stock_code"]).drop_duplicates(subset=["market_div", "stock_code"], keep="first")
    work = work.sort_values("stock_code").reset_index(drop=True)
    return work


def bootstrap_stock_master(
    csv_paths: List[str],
    stock_code_col: str,
    market_div_col: Optional[str],
    kis: KisClient,
    repo: StockMasterBootstrapRepository,
    sleep_ms: int = DEFAULT_SLEEP_MS,
) -> BootstrapResult:
    repo.seed_exchanges()
    exchange_id_map = repo.get_exchange_id_map()

    for code in ("KOSPI", "KOSDAQ", "KONEX"):
        if code not in exchange_id_map:
            raise RuntimeError(f"exchange seed missing: {code}")

    seed_df = load_seed_stock_rows(csv_paths, stock_code_col, market_div_col)
    total = len(seed_df)

    logger.info("Loaded %d unique stock codes from CSV sources: %s", total, ", ".join(csv_paths))

    success_full = 0
    success_partial = 0
    hard_fail = 0

    missing_classification_codes: List[Tuple[str, str]] = []
    hard_failed_codes: List[Tuple[str, str]] = []

    start_ts = time.time()
    last_request_ts = 0.0
    min_interval_sec = 0.12

    for idx, row in seed_df.iterrows():
        stock_code = row["stock_code"]
        csv_market_div = row["market_div"] or "J"
        kis_output: Dict[str, Any] = {}

        now = time.time()
        wait = min_interval_sec - (now - last_request_ts)
        if wait > 0:
            time.sleep(wait)

        try:
            kis_output = kis.search_stock_info(stock_code, csv_market_div)
            last_request_ts = time.time()

            stock_info = build_kis_stock_info(stock_code, csv_market_div, kis_output)

            exchange_id = exchange_id_map[stock_info.exchange_code]

            raw_sector_code, raw_industry_code = extract_raw_classification_codes(kis_output)
            sector_id: Optional[int] = None
            industry_id: Optional[int] = None

            if stock_info.sector_code:
                sector_id = repo.upsert_sector(
                    exchange_id=exchange_id,
                    scheme=SECTOR_SCHEME,
                    code=stock_info.sector_code,
                    name=stock_info.sector_name or stock_info.sector_code,
                )

            if stock_info.industry_code and sector_id is not None:
                industry_id = repo.upsert_industry(
                    exchange_id=exchange_id,
                    sector_id=sector_id,
                    scheme=INDUSTRY_SCHEME,
                    code=stock_info.industry_code,
                    name=stock_info.industry_name or stock_info.industry_code,
                )

            stock_id = repo.upsert_stock(
                exchange_id=exchange_id,
                sector_id=sector_id,
                industry_id=industry_id,
                stock_code=stock_info.stock_code,
                company_name=stock_info.company_name,
                isin=stock_info.isin,
                listed_at=stock_info.listed_at,
                asset_type="EQUITY",
                currency="KRW",
            )

            if sector_id is not None and industry_id is not None:
                success_full += 1
            else:
                reason_parts = []
                if not stock_info.sector_code:
                    reason_parts.append("missing sector info")
                if not stock_info.industry_code:
                    reason_parts.append("missing industry info")
                elif sector_id is None:
                    reason_parts.append("industry skipped without sector context")
                reason = ", ".join(reason_parts) if reason_parts else "missing classification"

                success_partial += 1
                missing_classification_codes.append((stock_info.stock_code, reason))

                logger.warning(
                    "[%d/%d] OK-PARTIAL stock_code=%s stock_id=%s exchange=%s company=%s reason=%s raw(idx_bztp_mcls_cd=%s, idx_bztp_scls_cd=%s)",
                    idx + 1,
                    total,
                    stock_info.stock_code,
                    stock_id,
                    stock_info.exchange_code,
                    stock_info.company_name,
                    reason,
                    raw_sector_code,
                    raw_industry_code,
                )

        except Exception as e:
            hard_fail += 1
            hard_failed_codes.append((stock_code, str(e)))
            raw_sector_code, raw_industry_code = extract_raw_classification_codes(kis_output) if kis_output else (None, None)
            logger.exception(
                "[%d/%d] HARD-FAIL stock_code=%s raw(idx_bztp_mcls_cd=%s, idx_bztp_scls_cd=%s)",
                idx + 1,
                total,
                stock_code,
                raw_sector_code,
                raw_industry_code,
            )

        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000.0)

    elapsed = time.time() - start_ts

    logger.info("===================================================")
    logger.info("Bootstrap finished")
    logger.info("total           = %d", total)
    logger.info("success_full    = %d", success_full)
    logger.info("success_partial = %d", success_partial)
    logger.info("hard_fail       = %d", hard_fail)
    logger.info("elapsed         = %.2f sec", elapsed)

    if missing_classification_codes:
        logger.info("Missing classification stock codes:")
        for code, reason in missing_classification_codes:
            logger.info(" - %s :: %s", code, reason)

    if hard_failed_codes:
        logger.info("Hard failed stock codes:")
        for code, reason in hard_failed_codes:
            logger.info(" - %s :: %s", code, reason)

    return BootstrapResult(
        total=total,
        success_full=success_full,
        success_partial=success_partial,
        hard_fail=hard_fail,
        elapsed_sec=elapsed,
        missing_classification_codes=missing_classification_codes,
        hard_failed_codes=hard_failed_codes,
    )


def build_prefix_classification_map(repo: StockMasterBootstrapRepository) -> Tuple[Dict[str, Tuple[int, int, str]], Dict[str, List[Tuple[int, int, str]]]]:
    rows = repo.get_resolved_classification_rows()

    grouped: Dict[str, set] = defaultdict(set)
    for row in rows:
        stock_code = row["stock_code"]
        company_name = row["company_name"] or ""
        normalized_name = normalize_company_name_for_backfill(company_name)
        sector_id = int(row["resolved_sector_id"])
        industry_id = int(row["industry_id"])
        prefix = company_prefix(stock_code)
        grouped[prefix].add((sector_id, industry_id, normalized_name))

    resolved: Dict[str, Tuple[int, int, str]] = {}
    conflicts: Dict[str, List[Tuple[int, int, str]]] = {}

    for prefix, values in grouped.items():
        if len(values) == 1:
            resolved[prefix] = next(iter(values))
        else:
            conflicts[prefix] = sorted(list(values))

    return resolved, conflicts


def backfill_missing_classification(
    repo: StockMasterBootstrapRepository,
    missing_classification_codes: List[Tuple[str, str]],
) -> Dict[str, Any]:
    resolved_map, conflict_map = build_prefix_classification_map(repo)

    updated = 0
    skipped_no_source: List[Tuple[str, str]] = []
    skipped_conflict: List[Tuple[str, str]] = []
    skipped_name_mismatch: List[Tuple[str, str, str]] = []

    for stock_code, reason in missing_classification_codes:
        row = repo.get_stock_by_code(stock_code)
        if not row:
            skipped_no_source.append((stock_code, "stock not found"))
            continue

        stock_id = int(row["stock_id"])
        current_industry_id = row["industry_id"]
        current_sector_id = row["sector_id"] if row["sector_id"] is not None else row["industry_sector_id"]
        target_name = row["company_name"] or ""

        # 이미 해결되었으면 skip
        if current_industry_id is not None and current_sector_id is not None:
            continue

        prefix = company_prefix(stock_code)

        if prefix in conflict_map:
            skipped_conflict.append((stock_code, prefix))
            continue

        source = resolved_map.get(prefix)
        if not source:
            skipped_no_source.append((stock_code, f"no resolved source for prefix={prefix}"))
            continue

        source_sector_id, source_industry_id, source_normalized_name = source
        target_normalized_name = normalize_company_name_for_backfill(target_name)

        # 회사명 정규화 일치할 때만 backfill
        if not target_normalized_name or target_normalized_name != source_normalized_name:
            skipped_name_mismatch.append(
                (stock_code, target_normalized_name, source_normalized_name)
            )
            continue

        repo.update_stock_industry(
            stock_id=stock_id,
            industry_id=source_industry_id,
            sector_id=source_sector_id,
        )
        updated += 1

        logger.info(
            "[BACKFILL] UPDATED stock_code=%s prefix=%s industry_id=%s sector_id=%s",
            stock_code,
            prefix,
            source_industry_id,
            source_sector_id,
        )

    result = {
        "updated": updated,
        "skipped_no_source": skipped_no_source,
        "skipped_conflict": skipped_conflict,
        "skipped_name_mismatch": skipped_name_mismatch,
    }

    logger.info("===================================================")
    logger.info("Backfill finished")
    logger.info("updated               = %d", updated)
    logger.info("skipped_no_source     = %d", len(skipped_no_source))
    logger.info("skipped_conflict      = %d", len(skipped_conflict))
    logger.info("skipped_name_mismatch = %d", len(skipped_name_mismatch))

    if skipped_no_source:
        logger.info("Backfill skipped - no source:")
        for code, reason in skipped_no_source:
            logger.info(" - %s :: %s", code, reason)

    if skipped_conflict:
        logger.info("Backfill skipped - conflict prefix:")
        for code, prefix in skipped_conflict:
            logger.info(" - %s :: prefix=%s", code, prefix)

    if skipped_name_mismatch:
        logger.info("Backfill skipped - normalized name mismatch:")
        for code, target_name, source_name in skipped_name_mismatch:
            logger.info(" - %s :: target=%s source=%s", code, target_name, source_name)

    return result


# ============================================================
# Main
# ============================================================

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Bootstrap QAIMA stock master from KIS using stock codes from CSV."
    )
    parser.add_argument(
        "--csv",
        action="append",
        default=None,
        help="Path to source CSV (financial CSV etc.)",
    )
    parser.add_argument(
        "--stock-code-col",
        default="stock_code",
        help="CSV column name for stock code",
    )
    parser.add_argument(
        "--market-div-col",
        default=None,
        help="CSV column name for market division (J/Q/K). Optional.",
    )
    parser.add_argument(
        "--sleep-ms",
        type=int,
        default=DEFAULT_SLEEP_MS,
        help="Sleep milliseconds between KIS requests",
    )
    parser.add_argument(
        "--skip-backfill",
        action="store_true",
        help="Skip second-pass prefix/name based classification backfill.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    market_div_col = None
    if args.market_div_col:
        market_div_col = args.market_div_col.strip() if isinstance(args.market_div_col, str) else None
        if market_div_col == "":
            market_div_col = None

    app_key = required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY)
    app_secret = required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET)
    csv_paths = resolve_csv_paths(args.csv)

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
            search_info_path=DEFAULT_KIS_SEARCH_INFO_PATH,
            search_info_tr_id=DEFAULT_KIS_SEARCH_INFO_TR_ID,
            custtype=DEFAULT_KIS_CUSTTYPE,
            timeout_sec=DEFAULT_REQUEST_TIMEOUT,
        )
        repo = StockMasterBootstrapRepository(conn)

        bootstrap_result = bootstrap_stock_master(
            csv_paths=csv_paths,
            stock_code_col=args.stock_code_col,
            market_div_col=market_div_col,
            kis=kis,
            repo=repo,
            sleep_ms=args.sleep_ms,
        )

        if not args.skip_backfill and bootstrap_result.missing_classification_codes:
            backfill_missing_classification(
                repo=repo,
                missing_classification_codes=bootstrap_result.missing_classification_codes,
            )

        return 0

    except Exception:
        logger.exception("Bootstrap failed")
        return 1

    finally:
        try:
            conn.close()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
