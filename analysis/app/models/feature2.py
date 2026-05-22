# analysis/app/models/feature2.py
from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional, Literal

from pydantic import BaseModel, Field, ConfigDict


Freq = Literal["ONE_D", "ONE_W"]  # MVP: ONE_D 권장, 미지원 시 경고 후 대체 처리


# =========================
# 공통 시계열 포인트
# =========================
class RelativePoint(BaseModel):
    """Rebased relative series point (e.g., +0.012 = +1.2%)."""
    model_config = ConfigDict(extra="forbid")

    t: datetime
    value: float


class BandPoint(BaseModel):
    """Distribution band at time t (p20/p80)."""
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    t: datetime
    p20: float
    p80: float


# =========================
# Peer 항목 응답 계약
# - market_cap/cap_score 제거
# - leader/follower 판단 포함
# =========================
Relation = Literal["LEADER", "FOLLOWER", "COINCIDENT", "UNKNOWN"]
AdjustmentMethod = Literal["SIMPLE_SUBTRACTION", "BETA_RESIDUAL_RESERVED"]
AdjustmentBasis = Literal["RAW_ONLY", "SIMPLE_SUBTRACTION", "FALLBACK_RAW"]
DisplayStatus = Literal[
    "SELECTED",
    "ELIGIBLE_NOT_SELECTED",
    "DISPLAY_ONLY",
    "LOW_CORR",
    "RAW_ONLY",
    "ADJUSTED_ONLY",
    "FALLBACK_RAW",
]


class PeerItem(BaseModel):
    """선정/표시용 peer 요약. 시총은 제외하고 선행/후행 관계를 포함한다."""
    model_config = ConfigDict(extra="forbid")

    stock_code: str
    company_name: Optional[str] = None

    # 필터링/점수화 메타데이터. 응답에서는 선택적으로 포함된다.
    avg_turnover: Optional[float] = None   # 거래대금(평균)
    avg_volume: Optional[float] = None     # 거래량(평균)

    # 동시점 상관계수. 함께 움직이는 정도를 나타낸다.
    corr: Optional[float] = None
    adjusted_corr: Optional[float] = None
    corr_stability: Optional[float] = None
    raw_corr_valid: bool = False
    adjusted_corr_valid: bool = False
    adjusted_return_sample_size: Optional[int] = None
    adjusted_return_coverage_ratio: Optional[float] = None
    adjustment_basis: AdjustmentBasis = "RAW_ONLY"
    display_status: DisplayStatus = "DISPLAY_ONLY"

    # 선행/후행 탐지
    # best_lag 기준(ONE_D 기준, 일):
    #   +k  => peer가 k일 "뒤따름" (anchor가 선행)  -> FOLLOWER
    #   -k  => peer가 k일 "앞섬"   (peer가 선행)    -> LEADER
    best_lag: Optional[int] = None
    lead_lag_corr: Optional[float] = None
    lag_confidence: Optional[float] = None
    relation: Relation = "UNKNOWN"

    liquidity_similarity_score: Optional[float] = None
    volatility_similarity_score: Optional[float] = None

    # 전체 순위 점수. 기존 score 별칭은 호환성을 위해 유지한다.
    score: Optional[float] = None
    peer_score: Optional[float] = None


# =========================
# 요청
# =========================
class PeerClusterRequest(BaseModel):
    """
    PeerCluster v1 요청.
    - industry_id: 산업 ID
    - anchor_stock_code: 기준 종목(메인 종목)
    - freq/window: 시계열 주기/윈도우
    - peer_count: 뽑을 피어 개수
    - liquidity_*: 거래대금/거래량 필터(있으면 적용)
    """
    model_config = ConfigDict(extra="forbid")

    industry_id: int = Field(..., ge=1)
    anchor_stock_code: str = Field(..., min_length=1)

    freq: Freq = Field(default="ONE_D")
    window: int = Field(default=90, ge=30, le=365)
    from_: Optional[datetime] = Field(default=None, alias="from")
    to: Optional[datetime] = Field(default=None)
    peer_count: int = Field(default=8, ge=3, le=30)

    # 유동성 필터(v1 범위)
    liquidity_min_turnover: Optional[float] = Field(default=None, ge=0)
    liquidity_min_volume: Optional[float] = Field(default=None, ge=0)

    # 거래대금 상위 K개 사전 필터. 선택적으로 사용한다.
    liquidity_top_k_turnover: Optional[int] = Field(default=None, ge=5, le=500)

    # 선행/후행 파라미터. 선택값이며 MVP 기본값을 사용한다.
    # - max_lag: ONE_D 기준 최대 시차(일)
    max_lag: int = Field(default=5, ge=1, le=20)
    display_limit: int = Field(default=30, ge=1, le=100)


