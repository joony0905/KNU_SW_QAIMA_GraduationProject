# app/api/feature2.py
from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from fastapi import APIRouter
from pydantic import ValidationError

from app.models.feature2 import PeerClusterRequest, PeerClusterResponse
from app.services.clustering import compute_peer_cluster_v1

log = logging.getLogger(__name__)

router = APIRouter(prefix="/feature2", tags=["feature2"])


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


def _safe_int(v: Any) -> Optional[int]:
    try:
        if v is None:
            return None
        return int(v)
    except Exception:
        return None


def _safe_str(v: Any) -> Optional[str]:
    try:
        if v is None:
            return None
        s = str(v).strip()
        return s if s else None
    except Exception:
        return None


def _default_response(
    *,
    industry_id: int,
    freq: str,
    window: int,
    peer_count: int,
    warnings: list[str],
) -> PeerClusterResponse:
    return PeerClusterResponse(
        method="INDUSTRY_CORR_V1",
        industry_id=industry_id,
        freq=freq,  # must be "ONE_D" | "ONE_W"
        window=window,
        peer_count=peer_count,
        centroid=[],
        band=[],
        peers=[],
        as_of=_now_utc(),
        warnings=warnings,
    )


@router.post("/peer-cluster", response_model=PeerClusterResponse)
def peer_cluster(req_raw: Dict[str, Any]) -> PeerClusterResponse:
    """
    PeerCluster v1 (rule-based)
    - throw 금지
    - 항상 200 (FastAPI validation 422를 피하기 위해 raw dict로 받고 내부에서 검증)
    """
    # 1) raw에서 가능한 값들만 "관측용"으로 먼저 추출 (validation 실패해도 echo 가능)
    raw_industry_id = _safe_int(req_raw.get("industry_id") or req_raw.get("industryId")) or 0
    raw_freq = _safe_str(req_raw.get("freq")) or "ONE_D"
    raw_window = _safe_int(req_raw.get("window")) or 0
    raw_peer_count = _safe_int(req_raw.get("peer_count") or req_raw.get("peerCount")) or 0

    # 2) Pydantic 검증 + 정상 계산
    try:
        req = PeerClusterRequest.model_validate(req_raw)
        try:
            return compute_peer_cluster_v1(req)
        except Exception as e:
            # 계산 실패(런타임) — 서버에만 스택트레이스 남기고 응답은 짧은 코드로
            log.exception("peer_cluster compute failed")
            return _default_response(
                industry_id=req.industry_id,
                freq=req.freq,
                window=req.window,
                peer_count=req.peer_count,  # 요청 echo
                warnings=["PEER_CLUSTER_FAILED", e.__class__.__name__],
            )

    except ValidationError:
        # 요청 자체가 불량이어도 200 유지
        # (여기서 detail을 길게 넣지 말고, 코드 중심으로)
        return _default_response(
            industry_id=raw_industry_id,
            freq=raw_freq if raw_freq in ("ONE_D", "ONE_W") else "ONE_D",
            window=raw_window,
            peer_count=raw_peer_count,
            warnings=["PEER_CLUSTER_BAD_REQUEST"],
        )