from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel


class IndicatorPoint1(BaseModel):
    t: str
    value: Optional[float] = None


class BollingerPoint(BaseModel):
    t: str
    mid: Optional[float] = None
    upper: Optional[float] = None
    lower: Optional[float] = None


class StochPoint(BaseModel):
    t: str
    k: Optional[float] = None
    d: Optional[float] = None


class IndicatorSpec(BaseModel):
    ema: Optional[dict] = None
    bollinger: Optional[dict] = None
    stochastic: Optional[dict] = None


class IndicatorBundle(BaseModel):
    spec: Optional[IndicatorSpec] = None
    ema20: Optional[List[IndicatorPoint1]] = None
    bb20_2: Optional[List[BollingerPoint]] = None
    stoch14_3_3: Optional[List[StochPoint]] = None
    warnings: List[str] = []
