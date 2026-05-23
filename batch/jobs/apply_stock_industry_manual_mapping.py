from __future__ import annotations

import argparse
import csv
import logging
import os
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import mysql.connector
from mysql.connector.connection import MySQLConnection


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("apply_stock_industry_manual_mapping")


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MAPPING_PATH = PROJECT_ROOT / "data" / "stock_industry_manual_mapping.tsv"
CSV_ENCODING_CANDIDATES = ("utf-8-sig", "utf-8", "cp949")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Apply manually curated stock -> industry mappings."
    )
    parser.add_argument(
        "--mapping",
        default=str(DEFAULT_MAPPING_PATH),
        help="Manual mapping CSV/TSV path. Default: data/stock_industry_manual_mapping.tsv",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Commit updates. Without this flag the script validates and rolls back.",
    )
    parser.add_argument(
        "--allow-remap",
        action="store_true",
        help="Allow updating stocks that already have industry_id. Default updates only missing industry_id rows.",
    )
    return parser.parse_args()


def connect_db() -> MySQLConnection:
    return mysql.connector.connect(
        host=DEFAULT_DB_HOST,
        port=DEFAULT_DB_PORT,
        user=DEFAULT_DB_USER,
        password=DEFAULT_DB_PASSWORD,
        database=DEFAULT_DB_NAME,
        charset="utf8mb4",
        use_unicode=True,
        autocommit=False,
    )


def normalize_stock_code(stock_code: str) -> str:
    trimmed = stock_code.strip().replace('"', "").replace("'", "").upper()
    if trimmed.isdigit():
        return f"{int(trimmed):06d}"
    return trimmed


def trim_to_none(raw: Optional[str]) -> Optional[str]:
    if raw is None:
        return None
    value = raw.strip()
    return value or None


def read_mapping_rows(path: Path) -> Iterable[dict]:
    last_error: Optional[Exception] = None
    for encoding in CSV_ENCODING_CANDIDATES:
        try:
            with path.open("r", encoding=encoding, newline="") as fp:
                sample = fp.read(4096)
                fp.seek(0)
                delimiter = "\t" if "\t" in sample.splitlines()[0] else ","
                yield from csv.DictReader(fp, delimiter=delimiter)
            return
        except UnicodeDecodeError as exc:
            last_error = exc
    if last_error:
        raise last_error


def load_exchange_id_by_code(conn: MySQLConnection) -> Dict[str, int]:
    cur = conn.cursor()
    try:
        cur.execute("SELECT exchange_id, code FROM exchange")
        return {str(code): int(exchange_id) for exchange_id, code in cur.fetchall()}
    finally:
        cur.close()


def load_industries(conn: MySQLConnection) -> Tuple[Dict[int, Tuple[int, int]], Dict[Tuple[int, str], List[int]]]:
    cur = conn.cursor()
    try:
        cur.execute("SELECT industry_id, exchange_id, sector_id, code FROM industry")
        by_id: Dict[int, Tuple[int, int]] = {}
        by_exchange_code: Dict[Tuple[int, str], List[int]] = {}
        for industry_id, exchange_id, sector_id, code in cur.fetchall():
            industry_id_int = int(industry_id)
            exchange_id_int = int(exchange_id)
            by_id[industry_id_int] = (exchange_id_int, int(sector_id))
            by_exchange_code.setdefault((exchange_id_int, str(code)), []).append(industry_id_int)
        return by_id, by_exchange_code
    finally:
        cur.close()


