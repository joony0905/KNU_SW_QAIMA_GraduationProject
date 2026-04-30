#python jobs\kis_overseas_industry_codes.py --excd NAS 

# KIS 해외 산업 코드 조회 스크립트
from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import requests


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("kis_overseas_industry_codes")


DEFAULT_KIS_BASE_URL = os.getenv("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443")
DEFAULT_KIS_APP_KEY = os.getenv("KIS_APP_KEY", "")
DEFAULT_KIS_APP_SECRET = os.getenv("KIS_APP_SECRET", "")
DEFAULT_KIS_TOKEN_PATH = os.getenv("KIS_TOKEN_PATH", "/oauth2/tokenP")
DEFAULT_KIS_INDUSTRY_PATH = os.getenv(
    "KIS_OVERSEAS_INDUSTRY_PATH",
    "/uapi/overseas-price/v1/quotations/industry-price",
)
DEFAULT_KIS_INDUSTRY_TR_ID = os.getenv("KIS_OVERSEAS_INDUSTRY_TR_ID", "HHDFS76370100")
DEFAULT_KIS_CUSTTYPE = os.getenv("KIS_CUSTTYPE", "P")
DEFAULT_KIS_TOKEN_CACHE_FILE = os.getenv("KIS_TOKEN_CACHE_FILE", "./.kis_token_cache.json")
DEFAULT_REQUEST_TIMEOUT = int(os.getenv("BOOTSTRAP_HTTP_TIMEOUT_SEC", "15"))
DEFAULT_RATE_LIMIT_BACKOFF_SEC = int(os.getenv("BOOTSTRAP_RATE_LIMIT_BACKOFF_SEC", "60"))


@dataclass
class OverseasIndustryCode:
    exchange_code: str
    industry_code: str
    name: str


def required_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if not value:
        raise ValueError(f"Missing required environment variable: {name}")
    return value


class KisOverseasIndustryClient:
    def __init__(
        self,
        app_key: str,
        app_secret: str,
        base_url: str = DEFAULT_KIS_BASE_URL,
        token_path: str = DEFAULT_KIS_TOKEN_PATH,
        industry_path: str = DEFAULT_KIS_INDUSTRY_PATH,
        industry_tr_id: str = DEFAULT_KIS_INDUSTRY_TR_ID,
        custtype: str = DEFAULT_KIS_CUSTTYPE,
        timeout_sec: int = DEFAULT_REQUEST_TIMEOUT,
        token_cache_file: str = DEFAULT_KIS_TOKEN_CACHE_FILE,
    ) -> None:
        self.app_key = app_key
        self.app_secret = app_secret
        self.base_url = base_url.rstrip("/")
        self.token_path = token_path
        self.industry_path = industry_path
        self.industry_tr_id = industry_tr_id
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
            "tr_id": self.industry_tr_id,
            "custtype": self.custtype,
        }

    def fetch_industry_codes(self, exchange_code: str) -> List[OverseasIndustryCode]:
        url = f"{self.base_url}{self.industry_path}"
        headers = self._auth_headers()
        params = {
            "AUTH": "",
            "EXCD": exchange_code,
        }

        resp = self.session.get(url, headers=headers, params=params, timeout=self.timeout_sec)
        body_preview = resp.text[:2000] if resp.text else ""
        if not resp.ok:
            raise RuntimeError(
                f"KIS overseas industry HTTP error exchange={exchange_code} "
                f"status={resp.status_code} body={body_preview}"
            )

        data = resp.json()
        logger.info(
            "KIS overseas industry response exchange=%s rt_cd=%s msg_cd=%s msg1=%s output1=%s",
            exchange_code,
            data.get("rt_cd"),
            data.get("msg_cd"),
            data.get("msg1"),
            data.get("output1"),
        )

        if str(data.get("rt_cd", "")).strip() != "0":
            raise RuntimeError(
                f"KIS overseas industry request failed exchange={exchange_code} "
                f"rt_cd={data.get('rt_cd')} msg_cd={data.get('msg_cd')} msg1={data.get('msg1')}"
            )

        output2 = data.get("output2")
        if not isinstance(output2, list):
            return []

        result: List[OverseasIndustryCode] = []
        for item in output2:
            industry_code = str(item.get("icod") or "").strip()
            name = str(item.get("name") or "").strip()
            if not industry_code and not name:
                continue
            result.append(
                OverseasIndustryCode(
                    exchange_code=exchange_code,
                    industry_code=industry_code,
                    name=name,
                )
            )
        return result


def write_json(rows: List[OverseasIndustryCode], path: str) -> None:
    data = [
        {
            "exchange_code": row.exchange_code,
            "industry_code": row.industry_code,
            "name": row.name,
        }
        for row in rows
    ]
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)


def write_csv(rows: List[OverseasIndustryCode], path: str) -> None:
    with open(path, "w", encoding="utf-8-sig", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=["exchange_code", "industry_code", "name"])
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "exchange_code": row.exchange_code,
                    "industry_code": row.industry_code,
                    "name": row.name,
                }
            )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch KIS overseas industry codes.")
    parser.add_argument(
        "--excd",
        action="append",
        default=None,
        help="Exchange code. Can be repeated. Examples: NAS, NYS, AMS, HKS, SHS, SZS, HSX, HNX, TSE",
    )
    parser.add_argument("--json-out", default=None, help="Optional JSON output path")
    parser.add_argument("--csv-out", default=None, help="Optional CSV output path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    exchanges = args.excd or ["NAS"]

    app_key = required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY)
    app_secret = required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET)

    client = KisOverseasIndustryClient(app_key=app_key, app_secret=app_secret)

    try:
        rows: List[OverseasIndustryCode] = []
        for exchange in exchanges:
            exchange_code = str(exchange).strip().upper()
            if not exchange_code:
                continue
            fetched = client.fetch_industry_codes(exchange_code)
            logger.info("Fetched %d overseas industry codes for exchange=%s", len(fetched), exchange_code)
            rows.extend(fetched)

        for row in rows:
            print(f"{row.exchange_code}\t{row.industry_code}\t{row.name}")

        if args.json_out:
            write_json(rows, args.json_out)
            logger.info("Wrote JSON output: %s", args.json_out)

        if args.csv_out:
            write_csv(rows, args.csv_out)
            logger.info("Wrote CSV output: %s", args.csv_out)

        return 0
    except Exception:
        logger.exception("KIS overseas industry code lookup failed")
        return 1


if __name__ == "__main__":
    sys.exit(main())
