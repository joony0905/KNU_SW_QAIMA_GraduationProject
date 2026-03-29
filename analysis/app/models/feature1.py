# app/models/feature1.py
from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field, field_validator, ConfigDict

from app.models.indicator import IndicatorBundle


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

    model_config = ConfigDict(extra="allow")


class Feature1Request(BaseModel):
    # Contract is snake_case only.
    model_config = ConfigDict(extra="forbid")

    stock_code: str
    freq: str
    ohlcv: List[OhlcvItem]
    financials: List[FinancialSummaryItem] = []
    include_explain: bool = False


# ======================
# Derived / Summary Data
# ======================

class OhlcvSummary(BaseModel):
    count: int
    from_: Optional[datetime] = Field(default=None, alias="from")
    to: Optional[datetime] = None
    last_close: Optional[float] = None

    model_config = ConfigDict(populate_by_name=True)


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
    indicator_summary: Optional[str] = None
    schema_version: str


# ======================
# Explain / Warnings
# ======================

class Feature1Explain(BaseModel):
    text: str = Field(..., description="LLM raw text")


# ======================
# Final Response
# ======================

class Feature1Response(BaseModel):
    metrics: Feature1Metrics
    explain: Optional[Feature1Explain] = None
    warnings: List[str] = []
