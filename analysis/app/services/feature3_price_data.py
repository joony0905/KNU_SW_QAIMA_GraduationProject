from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import httpx

from app.models.feature3 import Feature3Warning


@dataclass(frozen=True)
class Feature3PricePoint:
    ts: datetime
    close: float


@dataclass(frozen=True)
class Feature3PriceSeriesResult:
    stock_code: str
    company_name: str | None
    requested_price_basis: str
    used_price_basis: str
    source: str
    cache_status: str
    expected_trading_day_count: int
    available_price_count: int
    missing_rate: float
    fallback_used: bool
    points: list[Feature3PricePoint] = field(default_factory=list)
    warnings: list[Feature3Warning] = field(default_factory=list)


def fetch_feature3_price_series(
    stock_code: str,
    company_name: str | None,
    requested_price_basis: str,
    lookback_trading_days: int,
    fetch_calendar_days: int,
    timeout_sec: float = 15.0,
) -> Feature3PriceSeriesResult:
    if os.getenv("QAIMA_FEATURE3_DISABLE_PRICE_FETCH", "").lower() in {"1", "true", "yes"}:
        return _unavailable_result(
            stock_code,
            company_name,
            requested_price_basis,
            lookback_trading_days,
            "FEATURE3_PRICE_FETCH_DISABLED",
            "Feature3 price fetch is disabled by environment flag.",
        )

    base_url = os.getenv("SPRING_BASE_URL") or os.getenv("QAIMA_SPRING_BASE_URL") or "http://localhost:8080"
    if not base_url:
        return _unavailable_result(
            stock_code,
            company_name,
            requested_price_basis,
            lookback_trading_days,
            "SPRING_BASE_URL_MISSING",
            "SPRING_BASE_URL is not configured.",
        )

    # Feature3는 차트용 candle DTO가 아니라 price_ohlcv 기반 전용 price-series 계약을 사용한다.
    # Spring은 price_ohlcv 조회와 KIS fallback 책임을 갖고, FastAPI는 계산만 수행한다.
    url = f"{base_url.rstrip('/')}/api/v1/feature3/market-data/price-series"
    params = {
        "stockCode": stock_code,
        "requestedPriceBasis": requested_price_basis,
        "lookbackTradingDays": str(lookback_trading_days),
        "fetchCalendarDays": str(fetch_calendar_days),
    }

    try:
        with httpx.Client(timeout=timeout_sec) as client:
            response = client.get(url, params=params)
            response.raise_for_status()
            envelope = response.json()
    except Exception as exc:
        return _unavailable_result(
            stock_code,
            company_name,
            requested_price_basis,
            lookback_trading_days,
            "PRICE_SERIES_FETCH_FAILED",
            _fetch_error_message(exc),
        )

    data = envelope.get("data") or {}
    source = data.get("source") or "UNKNOWN"
    price_rows = data.get("data") or []
    points = [_to_price_point(row) for row in price_rows]
    points = [point for point in points if point is not None]
    available = int(data.get("availablePriceCount") or len(points))
    expected = int(data.get("expectedTradingDayCount") or lookback_trading_days)
    missing_rate = float(data.get("missingRate") or round(max(0.0, 1.0 - (available / max(expected, 1))), 6))
    warnings = _envelope_warnings(envelope)
    warnings.extend(_data_warnings(data, stock_code))

    return Feature3PriceSeriesResult(
        stock_code=data.get("stockCode") or stock_code,
        company_name=data.get("companyName") or company_name,
        requested_price_basis=requested_price_basis,
        used_price_basis=data.get("usedPriceBasis") or "RAW_CLOSE",
        source=_feature3_source(source),
        cache_status=data.get("cacheStatus") or _cache_status(source),
        expected_trading_day_count=expected,
        available_price_count=available,
        missing_rate=missing_rate,
        fallback_used=bool(data.get("fallbackUsed", requested_price_basis == "ADJUSTED_CLOSE")),
        points=points,
        warnings=warnings,
    )


def _to_price_point(row: dict[str, Any]) -> Feature3PricePoint | None:
    try:
        if "ts" in row and "close" in row:
            return Feature3PricePoint(
                ts=datetime.fromisoformat(str(row["ts"]).replace("Z", "+00:00")),
                close=float(row["close"]),
            )

        # 이전 chart/candles 응답을 읽던 개발 환경과의 임시 호환 경로.
        return Feature3PricePoint(
            ts=datetime.fromtimestamp(float(row["t"]), tz=timezone.utc),
            close=float(row["c"]),
        )
    except Exception:
        return None


def _fetch_error_message(exc: Exception) -> str:
    if isinstance(exc, httpx.HTTPStatusError):
        status = exc.response.status_code
        body = exc.response.text[:300] if exc.response is not None else ""
        return f"Spring Feature3 price-series fetch failed: HTTP_{status} body={body}"
    return f"Spring Feature3 price-series fetch failed: {exc.__class__.__name__}"


def _envelope_warnings(envelope: dict[str, Any]) -> list[Feature3Warning]:
    raw_warnings = (
        ((envelope.get("meta") or {}).get("warnings"))
        or ([((envelope.get("meta") or {}).get("warning"))] if (envelope.get("meta") or {}).get("warning") else [])
        or []
    )
    return [
        Feature3Warning(
            code=str(warning),
            message=f"Spring candle endpoint returned warning: {warning}",
            user_message=None,
            severity="WARN",
            target="priceSeries",
        )
        for warning in raw_warnings
        if warning
    ]


def _data_warnings(data: dict[str, Any], default_target: str) -> list[Feature3Warning]:
    raw_warnings = data.get("warnings") or []
    warnings: list[Feature3Warning] = []
    for warning in raw_warnings:
        if not isinstance(warning, dict):
            continue
        code = warning.get("code")
        if not code:
            continue
        warnings.append(
            Feature3Warning(
                code=str(code),
                message=str(warning.get("message") or code),
                user_message=warning.get("userMessage"),
                severity=str(warning.get("severity") or "WARN"),
                target=warning.get("target") or default_target,
            )
        )
    return warnings


def _unavailable_result(
    stock_code: str,
    company_name: str | None,
    requested_price_basis: str,
    lookback_trading_days: int,
    code: str,
    message: str,
) -> Feature3PriceSeriesResult:
    return Feature3PriceSeriesResult(
        stock_code=stock_code,
        company_name=company_name,
        requested_price_basis=requested_price_basis,
        used_price_basis="RAW_CLOSE",
        source="UNAVAILABLE",
        cache_status="MISS",
        expected_trading_day_count=lookback_trading_days,
        available_price_count=0,
        missing_rate=1.0,
        fallback_used=requested_price_basis == "ADJUSTED_CLOSE",
        points=[],
        warnings=[
            Feature3Warning(
                code=code,
                message=message,
                user_message="가격 시계열을 가져오지 못해 임시 분석값을 표시합니다.",
                severity="WARN",
                target=stock_code,
            )
        ],
    )


def _feature3_source(source: str) -> str:
    return source if source in {"DB", "KIS", "YAHOO", "MARKETSTACK", "MIXED", "EMPTY"} else "UNAVAILABLE"


def _cache_status(source: str) -> str:
    if source == "DB":
        return "HIT"
    if source in {"KIS", "YAHOO", "MARKETSTACK", "MIXED"}:
        return "MISS"
    return "MISS"
