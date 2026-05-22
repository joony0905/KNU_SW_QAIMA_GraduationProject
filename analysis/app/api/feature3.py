from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.models.feature3 import PortfolioAnalyzeRequest, PortfolioAnalyzeResponse
from app.services.feature3_llm import deterministic_feature3_explain, generate_feature3_explain
from app.services.portfolio import analyze_portfolio

log = logging.getLogger(__name__)

router = APIRouter(prefix="/feature3", tags=["feature3"])


@router.post("/analysis", response_model=PortfolioAnalyzeResponse)
async def analyze(req: PortfolioAnalyzeRequest) -> PortfolioAnalyzeResponse:
    try:
        response = analyze_portfolio(req)
        if req.options.include_llm_explain:
            explain = await generate_feature3_explain(response, req.options.llm_vendor, req.invest_level)
            response.explain = explain if explain.text else deterministic_feature3_explain(response)
            response.warnings.extend(explain.warnings)
        else:
            response.explain = deterministic_feature3_explain(response)
        return response
    except Exception as e:
        log.exception("feature3 portfolio analysis failed")
        raise HTTPException(status_code=500, detail=f"FEATURE3_ANALYSIS_FAILED:{e.__class__.__name__}")
