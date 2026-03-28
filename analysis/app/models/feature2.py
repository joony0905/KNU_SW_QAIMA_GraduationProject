# analysis/app/models/feature2.py
from __future__ import annotations

from datetime import datetime
from typing import List, Optional, Literal

from pydantic import BaseModel, Field, ConfigDict


Freq = Literal["ONE_D", "ONE_W"]  # MVP: ONE_D 권장, 미지원 시 warning + fallback


# =========================
# Shared points
# =========================
class RelativePoint(BaseModel):
    """Rebased relative series point (e.g., +0.012 = +1.2%)."""
    model_config = ConfigDict(extra="forbid")

    t: datetime
    value: float


class BandPoint(BaseModel):
    """Distribution band at time t (p20/p80)."""
    model_config = ConfigDict(extra="forbid")

    t: datetime
    p20: float
    p80: float


# =========================
# Peer item (contract)
# - market_cap/cap_score 제거
# - leader/follower 판단 포함
# =========================
Relation = Literal["LEADER", "FOLLOWER", "COINCIDENT", "UNKNOWN"]


class PeerItem(BaseModel):
    """Selected peer summary (market-cap excluded, leader/follower included)."""
    model_config = ConfigDict(extra="forbid")

    stock_code: str
    company_name: Optional[str] = None

    # screening/scoring metadata (optional in response)
    avg_turnover: Optional[float] = None   # 거래대금(평균)
    avg_volume: Optional[float] = None     # 거래량(평균)

    # same-time correlation (co-movement)
    corr: Optional[float] = None

    # lead/lag detection
    # best_lag convention (ONE_D 기준, days):
    #   +k  => peer가 k일 "뒤따름" (anchor가 선행)  -> FOLLOWER
    #   -k  => peer가 k일 "앞섬"   (peer가 선행)    -> LEADER
    best_lag: Optional[int] = None
    lead_lag_corr: Optional[float] = None
    relation: Relation = "UNKNOWN"

    # overall ranking score (MVP: corr 중심 + lead_lag_corr 보조)
    score: Optional[float] = None


# =========================
# Request
# =========================
class PeerClusterRequest(BaseModel):
    """
    PeerCluster v1 request.
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
    peer_count: int = Field(default=8, ge=3, le=30)

    # Liquidity filters (v1 scope)
    liquidity_min_turnover: Optional[float] = Field(default=None, ge=0)
    liquidity_min_volume: Optional[float] = Field(default=None, ge=0)

    # If you want “top K by turnover” prefilter (optional)
    liquidity_top_k_turnover: Optional[int] = Field(default=None, ge=5, le=500)

    # lead/lag params (optional, MVP default)
    # - max_lag: ONE_D 기준 최대 시차(일)
    max_lag: int = Field(default=5, ge=1, le=20)


# =========================
# Response
# =========================
class PeerClusterResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    method: str = Field(default="INDUSTRY_CORR_V1")

    industry_id: int
    anchor_stock_code: str

    freq: Freq
    window: int
    peer_count: int

    centroid: List[RelativePoint] = Field(default_factory=list)
    band: List[BandPoint] = Field(default_factory=list)
    peers: List[PeerItem] = Field(default_factory=list)

    as_of: datetime
    warnings: List[str] = Field(default_factory=list)