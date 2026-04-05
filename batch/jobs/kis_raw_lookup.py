#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from bootstrap_stock_from_kis import (  # noqa: E402
    DEFAULT_KIS_APP_KEY,
    DEFAULT_KIS_APP_SECRET,
    DEFAULT_KIS_BASE_URL,
    DEFAULT_KIS_CUSTTYPE,
    DEFAULT_KIS_SEARCH_INFO_PATH,
    DEFAULT_KIS_SEARCH_INFO_TR_ID,
    DEFAULT_KIS_TOKEN_PATH,
    DEFAULT_REQUEST_TIMEOUT,
    KisClient,
    normalize_stock_code,
    required_env,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Prompt for a stock code and print the raw KIS stock-info response."
    )
    parser.add_argument(
        "--stock-code",
        default=None,
        help="Optional stock code. If omitted, the script prompts for input.",
    )
    parser.add_argument(
        "--market-div",
        default=None,
        help="Optional market division code. KRX domestic requests use J.",
    )
    return parser.parse_args()


def build_client() -> KisClient:
    app_key = required_env("KIS_APP_KEY", DEFAULT_KIS_APP_KEY)
    app_secret = required_env("KIS_APP_SECRET", DEFAULT_KIS_APP_SECRET)
    return KisClient(
        app_key=app_key,
        app_secret=app_secret,
        base_url=DEFAULT_KIS_BASE_URL,
        token_path=DEFAULT_KIS_TOKEN_PATH,
        search_info_path=DEFAULT_KIS_SEARCH_INFO_PATH,
        search_info_tr_id=DEFAULT_KIS_SEARCH_INFO_TR_ID,
        custtype=DEFAULT_KIS_CUSTTYPE,
        timeout_sec=DEFAULT_REQUEST_TIMEOUT,
    )


def ask_stock_code(initial_value: str | None) -> str:
    raw = initial_value
    while True:
        if raw is None:
            raw = input("stock code> ").strip()

        stock_code = normalize_stock_code(raw)
        if stock_code:
            return stock_code

        print("invalid stock code. enter digits like 005930")
        raw = None


def ask_market_div(initial_value: str | None) -> str:
    raw = initial_value

    while True:
        if raw is None:
            raw = input("market div [default=J]> ").strip().upper() or "J"
        else:
            raw = raw.strip().upper() or "J"

        if raw in {"J", "Q", "K"}:
            if raw != "J":
                print("KRX domestic requests are normalized to J.")
            return "J"

        print("invalid market div. use J")
        raw = None


def main() -> int:
    args = parse_args()
    client = build_client()

    stock_code = ask_stock_code(args.stock_code)
    market_div = ask_market_div(args.market_div)

    print(f"requesting KIS raw response for stock_code={stock_code}, market_div={market_div}")
    output = client.search_stock_info(stock_code=stock_code, market_div_code=market_div)
    print(json.dumps(output, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
