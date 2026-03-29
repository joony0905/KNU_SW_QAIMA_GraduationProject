# app/api/feature1.py
from datetime import datetime, timezone
from fastapi import APIRouter


from app.models.feature1 import (
    Feature1Request,
    Feature1Response,
    Feature1Metrics,
    Feature1Explain,
    OhlcvSummary,
    FinancialSummary,
)
from app.models.indicator import (
    IndicatorBundle,
    calculate_ema,
    calculate_bb,
    calculate_stoch,
)

from app.services.llm_client import analyze_feature1 as analyze_feature1_llm


router = APIRouter(
    prefix="/api/v1/analysis",
    tags=["feature1"],
)


def _fmt_num(value: float | None) -> str:
    if value is None:
        return "n/a"
    return f"{value:.4f}"


def _latest_value(points, attr: str):
    if not points:
        return None
    for point in reversed(points):
        v = getattr(point, attr, None)
        if v is not None:
            return v
    return None


def build_indicator_summary(indicators: IndicatorBundle, last_close: float | None) -> str:
    ema20 = _latest_value(indicators.ema.get("20", []), "value") if indicators.ema else None
    ema60 = _latest_value(indicators.ema.get("60", []), "value") if indicators.ema else None
    ema120 = _latest_value(indicators.ema.get("120", []), "value") if indicators.ema else None

    if all(v is not None for v in [ema20, ema60, ema120]):
        if ema20 > ema60 > ema120:
            ema_trend = "bullish"
        elif ema20 < ema60 < ema120:
            ema_trend = "bearish"
        else:
            ema_trend = "mixed"
    else:
        ema_trend = "mixed"

    bb_mid = _latest_value(indicators.bb20_2, "mid")
    bb_upper = _latest_value(indicators.bb20_2, "upper")
    bb_lower = _latest_value(indicators.bb20_2, "lower")

    bb_width = None
    bb_percent_b = None
    if bb_upper is not None and bb_lower is not None:
        bb_width = bb_upper - bb_lower
        if bb_width != 0 and last_close is not None:
            bb_percent_b = ((last_close - bb_lower) / bb_width) * 100

    stoch_k = _latest_value(indicators.stoch14_3_3, "k")
    stoch_d = _latest_value(indicators.stoch14_3_3, "d")
    stoch_zone = "neutral"
    if stoch_k is not None:
        if stoch_k >= 80:
            stoch_zone = "overbought"
        elif stoch_k <= 20:
            stoch_zone = "oversold"

    k_minus_d = None
    if stoch_k is not None and stoch_d is not None:
        k_minus_d = stoch_k - stoch_d

    return "\n".join([
        f"EMA(20/60/120): 20={_fmt_num(ema20)}, 60={_fmt_num(ema60)}, 120={_fmt_num(ema120)}, alignment={ema_trend}",
        f"BB20_2: mid={_fmt_num(bb_mid)}, upper={_fmt_num(bb_upper)}, lower={_fmt_num(bb_lower)}, width={_fmt_num(bb_width)}, percent_b={_fmt_num(bb_percent_b)}",
        f"STO14_3_3: k={_fmt_num(stoch_k)}, d={_fmt_num(stoch_d)}, zone={stoch_zone}, k_minus_d={_fmt_num(k_minus_d)}",
    ])

@router.post("/feature1", response_model=Feature1Response)
async def analyze_stock(req: Feature1Request) -> Feature1Response:
    """
    QAIMA Feature1 (payload-only response contract)
    - OHLCV 기반 지표 계산 (EMA / BB / Stoch)
    - include_explain=true 인 경우에만 LLM 호출
    - 지표 계산 실패 시 fallback + warning

    FastAPI response intentionally returns payload-only JSON (no envelope).
    Spring gateway owns public envelope(meta/data/errors).
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
        indicator_summary=build_indicator_summary(indicators, ohlcv[-1].c if ohlcv else None),
        schema_version="1.0",
    )

    # ======================
    # Explain (LLM - optional)
    # ======================
    explain = None

    if req.include_explain:
        llm_res = await analyze_feature1_llm(req, metrics)  # Feature1Response 리턴

        # explain
        explain = llm_res.explain

        # warnings merge (router warnings + llm warnings)
        warnings.extend(llm_res.warnings)
    else:
        # includeExplain=false면 서비스에서도 SKIPPED 넣지만,
        # 라우터 레벨에서도 정책적으로 남기고 싶으면 유지
        warnings.append("LLM_EXPLAIN_SKIPPED")

    # ======================
    # Final Response
    # ======================
    deduped_warnings = list(dict.fromkeys(warnings))

    return Feature1Response(
        metrics=metrics,
        explain=explain,
        warnings=deduped_warnings,
    )
