from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.models.feature3 import Feature3AnalysisRequest, PortfolioAnalyzeResponse
from app.services.feature3_llm import deterministic_feature3_explain, generate_feature3_explain
from app.services.portfolio import analyze_portfolio

log = logging.getLogger(__name__)

router = APIRouter(prefix="/feature3", tags=["feature3"])


@router.post("/analysis", response_model=PortfolioAnalyzeResponse)
async def analyze(req: Feature3AnalysisRequest) -> PortfolioAnalyzeResponse:
    portfolio_req = req.to_portfolio_request()
    try:
        response = analyze_portfolio(portfolio_req)
        language_code = portfolio_req.options.language_code if portfolio_req.options else None
        if portfolio_req.options.include_llm_explain:
            explain = await generate_feature3_explain(
                response,
                portfolio_req.options.llm_vendor,
                portfolio_req.invest_level,
                language_code,
            )
            response.explain = explain if explain.text else deterministic_feature3_explain(response, language_code)
            response.warnings.extend(explain.warnings)
        else:
            response.explain = deterministic_feature3_explain(response, language_code)
        return response
    except Exception as e:
        log.exception("feature3 portfolio analysis failed")
        raise HTTPException(status_code=500, detail=f"FEATURE3_ANALYZE_FAILED:{e.__class__.__name__}")
