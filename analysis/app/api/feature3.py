from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.models.feature3 import PortfolioAnalyzeRequest, PortfolioAnalyzeResponse
from app.services.portfolio import analyze_portfolio

log = logging.getLogger(__name__)

router = APIRouter(prefix="/feature3", tags=["feature3"])


@router.post("/analysis", response_model=PortfolioAnalyzeResponse)
async def analyze(req: PortfolioAnalyzeRequest) -> PortfolioAnalyzeResponse:
    try:
        return analyze_portfolio(req)
    except Exception as e:
        log.exception("feature3 portfolio analysis failed")
        raise HTTPException(status_code=500, detail=f"FEATURE3_ANALYSIS_FAILED:{e.__class__.__name__}")
