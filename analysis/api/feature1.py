# app/api/feature1.py
from datetime import datetime, timezone
from fastapi import APIRouter


from models.feature1 import (
    Feature1Request,
    Feature1Response,
    Feature1Metrics,
    Feature1Explain,
    Feature1Meta,
    OhlcvSummary,
    FinancialSummary,
)
from models.indicator import (
    IndicatorBundle,
    calculate_ema,
    calculate_bb,
    calculate_stoch,
)

from services.llm_client import analyze_feature1 as analyze_feature1_llm


router = APIRouter(
    prefix="/api/v1/analysis",
    tags=["feature1"],
)


@router.post("/feature1", response_model=Feature1Response)
async def analyze_stock(req: Feature1Request) -> Feature1Response:
    """
    QAIMA Feature1
    - OHLCV 기반 지표 계산 (EMA / BB / Stoch)
    - include_explain=true 인 경우에만 LLM 호출
    - 지표 계산 실패 시 fallback + warning
    """

    warnings: list[str] = []

    # ======================
    # OHLCV Summary
    # ======================
    ohlcv = req.ohlcv

    if ohlcv:
        ohlcv_summary = OhlcvSummary(
            count=len(ohlcv),
            from_=ohlcv[0].t,
            to=ohlcv[-1].t,
            last_close=ohlcv[-1].c,
        )
    else:
        ohlcv_summary = OhlcvSummary(count=0)

    # ======================
    # Financial Summary (stub)
    # ======================
    financial_summary = FinancialSummary()

    # ======================
    # Indicator Calculation
    # ======================
    indicators = IndicatorBundle(
        ema={},
        bb20_2=None,
        stoch14_3_3=None,
        warnings=[],
    )

    try:
        # EMA
        indicators.ema = {
            "20": calculate_ema(ohlcv, 20),
            "60": calculate_ema(ohlcv, 60),
            "120": calculate_ema(ohlcv, 120),
        }

        # Bollinger Bands
        indicators.bb20_2 = calculate_bb(ohlcv, period=20, k=2)

        # Stochastic
        indicators.stoch14_3_3 = calculate_stoch(
            ohlcv,
            k_period=14,
            d_period=3,
            smooth=3,
        )

    except Exception:
        # 계산 실패 시 fallback 유지
        indicators.ema = {}
        indicators.bb20_2 = None
        indicators.stoch14_3_3 = None
        indicators.warnings.append("INDICATOR_CALC_FAILED")

    # ======================
    # Metrics
    # ======================
    metrics = Feature1Metrics(
        stock_code=req.stock_code,
        as_of=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        ohlcv_summary=ohlcv_summary,
        financial_summary=financial_summary,
        indicators=indicators,
        schema_version="1.0",
    )

    # ======================
    # Explain (LLM - optional)
    # ======================
    explain = None
    if req.include_explain:
        try:
            explain_text = await analyze_feature1_llm(req, metrics)
            explain = Feature1Explain(text=explain_text)
        except Exception:
            warnings.append("LLM_EXPLAIN_FAILED")

    # ======================
    # Final Response
    # ======================
    meta = Feature1Meta(warnings=warnings) if warnings else None

    return Feature1Response(
        metrics=metrics,
        explain=explain,
        meta=meta,
    )