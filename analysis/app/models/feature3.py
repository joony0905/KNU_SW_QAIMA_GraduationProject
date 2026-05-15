from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


ProfileType = Literal["CONSERVATIVE", "NEUTRAL", "AGGRESSIVE"]
PortfolioType = Literal[
    "CURRENT",
    "STABLE",
    "BALANCED",
    "AGGRESSIVE",
    "PSYCHOLOGICAL",
    "MIN_VOL",
    "MAX_SHARPE",
    "UTILITY_OPTIMAL",
    "THEORETICAL_UTILITY",
    "OVERLAY_BALANCED",
    "QUALITY_TILT",
    "MOMENTUM_AWARE",
    "NEWS_GUARDED",
    "DIVERSIFICATION_TILT",
]
RiskLevel = Literal["LOW", "MID", "HIGH"]
Suitability = Literal["CONSERVATIVE_THAN_PROFILE", "ALIGNED", "AGGRESSIVE_THAN_PROFILE"]
OptimizationStatus = Literal["SUCCESS", "NEAREST_FEASIBLE", "FAILED"]
WarningSeverity = Literal["INFO", "WARN", "ERROR"]


class Feature3HoldingRequest(BaseModel):
    stock_code: str = Field(min_length=1)
    company_name: str | None = None
    quantity: float = Field(gt=0)
    avg_price: float = Field(gt=0)
    current_price: float | None = Field(default=None, gt=0)
    currency: str | None = "KRW"
    asset_type: Literal["EQUITY"] = "EQUITY"


class Feature3CashPositionRequest(BaseModel):
    currency: str = "KRW"
    amount: float = Field(ge=0)


class Feature3RiskProfileRequest(BaseModel):
    risk_tolerance_score: float = Field(ge=0, le=1)
    risk_aversion_gamma: float | None = Field(default=None, ge=1, le=10)
    profile_type: ProfileType | None = None
    target_volatility: float | None = Field(default=None, ge=0)


class Feature3AnalyzeOptions(BaseModel):
    view_mode: Literal["BASIC", "ADVANCED"] = "BASIC"
    price_basis: Literal["ADJUSTED_CLOSE", "CLOSE"] = "ADJUSTED_CLOSE"
    covariance_model: Literal["LEDOIT_WOLF", "SAMPLE_COVARIANCE"] = "LEDOIT_WOLF"
    return_type: Literal["LOG_RETURN"] = "LOG_RETURN"
    lookback_trading_days: int = Field(default=252, ge=1)
    fetch_calendar_days: int = Field(default=370, ge=1)
    annualization_factor: int = Field(default=252, ge=1)
    cache_policy: Literal["CORE_ONLY", "REUSE_AVAILABLE", "REFRESH_MISSING_ONLY", "FORCE_REFRESH"] = "CORE_ONLY"
    selected_overlays: list[str] = Field(default_factory=list)
    include_frontier: bool = False
    include_diagnostics: bool = False
    include_llm_explain: bool = False
    llm_vendor: str | None = None
    risk_free_rate: float | None = Field(default=None, ge=0)
    risk_free_rate_source: str | None = None
    risk_free_rate_as_of: str | None = None
    max_cash_weight: float | None = Field(default=None, ge=0, le=1)


class Feature3OverlaySignal(BaseModel):
    stock_code: str
    company_name: str | None = None
    overlay_type: str
    label: str
    score: float = Field(ge=-1, le=1)
    severity: WarningSeverity = "INFO"
    source: str = "FEATURE3"
    evidence: str | None = None


class PortfolioAnalyzeRequest(BaseModel):
    portfolio_id: int | None = None
    holdings: list[Feature3HoldingRequest] = Field(min_length=1)
    cash_positions: list[Feature3CashPositionRequest] = Field(default_factory=list)
    risk_profile: Feature3RiskProfileRequest
    options: Feature3AnalyzeOptions = Field(default_factory=Feature3AnalyzeOptions)
    overlay_signals: list[Feature3OverlaySignal] = Field(default_factory=list)


class Feature3Warning(BaseModel):
    code: str
    message: str
    user_message: str | None = None
    severity: WarningSeverity = "INFO"
    target: str | None = None


