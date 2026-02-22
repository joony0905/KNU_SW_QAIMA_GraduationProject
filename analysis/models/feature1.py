# app/models/feature1.py
from __future__ import annotations

from datetime import date, datetime
from typing import Dict, List, Optional

from pydantic import BaseModel, Field

from models.indicator import IndicatorBundle


class OhlcvItem(BaseModel):
    t: datetime
    o: float
    h: float
    l: float
    c: float
    v: float


class FinancialSummaryItem(BaseModel):
    fiscal_year: Optional[int] = None
    report_date: Optional[date] = None
    revenue: Optional[float] = None
    operating_income: Optional[float] = None
    net_income: Optional[float] = None

    class Config:
        extra = "allow"


class Feature1Request(BaseModel):
    stock_code: str
    freq: str
    ohlcv: List[OhlcvItem]
    financials: List[FinancialSummaryItem] = []
    include_explain: bool = False


class OhlcvSummary(BaseModel):
    count: int
    from_: Optional[datetime] = Field(default=None, alias="from")
    to: Optional[datetime] = None
    last_close: Optional[float] = None

    class Config:
        allow_population_by_field_name = True


class FinancialSummary(BaseModel):
    years: List[int] = []
    revenue: Dict[int, Optional[float]] = {}
    operating_income: Dict[int, Optional[float]] = {}
    net_income: Dict[int, Optional[float]] = {}


class IndicatorSlots(BaseModel):
    valuation: Optional[dict] = None
    growth: Optional[dict] = None
    profitability: Optional[dict] = None


class Feature1Metrics(BaseModel):
    stock_code: str
    as_of: datetime
    ohlcv_summary: OhlcvSummary
    financial_summary: FinancialSummary
    indicators: Optional[IndicatorBundle] = None
    schema_version: str


class Feature1Explain(BaseModel):
    text: str


class Feature1Meta(BaseModel):
    warnings: List[str] = []


class Feature1Response(BaseModel):
    metrics: Feature1Metrics
    explain: Optional[Feature1Explain] = None
    meta: Optional[Feature1Meta] = None
