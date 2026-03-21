# app/models/indicator.py
from __future__ import annotations

from datetime import datetime
from math import sqrt
from typing import Dict, List, Optional

from pydantic import BaseModel, Field


# ======================
# Indicator Points
# ======================

class IndicatorPoint1(BaseModel):
    t: datetime
    value: Optional[float] = None


class BollingerPoint(BaseModel):
    t: datetime
    upper: Optional[float] = None
    mid: Optional[float] = None
    lower: Optional[float] = None


class StochPoint(BaseModel):
    t: datetime
    k: Optional[float] = None
    d: Optional[float] = None


# ======================
# Indicator Bundle
# ======================

class IndicatorBundle(BaseModel):
    """
    Feature1 Indicator Contract
    - ema: null 금지 (fallback = {})
    - bb20_2 / stoch14_3_3: 실패 시 null 허용
    - warnings: 항상 배열
    """
    ema: Dict[str, List[IndicatorPoint1]] = Field(default_factory=dict)
    bb20_2: Optional[List[BollingerPoint]] = None
    stoch14_3_3: Optional[List[StochPoint]] = None
    warnings: List[str] = Field(default_factory=list)


# ======================
# Calculations
# ======================

def calculate_ema(ohlcv: List, period: int) -> List[IndicatorPoint1]:
    result: List[IndicatorPoint1] = []
    k = 2 / (period + 1)
    ema_prev: Optional[float] = None

    for i, p in enumerate(ohlcv):
        if i < period - 1:
            result.append(IndicatorPoint1(t=p.t, value=None))
            continue

        if ema_prev is None:
            sma = sum(x.c for x in ohlcv[i - period + 1:i + 1]) / period
            ema = sma
        else:
            ema = p.c * k + ema_prev * (1 - k)

        ema_prev = ema
        result.append(IndicatorPoint1(t=p.t, value=ema))

    return result


def calculate_bb(ohlcv: List, period: int = 20, k: float = 2.0) -> List[BollingerPoint]:
    result: List[BollingerPoint] = []

    for i, p in enumerate(ohlcv):
        if i < period - 1:
            result.append(BollingerPoint(t=p.t, upper=None, mid=None, lower=None))
            continue

        window = [x.c for x in ohlcv[i - period + 1:i + 1]]
        mean = sum(window) / period
        variance = sum((x - mean) ** 2 for x in window) / period
        std = sqrt(variance)

        result.append(
            BollingerPoint(
                t=p.t,
                upper=mean + k * std,
                mid=mean,
                lower=mean - k * std,
            )
        )

    return result


def calculate_stoch(ohlcv: List, k_period: int = 14, d_period: int = 3, smooth: int = 3) -> List[StochPoint]:
    raw_k: List[Optional[float]] = []
    smooth_k: List[Optional[float]] = []
    d_values: List[Optional[float]] = []
    result: List[StochPoint] = []

    for i, p in enumerate(ohlcv):
        if i < k_period - 1:
            raw_k.append(None)
        else:
            window = ohlcv[i - k_period + 1:i + 1]
            low = min(x.l for x in window)
            high = max(x.h for x in window)
            if high == low:
                raw_k.append(None)
            else:
                raw_k.append((p.c - low) / (high - low) * 100)

    for i in range(len(raw_k)):
        if raw_k[i] is None or i < smooth - 1:
            smooth_k.append(None)
        else:
            vals = [x for x in raw_k[i - smooth + 1:i + 1] if x is not None]
            smooth_k.append(sum(vals) / len(vals) if vals else None)

    for i in range(len(smooth_k)):
        if smooth_k[i] is None or i < d_period - 1:
            d_values.append(None)
        else:
            vals = [x for x in smooth_k[i - d_period + 1:i + 1] if x is not None]
            d_values.append(sum(vals) / len(vals) if vals else None)

    for i, p in enumerate(ohlcv):
        result.append(StochPoint(t=p.t, k=smooth_k[i], d=d_values[i]))

    return result