class Feature3PricePolicy(BaseModel):
    requested: Literal["ADJUSTED_CLOSE", "CLOSE"]
    used: Literal["ADJUSTED_CLOSE", "CLOSE"]
    warnings: list[str] = Field(default_factory=list)


class Feature3RiskProfileEcho(BaseModel):
    risk_tolerance_score: float
    risk_aversion_gamma: float
    profile_type: ProfileType
    target_volatility: float


class Feature3DataPolicy(BaseModel):
    return_type: Literal["LOG_RETURN"]
    lookback_trading_days: int
    fetch_calendar_days: int
    annualization_factor: int
    min_observations: int
    max_missing_rate: float
    max_common_missing_rate: float


class Feature3ExcludedHolding(BaseModel):
    stock_code: str
    company_name: str | None = None
    reason: Literal["INSUFFICIENT_OBSERVATIONS", "HIGH_MISSING_RATE", "INVALID_PRICE_SERIES"]
    available_price_count: int
    expected_trading_day_count: int
    missing_rate: float


class Feature3PriceSeriesQuality(BaseModel):
    stock_code: str
    company_name: str | None = None
    requested_price_basis: Literal["ADJUSTED_CLOSE", "CLOSE"]
    used_price_basis: Literal["ADJUSTED_CLOSE", "CLOSE"]
    source: Literal["DUMMY", "DB", "KIS", "MARKETSTACK", "MIXED", "EMPTY", "UNAVAILABLE", "CACHE"] = "DUMMY"
    cache_status: Literal["HIT", "STALE", "MISS", "BYPASSED"] = "BYPASSED"
    expected_trading_day_count: int
    available_price_count: int
    missing_rate: float
    fallback_used: bool
    warnings: list[Feature3Warning] = Field(default_factory=list)


class Feature3DataQuality(BaseModel):
    expected_trading_day_count: int
    common_price_count: int = 0
    common_return_sample_size: int = 0
    common_missing_rate: float = 1.0
    included_holding_count: int
    excluded_holding_count: int
    price_series: list[Feature3PriceSeriesQuality] = Field(default_factory=list)
    excluded_holdings: list[Feature3ExcludedHolding] = Field(default_factory=list)


class Feature3RiskFreePolicy(BaseModel):
    rate: float
    source: str
    as_of: str | None = None
    instrument_code: str | None = None
    instrument_name: str | None = None


class Feature3PolicyEcho(BaseModel):
    price_policy: Feature3PricePolicy
    risk_profile: Feature3RiskProfileEcho
    data_policy: Feature3DataPolicy
    data_quality: Feature3DataQuality
    risk_free_policy: Feature3RiskFreePolicy


class Feature3Weight(BaseModel):
    stock_code: str
    company_name: str | None = None
    asset_type: Literal["EQUITY", "CASH"]
    weight: float
    quantity: float | None = None
    avg_price: float | None = None
    current_price: float | None = None
    cost_basis_value: float | None = None
    market_value: float | None = None
    unrealized_pnl: float | None = None
    unrealized_return_rate: float | None = None


class Feature3RiskContribution(BaseModel):
    stock_code: str
    company_name: str | None = None
    weight: float
    volatility: float | None = None
    marginal_risk_contribution: float | None = None
    risk_contribution: float
    risk_contribution_pct: float


class Feature3PortfolioResult(BaseModel):
    type: PortfolioType
    label: str
    expected_return: float | None = None
    raw_historical_return: float | None = None
    display_expected_return: float | None = None
    is_display_capped: bool = False
    additional_required_cash: float | None = None
    theoretical_risky_allocation: float | None = None
    constraint_binding: str | None = None
    sharpe_ratio: float | None = None
    volatility: float
    target_volatility: float | None = None
    achieved_volatility: float | None = None
    risk_level: RiskLevel
    optimization_status: OptimizationStatus = "SUCCESS"
    weights: list[Feature3Weight] = Field(default_factory=list)
    risk_contributions: list[Feature3RiskContribution] = Field(default_factory=list)
    user_description: str | None = None


class Feature3Summary(BaseModel):
    risk_level: RiskLevel
    suitability: Suitability
    annualized_volatility: float
    target_volatility: float
    volatility_gap: float
    main_risk_drivers: list[str] = Field(default_factory=list)


