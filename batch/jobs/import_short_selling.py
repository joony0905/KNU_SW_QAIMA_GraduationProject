from __future__ import annotations

import argparse
import csv
import logging
import os
from datetime import datetime
from decimal import Decimal, InvalidOperation
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
logger = logging.getLogger("import_short_selling")


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CSV_CANDIDATES = [
    PROJECT_ROOT / "data" / "short_selling.csv",
]
CSV_ENCODING_CANDIDATES = ("utf-8-sig", "utf-8", "cp949")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

BATCH_SIZE = 5000

UPSERT_SQL = """
INSERT INTO short_selling (
    stock_id,
    report_date,
    market_code,
    security_type,
    short_volume_total,
    short_volume_uptick_applied,
    short_volume_uptick_exempt,
    total_volume,
    short_volume_ratio,
    short_amount_total,
    short_amount_uptick_applied,
    short_amount_uptick_exempt,
    total_amount,
    short_amount_ratio,
    source,
    source_screen_id,
    created_at,
    updated_at
) VALUES (
    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
)
ON DUPLICATE KEY UPDATE
    market_code = VALUES(market_code),
    security_type = VALUES(security_type),
    short_volume_total = VALUES(short_volume_total),
    short_volume_uptick_applied = VALUES(short_volume_uptick_applied),
    short_volume_uptick_exempt = VALUES(short_volume_uptick_exempt),
    total_volume = VALUES(total_volume),
    short_volume_ratio = VALUES(short_volume_ratio),
    short_amount_total = VALUES(short_amount_total),
    short_amount_uptick_applied = VALUES(short_amount_uptick_applied),
    short_amount_uptick_exempt = VALUES(short_amount_uptick_exempt),
    total_amount = VALUES(total_amount),
    short_amount_ratio = VALUES(short_amount_ratio),
    source = VALUES(source),
    source_screen_id = VALUES(source_screen_id),
    updated_at = VALUES(updated_at)
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fast short_selling CSV importer")
    parser.add_argument(
        "--csv",
        action="append",
        default=[],
        help="CSV path. Can be repeated. If omitted, data/short_selling.csv is auto-discovered.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=BATCH_SIZE,
        help="executemany batch size",
    )
    return parser.parse_args()


def resolve_csv_paths(raw_paths: Sequence[str]) -> List[Path]:
    paths: List[Path] = []
    if raw_paths:
        for raw in raw_paths:
            for item in raw.split(","):
                candidate = Path(item.strip())
                if candidate:
                    paths.append(candidate)
    else:
        paths.extend(DEFAULT_CSV_CANDIDATES)

    resolved: List[Path] = []
    seen = set()
    for path in paths:
        normalized = path if path.is_absolute() else PROJECT_ROOT / path
        normalized = normalized.resolve()
        if normalized in seen or not normalized.exists():
            continue
        seen.add(normalized)
        resolved.append(normalized)

    if not resolved:
        raise SystemExit("No short_selling CSV found. Use --csv or place file in data/short_selling.csv")
    return resolved


def connect_db() -> MySQLConnection:
    return mysql.connector.connect(
        host=DEFAULT_DB_HOST,
        port=DEFAULT_DB_PORT,
        user=DEFAULT_DB_USER,
        password=DEFAULT_DB_PASSWORD,
        database=DEFAULT_DB_NAME,
        autocommit=False,
    )


def normalize_stock_code(stock_code: str) -> str:
    trimmed = stock_code.strip().replace('"', "").replace("'", "").upper()
    if trimmed.isdigit():
        return f"{int(trimmed):06d}"
    return trimmed


def load_stock_id_by_code(conn: MySQLConnection) -> Dict[str, int]:
    sql = "SELECT stock_id, stock_code FROM stock ORDER BY stock_code ASC"
    cur = conn.cursor()
    try:
        cur.execute(sql)
        result: Dict[str, int] = {}
        for stock_id, stock_code in cur.fetchall():
            if stock_id is None or stock_code is None:
                continue
            result[normalize_stock_code(str(stock_code))] = int(stock_id)
        return result
    finally:
        cur.close()


def read_csv_rows(path: Path) -> Iterable[dict]:
    last_error: Optional[Exception] = None
    for encoding in CSV_ENCODING_CANDIDATES:
        try:
            with path.open("r", encoding=encoding, newline="") as fp:
                yield from csv.DictReader(fp)
            return
        except UnicodeDecodeError as exc:
            last_error = exc
    if last_error:
        raise last_error


def trim_to_none(raw: Optional[str]) -> Optional[str]:
    if raw is None:
        return None
    value = raw.strip()
    return value or None


def require(row: dict, key: str) -> str:
    value = trim_to_none(row.get(key))
    if value is None:
        raise ValueError(f"Required column is blank: {key}")
    return value


def parse_decimal(raw: Optional[str]) -> Optional[Decimal]:
    value = trim_to_none(raw)
    if value is None:
        return None
    try:
        return Decimal(value.replace(",", ""))
    except InvalidOperation as exc:
        raise ValueError(f"Invalid decimal value: {raw}") from exc


def parse_date(raw: Optional[str]) -> str:
    value = require({"value": raw}, "value")
    return datetime.strptime(value, "%Y-%m-%d").date().isoformat()


def to_params(
    row: dict,
    stock_id_by_code: Dict[str, int],
    now: datetime,
) -> Optional[Tuple]:
    normalized_code = normalize_stock_code(require(row, "stock_code"))
    stock_id = stock_id_by_code.get(normalized_code)
    if stock_id is None:
        return None

    now_sql = now.strftime("%Y-%m-%d %H:%M:%S.%f")
    return (
        stock_id,
        parse_date(row.get("report_date")),
        require(row, "market_code"),
        require(row, "security_type"),
        parse_decimal(row.get("short_volume_total")),
        parse_decimal(row.get("short_volume_uptick_applied")),
        parse_decimal(row.get("short_volume_uptick_exempt")),
        parse_decimal(row.get("total_volume")),
        parse_decimal(row.get("short_volume_ratio")),
        parse_decimal(row.get("short_amount_total")),
        parse_decimal(row.get("short_amount_uptick_applied")),
        parse_decimal(row.get("short_amount_uptick_exempt")),
        parse_decimal(row.get("total_amount")),
        parse_decimal(row.get("short_amount_ratio")),
        trim_to_none(row.get("source")) or "KRX",
        trim_to_none(row.get("source_screen_id")) or "MDCSTAT301",
        now_sql,
        now_sql,
    )


def flush_batch(conn: MySQLConnection, batch: List[Tuple]) -> int:
    if not batch:
        return 0
    cur = conn.cursor()
    try:
        cur.executemany(UPSERT_SQL, batch)
        conn.commit()
        return len(batch)
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()


def import_csvs(csv_paths: Sequence[Path], batch_size: int) -> None:
    conn = connect_db()
    try:
        stock_id_by_code = load_stock_id_by_code(conn)
        logger.info("Loaded %d stocks from DB", len(stock_id_by_code))

        total_upserted = 0
        total_skipped_unknown_stock = 0
        total_skipped_blank = 0
        total_failed = 0
        missing_stock_counts: Dict[str, int] = {}

        for csv_path in csv_paths:
            logger.info("short selling import start: %s", csv_path)

            file_upserted = 0
            file_skipped_unknown_stock = 0
            file_skipped_blank = 0
            file_failed = 0
            batch: List[Tuple] = []

            for line_no, row in enumerate(read_csv_rows(csv_path), start=2):
                if not any((value or "").strip() for value in row.values()):
                    file_skipped_blank += 1
                    continue

                try:
                    params = to_params(row, stock_id_by_code, datetime.now())
                    if params is None:
                        code = normalize_stock_code(row.get("stock_code") or "")
                        missing_stock_counts[code] = missing_stock_counts.get(code, 0) + 1
                        file_skipped_unknown_stock += 1
                        continue

                    batch.append(params)
                    if len(batch) >= batch_size:
                        flushed = flush_batch(conn, batch)
                        file_upserted += flushed
                        logger.info(
                            "short selling batch flushed: file=%s line=%d flushed=%d upserted=%d skippedUnknownStock=%d failed=%d",
                            csv_path,
                            line_no,
                            flushed,
                            file_upserted,
                            file_skipped_unknown_stock,
                            file_failed,
                        )
                        batch.clear()
                except Exception as exc:
                    file_failed += 1
                    logger.warning(
                        "Short selling CSV row skipped. file=%s line=%s cause=%s",
                        csv_path,
                        line_no,
                        exc,
                    )

                processed = file_upserted + file_skipped_unknown_stock + file_failed
                if processed > 0 and processed % 5000 == 0:
                    logger.info(
                        "short selling import progress: file=%s line=%d upserted=%d skippedUnknownStock=%d failed=%d",
                        csv_path,
                        line_no,
                        file_upserted,
                        file_skipped_unknown_stock,
                        file_failed,
                    )

            if batch:
                flushed = flush_batch(conn, batch)
                file_upserted += flushed
                logger.info(
                    "short selling batch flushed: file=%s line=%d flushed=%d upserted=%d skippedUnknownStock=%d failed=%d",
                    csv_path,
                    line_no,
                    flushed,
                    file_upserted,
                    file_skipped_unknown_stock,
                    file_failed,
                )
                batch.clear()

            total_upserted += file_upserted
            total_skipped_unknown_stock += file_skipped_unknown_stock
            total_skipped_blank += file_skipped_blank
            total_failed += file_failed

            logger.info(
                "short selling import done: file=%s upserted=%d skippedBlank=%d skippedUnknownStock=%d failed=%d",
                csv_path,
                file_upserted,
                file_skipped_blank,
                file_skipped_unknown_stock,
                file_failed,
            )

        logger.info(
            "short selling import summary: files=%d upserted=%d skippedBlank=%d skippedUnknownStock=%d failed=%d",
            len(csv_paths),
            total_upserted,
            total_skipped_blank,
            total_skipped_unknown_stock,
            total_failed,
        )

        if missing_stock_counts:
            for stock_code, count in sorted(missing_stock_counts.items(), key=lambda item: (-item[1], item[0]))[:20]:
                logger.warning("unknown stock code skipped: %s (count=%d)", stock_code, count)
    finally:
        conn.close()


def main() -> None:
    args = parse_args()
    csv_paths = resolve_csv_paths(args.csv)
    import_csvs(csv_paths, args.batch_size)


if __name__ == "__main__":
    main()
