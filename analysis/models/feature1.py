# app/models/feature1.py
from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Dict, List, Optional

from pydantic import BaseModel, Field, field_validator

from models.indicator import IndicatorBundle


# ======================
# Input / Request Models
# ======================

class OhlcvItem(BaseModel):
    t: datetime
    o: float
    h: float
    l: float
    c: float
    v: float

    @field_validator("t", mode="before")
    @classmethod
    def parse_t(cls, v):
        # epoch seconds (int/float)
        if isinstance(v, (int, float)):
            return datetime.fromtimestamp(v, tz=timezone.utc)
        return v


class FinancialSummaryItem(BaseModel):
    fiscal_year: Optional[int] = None
    report_date: Optional[date] = None
    revenue: Optional[float] = None
    operating_income: Optional[float] = None
    net_income: Optional[float] = None

    class Config:
        extra = "allow"


class Feature1Request(BaseModel):
    stock_code: str = Field(alias="stockCode")
    freq: str
    ohlcv: List[OhlcvItem]
    financials: List[FinancialSummaryItem] = []
    include_explain: bool = Field(default=False, alias="includeExplain")

    class Config:
        populate_by_name = True  # pydantic v2


# ======================
# Derived / Summary Data
# ======================

class OhlcvSummary(BaseModel):
    count: int
    from_: Optional[datetime] = Field(default=None, alias="from")
    to: Optional[datetime] = None
    last_close: Optional[float] = None

    class Config:
        populate_by_name = True


class FinancialSummary(BaseModel):
    years: List[int] = []
    revenue: Dict[int, Optional[float]] = {}
    operating_income: Dict[int, Optional[float]] = {}
    net_income: Dict[int, Optional[float]] = {}


# ======================
# Metrics / Analysis
# ======================

class Feature1Metrics(BaseModel):
    stock_code: str
    as_of: str
    ohlcv_summary: OhlcvSummary
    financial_summary: FinancialSummary
    indicators: IndicatorBundle
    schema_version: str


# ======================
# Explain / Meta
# ======================

class Feature1Explain(BaseModel):
    text: str


class Feature1Meta(BaseModel):
    warnings: List[str] = []


# ======================
# Final Response
# ======================

class Feature1Response(BaseModel):
    metrics: Feature1Metrics
    explain: Optional[Feature1Explain] = None
    meta: Optional[Feature1Meta] = None