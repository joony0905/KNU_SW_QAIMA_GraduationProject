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


class FinancialPointItem(BaseModel):
    fiscal_year: Optional[int] = None
    fiscal_quarter: Optional[int] = None
    fiscal_half: Optional[int] = None
    period_no: Optional[int] = None
    period_type: Optional[str] = None
    report_date: Optional[date] = None
    revenue: Optional[float] = None
    operating_income: Optional[float] = None
    net_income: Optional[float] = None
    assets: Optional[float] = None
    liabilities: Optional[float] = None
    equity: Optional[float] = None
    current_assets: Optional[float] = None
    current_liabilities: Optional[float] = None
    inventories: Optional[float] = None
    interest_expense: Optional[float] = None
    operating_cash_flow: Optional[float] = None
    capex: Optional[float] = None

    model_config = ConfigDict(extra="allow")


class MarketContext(BaseModel):
    as_of: Optional[date] = None
    currency: Optional[str] = None
    shares_outstanding: Optional[float] = None


# ======================
# Derived / Summary Data
# ======================

class OhlcvSummary(BaseModel):
    count: int
    from_: Optional[datetime] = Field(default=None, alias="from")
    to: Optional[datetime] = None
    last_close: Optional[float] = None

    model_config = ConfigDict(populate_by_name=True)


class FinancialSeries(BaseModel):
    years: List[int] = []
    revenue: Dict[int, Optional[float]] = {}
    operating_income: Dict[int, Optional[float]] = {}
    net_income: Dict[int, Optional[float]] = {}


class ValuationMetrics(BaseModel):
    per: Optional[float] = None
    pbr: Optional[float] = None
    psr: Optional[float] = None
    market_cap: Optional[float] = None


class ProfitabilityMetrics(BaseModel):
    roe: Optional[float] = None
    roa: Optional[float] = None
    operating_margin: Optional[float] = None
    net_margin: Optional[float] = None


class StabilityMetrics(BaseModel):
    debt_ratio: Optional[float] = None
    current_ratio: Optional[float] = None
    quick_ratio: Optional[float] = None
    interest_coverage_ratio: Optional[float] = None


class GrowthMetrics(BaseModel):
    revenue_growth: Optional[float] = None
    eps_growth: Optional[float] = None
    free_cash_flow: Optional[float] = None


class PerShareMetrics(BaseModel):
    eps: Optional[float] = None
    bps: Optional[float] = None


class MarketSnapshotMetrics(BaseModel):
    as_of: Optional[str] = None
    currency: Optional[str] = None
    valuation: ValuationMetrics = Field(default_factory=ValuationMetrics)
    profitability: ProfitabilityMetrics = Field(default_factory=ProfitabilityMetrics)
    stability: StabilityMetrics = Field(default_factory=StabilityMetrics)
    growth: GrowthMetrics = Field(default_factory=GrowthMetrics)
    per_share: PerShareMetrics = Field(default_factory=PerShareMetrics)


class Feature1Request(BaseModel):
    # Contract is snake_case only.
    stock_code: str
    freq: str
    from_: Optional[date] = Field(default=None, alias="from")
    to: Optional[date] = None
    ohlcv: List[OhlcvItem]
    financials: List[FinancialPointItem] = []
    market_context: Optional[MarketContext] = None
    market_snapshot: Optional[MarketSnapshotMetrics] = None
    include_explain: bool = False
    llm_vendor: Optional[str] = None
    invest_level: Optional[str] = None

    model_config = ConfigDict(extra="forbid", populate_by_name=True)


# ======================
# Metrics / Analysis
# ======================

class Feature1Metrics(BaseModel):
    stock_code: str
    as_of: str
    ohlcv_summary: OhlcvSummary
    financial_series: FinancialSeries
    market_snapshot: MarketSnapshotMetrics
    indicators: IndicatorBundle
    indicator_summary: Optional[str] = None
    schema_version: str


# ======================
# Explain / Warnings
# ======================

class Feature1ExplainSection(BaseModel):
    summary: str
    title: Optional[str] = None
    bullets: List[str] = []


class Feature1ExplainSections(BaseModel):
    price_flow: Feature1ExplainSection
    market_snapshot: Feature1ExplainSection
    indicators: Feature1ExplainSection
    financial_timeline: Feature1ExplainSection


class Feature1ExplainOverall(BaseModel):
    summary: str
    bullets: List[str] = []
    risks: List[str] = []
    conclusion: Optional[str] = None


class Feature1Explain(BaseModel):
    sections: Feature1ExplainSections
    overall: Feature1ExplainOverall
    text: Optional[str] = Field(default=None, description="LLM raw text")


# ======================
# Final Response
# ======================

class Feature1Response(BaseModel):
    metrics: Feature1Metrics
    explain: Optional[Feature1Explain] = None
    warnings: List[str] = []
