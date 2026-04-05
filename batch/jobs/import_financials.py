from __future__ import annotations

import argparse
import csv
import logging
import os
import sys
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
logger = logging.getLogger("import_financials")


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CSV_CANDIDATES = [
    PROJECT_ROOT / "data" / "financials_kospi.csv",
    PROJECT_ROOT / "data" / "financials_kosdaq.csv",
]
CSV_ENCODING_CANDIDATES = ("utf-8-sig", "utf-8", "cp949")

DEFAULT_DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DEFAULT_DB_PORT = int(os.getenv("DB_PORT", "3306"))
DEFAULT_DB_USER = os.getenv("DB_USER", "root")
DEFAULT_DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DEFAULT_DB_NAME = os.getenv("DB_NAME", "qaima")

BATCH_SIZE = 2000
RATIO_MAX_ABS = Decimal("999999.9999")
RATIO_QUANT = Decimal("0.0001")

UPSERT_SQL = """
INSERT INTO financial (
    stock_id,
    report_date,
    version,
    fiscal_year,
    period_no,
    fiscal_quarter,
    period_type,
    filing_date,
    currency,
    source,
    revenue,
    gross_profit,
    operating_income,
    net_income,
    assets,
    current_assets,
    liabilities,
    current_liabilities,
    equity,
    capital_stock,
    retained_earnings,
    cash_and_equivalents,
    accounts_receivable,
    inventories,
    short_term_borrowings,
    current_portion_of_long_term_borrowings,
    long_term_borrowings,
    operating_cash_flow,
    investing_cash_flow,
    financing_cash_flow,
    interest_expense,
    capex_ppe,
    capex_intangible,
    depreciation_expense,
    amortization_expense,
    income_tax_expense,
    market_cap,
    operating_margin,
    net_margin,
    roe,
    per,
    pbr,
    created_at,
    updated_at
) VALUES (
    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
    %s, %s, %s, %s,
    %s, %s, %s, %s, %s,
    %s, %s,
    %s, %s, %s,
    %s, %s, %s,
    %s, %s, %s,
    %s, %s, %s, %s, %s, %s,
    %s,
    %s, %s, %s, %s, %s,
    %s, %s
)
ON DUPLICATE KEY UPDATE
    report_date = VALUES(report_date),
    version = VALUES(version),
    fiscal_quarter = VALUES(fiscal_quarter),
    filing_date = VALUES(filing_date),
    currency = VALUES(currency),
    source = VALUES(source),
    revenue = VALUES(revenue),
    gross_profit = VALUES(gross_profit),
    operating_income = VALUES(operating_income),
    net_income = VALUES(net_income),
    assets = VALUES(assets),
    current_assets = VALUES(current_assets),
    liabilities = VALUES(liabilities),
    current_liabilities = VALUES(current_liabilities),
    equity = VALUES(equity),
    capital_stock = VALUES(capital_stock),
    retained_earnings = VALUES(retained_earnings),
    cash_and_equivalents = VALUES(cash_and_equivalents),
    accounts_receivable = VALUES(accounts_receivable),
    inventories = VALUES(inventories),
    short_term_borrowings = VALUES(short_term_borrowings),
    current_portion_of_long_term_borrowings = VALUES(current_portion_of_long_term_borrowings),
    long_term_borrowings = VALUES(long_term_borrowings),
    operating_cash_flow = VALUES(operating_cash_flow),
    investing_cash_flow = VALUES(investing_cash_flow),
    financing_cash_flow = VALUES(financing_cash_flow),
    interest_expense = VALUES(interest_expense),
    capex_ppe = VALUES(capex_ppe),
    capex_intangible = VALUES(capex_intangible),
    depreciation_expense = VALUES(depreciation_expense),
    amortization_expense = VALUES(amortization_expense),
    income_tax_expense = VALUES(income_tax_expense),
    market_cap = VALUES(market_cap),
    operating_margin = VALUES(operating_margin),
    net_margin = VALUES(net_margin),
    roe = VALUES(roe),
    per = VALUES(per),
    pbr = VALUES(pbr),
    updated_at = VALUES(updated_at)
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fast financial CSV importer")
    parser.add_argument(
        "--csv",
        action="append",
        default=[],
        help="CSV path. Can be repeated. If omitted, data/financials_kospi.csv and data/financials_kosdaq.csv are auto-discovered.",
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
        raise SystemExit("No financial CSV found. Use --csv or place files in data/financials_kospi.csv and data/financials_kosdaq.csv")
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


def normalize_stock_code(stock_code: str) -> str:
    trimmed = stock_code.strip().upper()
    if trimmed.isdigit():
        return f"{int(trimmed):06d}"
    return trimmed


def normalize_period_type(raw: str) -> str:
    code = raw.strip().upper()
    if code not in {"A", "Q", "H", "TTM"}:
        raise ValueError(f"Unsupported period_type: {raw}")
    return code


def normalize_period_no(period_type: str, raw_period_no: Optional[str]) -> int:
    value = parse_int(raw_period_no)
    if period_type == "A":
        return 1
    if period_type == "TTM":
        return 0
    if period_type == "Q":
        if value is None or value < 1 or value > 4:
            raise ValueError("Q period_no must be 1~4")
        return value
    if period_type == "H":
        if value is None or value < 1 or value > 2:
            raise ValueError("H period_no must be 1~2")
        return value
    raise ValueError(f"Unsupported period_type: {period_type}")


def parse_int(raw: Optional[str]) -> Optional[int]:
    if raw is None:
        return None
    value = raw.strip()
    if not value:
        return None
    try:
        return int(value)
    except ValueError:
        return int(float(value))


def parse_decimal(raw: Optional[str]) -> Optional[Decimal]:
    if raw is None:
        return None
    value = raw.strip()
    if not value:
        return None
    try:
        return Decimal(value)
    except InvalidOperation as exc:
        raise ValueError(f"Invalid decimal value: {raw}") from exc


def parse_ratio_decimal(raw: Optional[str]) -> Optional[Decimal]:
    value = parse_decimal(raw)
    if value is None:
        return None
    if abs(value) > RATIO_MAX_ABS:
        return None
    return value.quantize(RATIO_QUANT)


def normalize_ratio_value(value: Optional[Decimal]) -> Optional[Decimal]:
    if value is None:
        return None
    if abs(value) > RATIO_MAX_ABS:
        return None
    return value.quantize(RATIO_QUANT)


def recalc_ratio(field_name: str, row: dict) -> Optional[Decimal]:
    revenue = parse_decimal(row.get("revenue"))
    operating_income = parse_decimal(row.get("operating_income"))
    net_income = parse_decimal(row.get("net_income"))
    equity = parse_decimal(row.get("equity"))
    market_cap = parse_decimal(row.get("market_cap"))

    if field_name == "operating_margin":
        if revenue in (None, Decimal("0")) or operating_income is None:
            return None
        return normalize_ratio_value(operating_income / revenue)

    if field_name == "net_margin":
        if revenue in (None, Decimal("0")) or net_income is None:
            return None
        return normalize_ratio_value(net_income / revenue)

    if field_name == "roe":
        if equity in (None, Decimal("0")) or net_income is None:
            return None
        return normalize_ratio_value(net_income / equity)

    if field_name == "per":
        if net_income in (None, Decimal("0")) or market_cap is None:
            return None
        return normalize_ratio_value(market_cap / net_income)

    if field_name == "pbr":
        if equity in (None, Decimal("0")) or market_cap is None:
            return None
        return normalize_ratio_value(market_cap / equity)

    return None


def resolve_ratio_decimal(
    field_name: str,
    row: dict,
    csv_path: Path,
    line_no: int,
) -> Optional[Decimal]:
    raw = row.get(field_name)
    value = parse_decimal(raw)
    normalized = normalize_ratio_value(value)
    if value is None or normalized is not None:
        return normalized

    logger.warning(
        "Out-of-range ratio detected. file=%s line=%s stock_code=%s name=%s field=%s raw=%s report_date=%s period_type=%s period_no=%s",
        csv_path,
        line_no,
        row.get("stock_code"),
        row.get("name"),
        field_name,
        raw,
        row.get("report_date"),
        row.get("period_type"),
        row.get("period_no"),
    )

    if not sys.stdin.isatty():
        logger.warning("stdin is not interactive. field=%s will be stored as NULL", field_name)
        return None

    while True:
        answer = input(f"[{row.get('stock_code')}] {field_name} out of range. Recalculate fallback? [y/n]: ").strip().lower()
        if answer == "y":
            recalculated = recalc_ratio(field_name, row)
            logger.warning(
                "Recalculated ratio. stock_code=%s field=%s raw=%s recalculated=%s",
                row.get("stock_code"),
                field_name,
                raw,
                recalculated,
            )
            return recalculated
        if answer == "n":
            logger.warning(
                "Ratio stored as NULL. stock_code=%s field=%s raw=%s",
                row.get("stock_code"),
                field_name,
                raw,
            )
            return None
        print("Type 'y' or 'n'.")


def parse_date(raw: Optional[str]) -> Optional[str]:
    if raw is None:
        return None
    value = raw.strip()
    if not value:
        return None
    return datetime.strptime(value, "%Y-%m-%d").date().isoformat()


def trim_to_none(raw: Optional[str]) -> Optional[str]:
    if raw is None:
        return None
    value = raw.strip()
    return value or None


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


def to_params(
    row: dict,
    stock_id_by_code: Dict[str, int],
    now: datetime,
    csv_path: Path,
    line_no: int,
) -> Optional[Tuple]:
    stock_code = row.get("stock_code") or row.get("code")
    if not stock_code:
        raise ValueError("stock_code missing")

    normalized_code = normalize_stock_code(stock_code)
    stock_id = stock_id_by_code.get(normalized_code)
    if stock_id is None:
        return None

    report_date = parse_date(require(row, "report_date"))
    version = parse_int(require(row, "version"))
    fiscal_year = parse_int(require(row, "fiscal_year"))
    period_type = normalize_period_type(require(row, "period_type"))
    period_no = normalize_period_no(period_type, row.get("period_no"))
    fiscal_quarter = parse_int(row.get("fiscal_quarter"))
    filing_date = parse_date(row.get("filing_date"))
    currency = trim_to_none(row.get("currency"))
    source = trim_to_none(row.get("source"))

    now_sql = now.strftime("%Y-%m-%d %H:%M:%S.%f")
    return (
        stock_id,
        report_date,
        version,
        fiscal_year,
        period_no,
        fiscal_quarter,
        period_type,
        filing_date,
        currency,
        source,
        parse_decimal(row.get("revenue")),
        parse_decimal(row.get("gross_profit")),
        parse_decimal(row.get("operating_income")),
        parse_decimal(row.get("net_income")),
        parse_decimal(row.get("assets")),
        parse_decimal(row.get("current_assets")),
        parse_decimal(row.get("liabilities")),
        parse_decimal(row.get("current_liabilities")),
        parse_decimal(row.get("equity")),
        parse_decimal(row.get("capital_stock")),
        parse_decimal(row.get("retained_earnings")),
        parse_decimal(row.get("cash_and_equivalents")),
        parse_decimal(row.get("accounts_receivable")),
        parse_decimal(row.get("inventories")),
        parse_decimal(row.get("short_term_borrowings")),
        parse_decimal(row.get("current_portion_of_long_term_borrowings")),
        parse_decimal(row.get("long_term_borrowings")),
        parse_decimal(row.get("operating_cash_flow")),
        parse_decimal(row.get("investing_cash_flow")),
        parse_decimal(row.get("financing_cash_flow")),
        parse_decimal(row.get("interest_expense")),
        parse_decimal(row.get("capex_ppe")),
        parse_decimal(row.get("capex_intangible")),
        parse_decimal(row.get("depreciation_expense")),
        parse_decimal(row.get("amortization_expense")),
        parse_decimal(row.get("income_tax_expense")),
        parse_decimal(row.get("market_cap")),
        resolve_ratio_decimal("operating_margin", row, csv_path, line_no),
        resolve_ratio_decimal("net_margin", row, csv_path, line_no),
        resolve_ratio_decimal("roe", row, csv_path, line_no),
        resolve_ratio_decimal("per", row, csv_path, line_no),
        resolve_ratio_decimal("pbr", row, csv_path, line_no),
        now_sql,
        now_sql,
    )


def require(row: dict, key: str) -> str:
    value = trim_to_none(row.get(key))
    if value is None:
        raise ValueError(f"Required column is blank: {key}")
    return value


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
            logger.info("financial import start: %s", csv_path)
            batch: List[Tuple] = []
            file_upserted = 0
            file_skipped_unknown_stock = 0
            file_skipped_blank = 0
            file_failed = 0

            for line_no, row in enumerate(read_csv_rows(csv_path), start=2):
                if not any((value or "").strip() for value in row.values()):
                    file_skipped_blank += 1
                    continue

                try:
                    params = to_params(row, stock_id_by_code, datetime.now(), csv_path, line_no)
                    if params is None:
                        code = normalize_stock_code(row.get("stock_code") or row.get("code") or "")
                        missing_stock_counts[code] = missing_stock_counts.get(code, 0) + 1
                        file_skipped_unknown_stock += 1
                        continue

                    batch.append(params)
                    if len(batch) >= batch_size:
                        flushed = flush_batch(conn, batch)
                        file_upserted += flushed
                        batch.clear()
                except Exception as exc:
                    file_failed += 1
                    logger.warning(
                        "Financial CSV row skipped. file=%s line=%s cause=%s",
                        csv_path,
                        line_no,
                        exc,
                    )

            if batch:
                flushed = flush_batch(conn, batch)
                file_upserted += flushed
                batch.clear()

            total_upserted += file_upserted
            total_skipped_unknown_stock += file_skipped_unknown_stock
            total_skipped_blank += file_skipped_blank
            total_failed += file_failed

            logger.info(
                "financial import done: file=%s upserted=%d skippedBlank=%d skippedUnknownStock=%d failed=%d",
                csv_path,
                file_upserted,
                file_skipped_blank,
                file_skipped_unknown_stock,
                file_failed,
            )

        logger.info(
            "financial import summary: files=%d upserted=%d skippedBlank=%d skippedUnknownStock=%d failed=%d",
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
