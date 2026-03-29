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
    anchor_stock_code: str,
    freq: str,
    window: int,
    peer_count: int,
    warnings: list[str],
) -> PeerClusterResponse:
    return PeerClusterResponse(
        method="INDUSTRY_CORR_V1",
        industry_id=industry_id,
        anchor_stock_code=anchor_stock_code,
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
    # Contract is snake_case. camelCase keys are accepted temporarily
    # for backward compatibility with pre-refactor callers.
    raw_industry_id = _safe_int(req_raw.get("industry_id") or req_raw.get("industryId")) or 0
    raw_anchor_stock_code = _safe_str(
        req_raw.get("anchor_stock_code") or req_raw.get("anchorStockCode")
    ) or ""
    raw_freq = _safe_str(req_raw.get("freq")) or "ONE_D"
    raw_window = _safe_int(req_raw.get("window")) or 0
    raw_peer_count = _safe_int(req_raw.get("peer_count") or req_raw.get("peerCount")) or 0

    try:
        req = PeerClusterRequest.model_validate(req_raw)
        try:
            return compute_peer_cluster_v1(req)
        except Exception as e:
            log.exception("peer_cluster compute failed")
            return _default_response(
                industry_id=req.industry_id,
                anchor_stock_code=req.anchor_stock_code,
                freq=req.freq,
                window=req.window,
                peer_count=req.peer_count,
                warnings=["PEER_CLUSTER_FAILED", e.__class__.__name__],
            )

    except ValidationError:
        return _default_response(
            industry_id=raw_industry_id,
            anchor_stock_code=raw_anchor_stock_code,
            freq=raw_freq if raw_freq in ("ONE_D", "ONE_W") else "ONE_D",
            window=raw_window,
            peer_count=raw_peer_count,
            warnings=["PEER_CLUSTER_BAD_REQUEST"],
        )
