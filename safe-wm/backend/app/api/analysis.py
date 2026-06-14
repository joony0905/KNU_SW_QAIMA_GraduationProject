from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.agents.workflow import run_analysis
from app.models.analysis import AnalysisRequest
from app.services.customer_repository import CustomerNotFoundError, get_customer

router = APIRouter(prefix="/api/wm/customers", tags=["analysis"])


@router.post("/{customer_id}/analysis")
async def analyze_customer(customer_id: str, request: AnalysisRequest | None = None) -> dict:
    req = request or AnalysisRequest()
    try:
        customer = get_customer(customer_id)
    except CustomerNotFoundError as exc:
        raise HTTPException(status_code=404, detail={"code": "CUSTOMER_NOT_FOUND", "message": str(exc)}) from exc
    try:
        return await run_analysis(
            customer,
            include_debug=req.includeDebug,
            include_qaima=req.includeQaima,
            force_unsafe_explanation=req.forceUnsafeExplanation,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail={"code": "ANALYSIS_FAILED", "message": str(exc)},
        ) from exc

