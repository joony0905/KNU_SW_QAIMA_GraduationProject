from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.models.feature2 import (
    NewsSentimentRequest,
    NewsSentimentResponse,
    NewsSentimentResultItem,
    Feature2ExplainRequest,
    Feature2ExplainResponse,
    PeerClusterRequest,
    PeerClusterResponse,
)
from app.services.clustering import compute_peer_cluster_v1
from app.services.llm_client import analyze_feature2_explain
from app.services.news_sentiment import score_news_sentiment_batch

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


@router.post("/news-sentiment", response_model=NewsSentimentResponse)
async def news_sentiment(req: NewsSentimentRequest) -> NewsSentimentResponse:
    try:
        results, warnings = await score_news_sentiment_batch(req)
        return NewsSentimentResponse(
            results=[
                NewsSentimentResultItem(
                    url=item["url"],
                    sentiment_score=item["sentiment_score"],
                    predicted_label=item.get("predicted_label"),
                    negative_prob=item.get("negative_prob"),
                    neutral_prob=item.get("neutral_prob"),
                    positive_prob=item.get("positive_prob"),
                    model_version=item.get("model_version"),
                    input_format_version=item.get("input_format_version"),
                )
                for item in results
            ],
            warnings=warnings,
        )
    except Exception as e:
        log.exception("news_sentiment compute failed")
        raise HTTPException(status_code=500, detail=f"NEWS_SENTIMENT_FAILED:{e.__class__.__name__}")


@router.post("/explain", response_model=Feature2ExplainResponse)
async def explain(req: Feature2ExplainRequest) -> Feature2ExplainResponse:
    try:
        return await analyze_feature2_explain(req)
    except Exception as e:
        log.exception("feature2 explain failed")
        raise HTTPException(status_code=500, detail=f"FEATURE2_EXPLAIN_FAILED:{e.__class__.__name__}")