class Feature3RiskDriver(BaseModel):
    code: str
    severity: RiskLevel
    title: str
    description: str
    affected_holdings: list[str] = Field(default_factory=list)
    source: Literal["CORE_RISK", "FEATURE1", "FEATURE2"] = "CORE_RISK"


class Feature3CovarianceDiagnostics(BaseModel):
    requested_covariance_model: Literal["LEDOIT_WOLF", "SAMPLE_COVARIANCE"]
    used_covariance_model: Literal["LEDOIT_WOLF", "SAMPLE_COVARIANCE"]
    sample_size: int
    asset_count: int
    shrinkage_lambda: float | None = None
    min_eigenvalue: float | None = None
    condition_number: float | None = None
    positive_definite: bool | None = None


class Feature3AdvancedResult(BaseModel):
    candidate_portfolios: list[Feature3PortfolioResult] = Field(default_factory=list)
    covariance_diagnostics: Feature3CovarianceDiagnostics | None = None
    correlation_matrix: dict | None = None
    frontier: list[dict] = Field(default_factory=list)
    expected_return_policy: dict | None = None


class Feature3FreshnessOverlay(BaseModel):
    overlay_type: str
    status: Literal["FRESH", "STALE", "MISSING", "REFRESHED", "NOT_REQUESTED"]
    analyzed_at: str | None = None
    data_as_of: str | None = None
    expires_at: str | None = None
    source: Literal["CACHE", "REFRESHED", "NOT_REQUESTED"] = "NOT_REQUESTED"


class Feature3Freshness(BaseModel):
    core_risk_as_of: str | None = None
    price_series_as_of: str | None = None
    has_mixed_freshness: bool = False
    newest_data_at: str | None = None
    oldest_data_at: str | None = None
    user_message: str | None = None
    overlays: list[Feature3FreshnessOverlay] = Field(default_factory=list)


class Feature3OverlayInsightCard(BaseModel):
    overlay_type: str
    title: str
    description: str
    severity: WarningSeverity = "INFO"
    source: Literal["FEATURE1", "FEATURE2", "FEATURE3"] = "FEATURE3"
    cache_status: Literal["HIT", "STALE", "MISS", "BYPASSED"] = "BYPASSED"
    affected_holdings: list[str] = Field(default_factory=list)


class Feature3HoldingOverlayRow(BaseModel):
    stock_code: str
    company_name: str | None = None
    overlay_type: str
    label: str
    value: str | None = None
    severity: WarningSeverity = "INFO"
    source: Literal["FEATURE1", "FEATURE2", "FEATURE3"] = "FEATURE3"
    cache_status: Literal["HIT", "STALE", "MISS", "BYPASSED"] = "BYPASSED"


class Feature3OverlayResult(BaseModel):
    insight_cards: list[Feature3OverlayInsightCard] = Field(default_factory=list)
    holding_overlay_table: list[Feature3HoldingOverlayRow] = Field(default_factory=list)
    advanced_overlay_exposure: list[dict] = Field(default_factory=list)
    overlay_signals: list[Feature3OverlaySignal] = Field(default_factory=list)
    adjusted_portfolios: list[Feature3PortfolioResult] = Field(default_factory=list)
    visualizations: list[dict] = Field(default_factory=list)
    explanations: list[dict] = Field(default_factory=list)


class Feature3ExplainResult(BaseModel):
    provider: str = "DETERMINISTIC"
    model: str | None = None
    text: str | None = None
    sections: dict[str, dict] | None = None
    overall: dict | None = None
    warnings: list[Feature3Warning] = Field(default_factory=list)


class PortfolioAnalyzeResponse(BaseModel):
    policy: Feature3PolicyEcho
    summary: Feature3Summary
    current_portfolio: Feature3PortfolioResult
    basic_portfolios: list[Feature3PortfolioResult]
    risk_drivers: list[Feature3RiskDriver] = Field(default_factory=list)
    advanced: Feature3AdvancedResult | None = None
    overlays: Feature3OverlayResult = Field(default_factory=Feature3OverlayResult)
    explain: Feature3ExplainResult | None = None
    warnings: list[Feature3Warning] = Field(default_factory=list)
    freshness: Feature3Freshness