def resolve_industry_id(
    row: dict,
    exchange_id: int,
    industries_by_id: Dict[int, Tuple[int, int]],
    industries_by_exchange_code: Dict[Tuple[int, str], List[int]],
) -> Optional[int]:
    raw_industry_id = trim_to_none(
        row.get("target_industry_id")
        or row.get("industry_id")
        or row.get("manual_industry_id")
    )
    if raw_industry_id:
        if not raw_industry_id.isdigit():
            raise ValueError(f"Invalid target_industry_id={raw_industry_id}")
        industry_id = int(raw_industry_id)
        industry = industries_by_id.get(industry_id)
        if industry is None:
            raise ValueError(f"industry_id not found: {industry_id}")
        industry_exchange_id, _ = industry
        if industry_exchange_id != exchange_id:
            raise ValueError(f"industry_id={industry_id} belongs to a different exchange")
        return industry_id

    raw_industry_code = trim_to_none(
        row.get("target_industry_code")
        or row.get("industry_code")
        or row.get("manual_industry_code")
    )
    if not raw_industry_code:
        return None

    industry_ids = industries_by_exchange_code.get((exchange_id, raw_industry_code), [])
    if not industry_ids:
        raise ValueError(f"industry_code not found for exchange: {raw_industry_code}")
    if len(industry_ids) > 1:
        raise ValueError(
            f"industry_code is ambiguous for exchange: {raw_industry_code}; use target_industry_id"
        )
    return industry_ids[0]


def update_stock_industry(
    conn: MySQLConnection,
    stock_code: str,
    exchange_id: int,
    industry_id: int,
    sector_id: int,
    allow_remap: bool,
) -> int:
    where_extra = "" if allow_remap else " AND industry_id IS NULL"
    sql = f"""
    UPDATE stock
    SET sector_id = %s,
        industry_id = %s
    WHERE stock_code = %s
      AND exchange_id = %s
      {where_extra}
    """
    cur = conn.cursor()
    try:
        cur.execute(sql, (sector_id, industry_id, stock_code, exchange_id))
        return int(cur.rowcount)
    finally:
        cur.close()


def apply_rows(conn: MySQLConnection, rows: Sequence[dict], apply: bool, allow_remap: bool) -> None:
    exchange_id_by_code = load_exchange_id_by_code(conn)
    industries_by_id, industries_by_exchange_code = load_industries(conn)

    seen: set[Tuple[str, str]] = set()
    provided = 0
    updated = 0
    skipped_blank = 0

    for line_no, row in enumerate(rows, start=2):
        stock_code_raw = trim_to_none(row.get("stock_code"))
        exchange_code = trim_to_none(row.get("exchange_code"))
        if not stock_code_raw or not exchange_code:
            raise ValueError(f"line {line_no}: stock_code and exchange_code are required")

        stock_code = normalize_stock_code(stock_code_raw)
        key = (exchange_code, stock_code)
        if key in seen:
            raise ValueError(f"line {line_no}: duplicate mapping row for {exchange_code} {stock_code}")
        seen.add(key)

        exchange_id = exchange_id_by_code.get(exchange_code)
        if exchange_id is None:
            raise ValueError(f"line {line_no}: exchange_code not found: {exchange_code}")

        industry_id = resolve_industry_id(
            row=row,
            exchange_id=exchange_id,
            industries_by_id=industries_by_id,
            industries_by_exchange_code=industries_by_exchange_code,
        )
        if industry_id is None:
            skipped_blank += 1
            continue

        provided += 1
        _, sector_id = industries_by_id[industry_id]
        updated += update_stock_industry(
            conn=conn,
            stock_code=stock_code,
            exchange_id=exchange_id,
            industry_id=industry_id,
            sector_id=sector_id,
            allow_remap=allow_remap,
        )

    if apply:
        conn.commit()
        logger.info("Committed manual industry mapping updates")
    else:
        conn.rollback()
        logger.info("Dry-run complete; rolled back updates. Pass --apply to commit.")

    logger.info("mapping_rows=%d", len(rows))
    logger.info("provided_mappings=%d", provided)
    logger.info("blank_mappings_skipped=%d", skipped_blank)
    logger.info("stock_rows_updated=%d", updated)


def main() -> int:
    args = parse_args()
    mapping_path = Path(args.mapping)
    if not mapping_path.is_absolute():
        mapping_path = (PROJECT_ROOT / mapping_path).resolve()
    if not mapping_path.exists():
        raise SystemExit(f"Mapping file not found: {mapping_path}")

    rows = list(read_mapping_rows(mapping_path))
    conn = connect_db()
    try:
        apply_rows(
            conn=conn,
            rows=rows,
            apply=args.apply,
            allow_remap=args.allow_remap,
        )
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
