# analysis/app/models/feature2.py
from __future__ import annotations

from datetime import datetime
from typing import List, Optional, Literal

from pydantic import BaseModel, Field, ConfigDict


# =========================
# Common
# =========================
# MVP에서는 ONE_D만 사용 (ONE_W는 확장 대비)
Freq = Literal["ONE_D", "ONE_W"]


# =========================
# Time-series DTOs
# =========================
class RelativePoint(BaseModel):
    """
    Rebased relative series point.
    value: 기준 시점 대비 상대 변화율
    예) +0.012 = +1.2%
    """
    model_config = ConfigDict(extra="forbid")

    t: datetime
    value: float


class BandPoint(BaseModel):
    """
    Distribution band at time t.
    p20/p80: peer 분포 분위수
    """
    model_config = ConfigDict(extra="forbid")

    t: datetime
    p20: float
    p80: float


# =========================
# Peer summary
# =========================
class PeerItem(BaseModel):
    """
    Selected peer summary (설명/디버깅/툴팁용).
    """
    model_config = ConfigDict(extra="forbid")

    stock_code: str
    company_name: Optional[str] = None

    # --- screening / liquidity ---
    market_cap: Optional[float] = None
    avg_turnover: Optional[float] = None   # 평균 거래대금
    avg_volume: Optional[float] = None     # 평균 거래량

    # --- similarity / scoring ---
    corr: Optional[float] = None           # 수익률 상관계수
    cap_score: Optional[float] = None      # |log(Mi / M0)|
    score: Optional[float] = None          # 최종 score (정렬 기준)


# =========================
# Request
# =========================
class PeerClusterRequest(BaseModel):
    """
    PeerCluster v1 request (rule-based).

    - industry_id: 산업 ID
    - anchor_stock_code: 기준 종목
    - freq/window: 시계열 조건
    - peer_count: 선택할 peer 개수

    Liquidity filters (v1 scope):
    - 거래대금 / 거래량 기준 필터
    """
    model_config = ConfigDict(extra="forbid")

    industry_id: int = Field(..., ge=1)
    anchor_stock_code: str = Field(..., min_length=1)

    freq: Freq = Field(default="ONE_D")
    window: int = Field(default=90, ge=30, le=365)
    peer_count: int = Field(default=8, ge=3, le=30)

    # --- Liquidity filters ---
    liquidity_min_turnover: Optional[float] = Field(
        default=None, ge=0, description="평균 거래대금 하한"
    )
    liquidity_min_volume: Optional[float] = Field(
        default=None, ge=0, description="평균 거래량 하한"
    )

    # 거래대금 기준 상위 K개만 사전 필터링 (선택)
    liquidity_top_k_turnover: Optional[int] = Field(
        default=None, ge=5, le=500
    )


# =========================
# Response
# =========================
class PeerClusterResponse(BaseModel):
    """
    PeerCluster v1 response.

    Spring-side PeerClusterDto / Redis 캐시와 1:1 대응.
    """
    model_config = ConfigDict(extra="forbid")

    # 알고리즘 식별자 (버전 고정)
    method: str = Field(default="INDUSTRY_CORR_V1")

    industry_id: int
    freq: Freq
    window: int
    peer_count: int

    # --- chart data ---
    centroid: List[RelativePoint] = Field(default_factory=list)
    band: List[BandPoint] = Field(default_factory=list)

    # --- peer list ---
    peers: List[PeerItem] = Field(default_factory=list)

    # 계산 기준 시각
    as_of: datetime

    # partial success / 실패 사유
    warnings: List[str] = Field(default_factory=list)