# =========================
# 응답
# =========================
class PeerClusterResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    method: str = Field(default="INDUSTRY_CORR_V1")

    industry_id: int
    anchor_stock_code: str

    freq: Freq
    window: int
    peer_count: int
    requested_peer_count: Optional[int] = None
    effective_peer_count: Optional[int] = None
    raw_candidate_count: Optional[int] = None
    evaluated_candidate_count: Optional[int] = None
    eligible_candidate_count: Optional[int] = None
    selected_peer_count: Optional[int] = None
    displayed_candidate_count: Optional[int] = None
    display_limit: Optional[int] = None

    adjustment_method: AdjustmentMethod = "SIMPLE_SUBTRACTION"
    industry_index_code: Optional[str] = None
    industry_index_name: Optional[str] = None
    adjusted_return_sample_size: Optional[int] = None
    adjusted_return_coverage_ratio: Optional[float] = None
    adjustment_valid: bool = False
    adjustment_fallback_reason: Optional[str] = None

    anchor_series: List[RelativePoint] = Field(default_factory=list)
    industry_index_series: List[RelativePoint] = Field(default_factory=list)
    # TODO: 프론트가 peer_centroid/peer_band로 완전히 전환되면 centroid/band는 폐기한다.
    centroid: List[RelativePoint] = Field(default_factory=list)
    band: List[BandPoint] = Field(default_factory=list)
    peer_centroid: List[RelativePoint] = Field(default_factory=list)
    peer_band: List[BandPoint] = Field(default_factory=list)
    peer_coverage: List[RelativePoint] = Field(default_factory=list)
    peers: List[PeerItem] = Field(default_factory=list)
    candidates: List[PeerItem] = Field(default_factory=list)

    as_of: datetime
    interpretation_note: Optional[str] = None
    warnings: List[str] = Field(default_factory=list)


class NewsSentimentItemRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    url: str
    title: str
    publisher: str
    published_at: datetime
    focus_text: str


class NewsSentimentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: List[NewsSentimentItemRequest] = Field(default_factory=list)
    model: str


class NewsSentimentResultItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    url: str
    sentiment_score: float
    predicted_label: Optional[str] = None
    negative_prob: Optional[float] = None
    neutral_prob: Optional[float] = None
    positive_prob: Optional[float] = None
    model_version: Optional[str] = None
    input_format_version: Optional[str] = None


class NewsSentimentResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    results: List[NewsSentimentResultItem] = Field(default_factory=list)
    warnings: List[str] = Field(default_factory=list)


class Feature2ExplainMetrics(BaseModel):
    model_config = ConfigDict(extra="forbid")

    stock: Optional[Dict[str, Any]] = None
    industry: Optional[Dict[str, Any]] = None
    base_rate: Optional[Dict[str, Any]] = None
    macro_rates: Optional[Dict[str, Any]] = None
    macro_trend_summaries: List[Dict[str, Any]] = Field(default_factory=list)
    stock_investor_flow_summary: Optional[Dict[str, Any]] = None
    market_investor_flow_summary: Optional[Dict[str, Any]] = None
    short_selling: Optional[Dict[str, Any]] = None
    base_rate_trend_summary: Optional[Dict[str, Any]] = None
    short_selling_trend_summary: Optional[Dict[str, Any]] = None
    industry_index: Optional[Dict[str, Any]] = None
    peer_cluster_summary: Optional[Dict[str, Any]] = None
    news_sentiment_summary: Optional[Dict[str, Any]] = None
    recent_news: List[Dict[str, Any]] = Field(default_factory=list)


class Feature2ExplainRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    stock_code: str
    freq: Optional[Freq] = None
    window: Optional[int] = None
    llm_vendor: Optional[str] = None
    invest_level: Optional[str] = None
    metrics: Feature2ExplainMetrics = Field(default_factory=Feature2ExplainMetrics)


class Feature2ExplainResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    explain: Optional[str] = None
    warnings: List[str] = Field(default_factory=list)
