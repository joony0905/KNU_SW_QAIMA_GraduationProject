from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import httpx

from app.models.feature3 import Feature3Warning


@dataclass(frozen=True)
class Feature3BenchmarkPoint:
    ts: datetime
    close: float


@dataclass(frozen=True)
class Feature3BenchmarkSeriesResult:
    benchmark_code: str
    benchmark_name: str | None
    source: str
    benchmark_available: bool
    expected_trading_day_count: int
    available_price_count: int
    missing_rate: float
    points: list[Feature3BenchmarkPoint] = field(default_factory=list)
    warnings: list[Feature3Warning] = field(default_factory=list)


def fetch_feature3_benchmark_series(
    benchmark_code: str,
    lookback_trading_days: int,
    fetch_calendar_days: int,
    timeout_sec: float = 15.0,
) -> Feature3BenchmarkSeriesResult:
    base_url = os.getenv("SPRING_BASE_URL") or os.getenv("QAIMA_SPRING_BASE_URL") or "http://localhost:8080"
    url = f"{base_url.rstrip('/')}/api/v1/feature3/market-data/benchmark-series"
    params = {
        "benchmarkCode": benchmark_code,
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
            benchmark_code,
            lookback_trading_days,
            "BENCHMARK_FETCH_FAILED",
            _fetch_error_message(exc),
        )

    data = envelope.get("data") or {}
    price_rows = data.get("data") or []
    points = [_to_benchmark_point(row) for row in price_rows]
    points = [point for point in points if point is not None]
    expected = int(data.get("expectedTradingDayCount") or lookback_trading_days)
    available = int(data.get("availablePriceCount") or len(points))
    missing_rate = float(data.get("missingRate") or round(max(0.0, 1.0 - (available / max(expected, 1))), 6))
    warnings = _envelope_warnings(envelope)
    warnings.extend(_data_warnings(data, data.get("benchmarkCode") or benchmark_code))

    return Feature3BenchmarkSeriesResult(
        benchmark_code=data.get("benchmarkCode") or benchmark_code,
        benchmark_name=data.get("benchmarkName"),
        source=data.get("source") or "UNAVAILABLE",
        benchmark_available=bool(data.get("benchmarkAvailable", False)),
        expected_trading_day_count=expected,
        available_price_count=available,
        missing_rate=missing_rate,
        points=points,
        warnings=warnings,
    )


def _to_benchmark_point(row: dict[str, Any]) -> Feature3BenchmarkPoint | None:
    try:
        return Feature3BenchmarkPoint(
            ts=datetime.fromisoformat(str(row["ts"]).replace("Z", "+00:00")),
            close=float(row["close"]),
        )
    except Exception:
        return None


def _fetch_error_message(exc: Exception) -> str:
    if isinstance(exc, httpx.HTTPStatusError):
        status = exc.response.status_code
        body = exc.response.text[:300] if exc.response is not None else ""
        return f"Spring Feature3 benchmark-series fetch failed: HTTP_{status} body={body}"
    return f"Spring Feature3 benchmark-series fetch failed: {exc.__class__.__name__}"


def _envelope_warnings(envelope: dict[str, Any]) -> list[Feature3Warning]:
    raw_warnings = (
        ((envelope.get("meta") or {}).get("warnings"))
        or ([((envelope.get("meta") or {}).get("warning"))] if (envelope.get("meta") or {}).get("warning") else [])
        or []
    )
    return [
        Feature3Warning(
            code=str(warning),
            message=f"Spring benchmark endpoint returned warning: {warning}",
            user_message=None,
            severity="WARN",
            target="benchmarkPolicy",
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
    benchmark_code: str,
    lookback_trading_days: int,
    code: str,
    message: str,
) -> Feature3BenchmarkSeriesResult:
    return Feature3BenchmarkSeriesResult(
        benchmark_code=benchmark_code,
        benchmark_name=None,
        source="UNAVAILABLE",
        benchmark_available=False,
        expected_trading_day_count=lookback_trading_days,
        available_price_count=0,
        missing_rate=1.0,
        points=[],
        warnings=[
            Feature3Warning(
                code=code,
                message=message,
                user_message="벤치마크 가격 데이터를 가져오지 못해 CAPM 분석만 제외하고 기존 방식으로 분석합니다.",
                severity="WARN",
                target="advanced.benchmarkPolicy",
            )
        ],
    )
