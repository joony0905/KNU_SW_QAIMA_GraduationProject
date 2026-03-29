from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.models.feature2 import PeerClusterRequest, PeerClusterResponse
from app.services.clustering import compute_peer_cluster_v1

log = logging.getLogger(__name__)

router = APIRouter(prefix="/feature2", tags=["feature2"])


@router.post("/peer-cluster", response_model=PeerClusterResponse)
def peer_cluster(req: PeerClusterRequest) -> PeerClusterResponse:
    """
    PeerCluster v1 (payload-only response contract)
    - request: snake_case only (Pydantic model)
    - response: payload-only JSON (no meta/data/errors envelope)
    """

    try:
        return compute_peer_cluster_v1(req)
    except Exception as e:
        log.exception("peer_cluster compute failed")
        raise HTTPException(status_code=500, detail=f"PEER_CLUSTER_FAILED:{e.__class__.__name__}")
