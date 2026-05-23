# app/api/feature1.py
from datetime import date, datetime, timezone
from fastapi import APIRouter


from app.models.feature1 import (
    Feature1AnalysisRequest,
    Feature1Request,
    Feature1Response,
    Feature1Metrics,
    Feature1Explain,
    OhlcvSummary,
    FinancialSeries,
    MarketSnapshotMetrics,
    ValuationMetrics,
    ProfitabilityMetrics,
    StabilityMetrics,
    GrowthMetrics,
    PerShareMetrics,
)
from app.models.indicator import (
    IndicatorBundle,
    calculate_ema,
    calculate_bb,
    calculate_stoch,
)

from app.services.llm_client import analyze_feature1 as analyze_feature1_llm


router = APIRouter(
    prefix="/feature1",
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


def _safe_div(numerator: float | None, denominator: float | None) -> float | None:
    if numerator is None or denominator is None or denominator == 0:
        return None
    return numerator / denominator


def _ratio_percent(numerator: float | None, denominator: float | None) -> float | None:
    base = _safe_div(numerator, denominator)
    return None if base is None else base * 100


def _growth_percent(current: float | None, previous: float | None) -> float | None:
    if current is None or previous is None or previous == 0:
        return None
    return ((current - previous) / previous) * 100


def _sort_financials(financials):
    return sorted(
        [f for f in financials if f is not None],
        key=lambda f: (
            f.fiscal_year or 0,
            f.period_no or 0,
            f.report_date or date.min,
        ),
        reverse=True,
    )


def _is_previous_quarter(current, previous) -> bool:
    if current.period_no is None or previous.period_no is None or current.fiscal_year is None or previous.fiscal_year is None:
        return False
    expected_year = current.fiscal_year - 1 if current.period_no == 1 else current.fiscal_year
    expected_quarter = 4 if current.period_no == 1 else current.period_no - 1
    return previous.fiscal_year == expected_year and previous.period_no == expected_quarter


def _latest_contiguous_four_quarters(quarters):
    if len(quarters) < 4:
        return None
    latest = quarters[:4]
    for idx in range(len(latest) - 1):
        if not _is_previous_quarter(latest[idx], latest[idx + 1]):
            return None
    return latest


def _sum_attr(items, attr: str) -> float | None:
    total = 0.0
    for item in items:
        value = getattr(item, attr, None)
        if value is None:
            return None
        total += value
    return total


def _build_financial_series(financials) -> FinancialSeries:
    if not financials:
        return FinancialSeries()

    revenue: Dict[int, Optional[float]] = {}
    operating_income: Dict[int, Optional[float]] = {}
    net_income: Dict[int, Optional[float]] = {}

    for item in financials:
        if item.fiscal_year is None:
            continue
        revenue[item.fiscal_year] = item.revenue
        operating_income[item.fiscal_year] = item.operating_income
        net_income[item.fiscal_year] = item.net_income

    years = sorted(revenue.keys())
    return FinancialSeries(
        years=years,
        revenue=revenue,
        operating_income=operating_income,
        net_income=net_income,
    )


def _build_market_snapshot(req: Feature1Request, warnings: list[str]) -> MarketSnapshotMetrics:
    if req.market_snapshot is not None:
        snapshot = req.market_snapshot

        null_checks = {
            "MARKET_SNAPSHOT_AS_OF_MISSING": snapshot.as_of,
            "MARKET_SNAPSHOT_CURRENCY_MISSING": snapshot.currency,
            "MARKET_SNAPSHOT_PER_MISSING": snapshot.valuation.per,
            "MARKET_SNAPSHOT_PBR_MISSING": snapshot.valuation.pbr,
            "MARKET_SNAPSHOT_PSR_MISSING": snapshot.valuation.psr,
            "MARKET_SNAPSHOT_MARKET_CAP_MISSING": snapshot.valuation.market_cap,
            "MARKET_SNAPSHOT_EPS_MISSING": snapshot.per_share.eps,
            "MARKET_SNAPSHOT_BPS_MISSING": snapshot.per_share.bps,
            "MARKET_SNAPSHOT_ROE_MISSING": snapshot.profitability.roe,
            "MARKET_SNAPSHOT_ROA_MISSING": snapshot.profitability.roa,
            "MARKET_SNAPSHOT_OPERATING_MARGIN_MISSING": snapshot.profitability.operating_margin,
            "MARKET_SNAPSHOT_NET_MARGIN_MISSING": snapshot.profitability.net_margin,
            "MARKET_SNAPSHOT_DEBT_RATIO_MISSING": snapshot.stability.debt_ratio,
            "MARKET_SNAPSHOT_CURRENT_RATIO_MISSING": snapshot.stability.current_ratio,
            "MARKET_SNAPSHOT_QUICK_RATIO_MISSING": snapshot.stability.quick_ratio,
            "MARKET_SNAPSHOT_INTEREST_COVERAGE_RATIO_MISSING": snapshot.stability.interest_coverage_ratio,
            "MARKET_SNAPSHOT_FREE_CASH_FLOW_MISSING": snapshot.growth.free_cash_flow,
            "MARKET_SNAPSHOT_REVENUE_GROWTH_MISSING": snapshot.growth.revenue_growth,
            "MARKET_SNAPSHOT_EPS_GROWTH_MISSING": snapshot.growth.eps_growth,
        }
        missing_found = False
        for warning_code, value in null_checks.items():
            if value is None:
                warnings.append(warning_code)
                missing_found = True
        if missing_found:
            warnings.append("MARKET_SNAPSHOT_PARTIAL")
        return snapshot

    financials = _sort_financials(req.financials)
    market_context = req.market_context
    last_close = req.ohlcv[-1].c if req.ohlcv else None
    as_of = None
    if market_context and market_context.as_of:
        as_of = market_context.as_of.isoformat()
    elif req.ohlcv:
        as_of = req.ohlcv[-1].t.date().isoformat()

    shares_outstanding = market_context.shares_outstanding if market_context else None

    quarters = [f for f in financials if (f.period_type or "").upper() == "Q"]
    annuals = [f for f in financials if (f.period_type or "").upper() == "A"]
    latest_q4 = _latest_contiguous_four_quarters(quarters)
    latest_snapshot = financials[0] if financials else None

    revenue_ttm = _sum_attr(latest_q4, "revenue") if latest_q4 else None
    operating_income_ttm = _sum_attr(latest_q4, "operating_income") if latest_q4 else None
    net_income_ttm = _sum_attr(latest_q4, "net_income") if latest_q4 else None

    if revenue_ttm is None and annuals:
        revenue_ttm = annuals[0].revenue
        operating_income_ttm = annuals[0].operating_income
        net_income_ttm = annuals[0].net_income
        warnings.append("MARKET_SNAPSHOT_TTM_FALLBACK_TO_ANNUAL")

    market_cap = None if last_close is None or shares_outstanding is None else last_close * shares_outstanding
    eps = _safe_div(net_income_ttm, shares_outstanding)
    bps = _safe_div(latest_snapshot.equity if latest_snapshot else None, shares_outstanding)

    valuation = ValuationMetrics(
        per=_safe_div(last_close, eps),
        pbr=_safe_div(last_close, bps),
        psr=_safe_div(market_cap, revenue_ttm),
        market_cap=market_cap,
    )
    profitability = ProfitabilityMetrics(
        roe=_ratio_percent(net_income_ttm, latest_snapshot.equity if latest_snapshot else None),
        roa=_ratio_percent(net_income_ttm, latest_snapshot.assets if latest_snapshot else None),
        operating_margin=_ratio_percent(operating_income_ttm, revenue_ttm),
        net_margin=_ratio_percent(net_income_ttm, revenue_ttm),
    )
    stability = StabilityMetrics(
        debt_ratio=_ratio_percent(latest_snapshot.liabilities if latest_snapshot else None,
                                  latest_snapshot.equity if latest_snapshot else None),
        current_ratio=_ratio_percent(latest_snapshot.current_assets if latest_snapshot else None,
                                     latest_snapshot.current_liabilities if latest_snapshot else None),
        quick_ratio=_ratio_percent(
            (latest_snapshot.current_assets - latest_snapshot.inventories)
            if latest_snapshot and latest_snapshot.current_assets is not None and latest_snapshot.inventories is not None
            else (latest_snapshot.current_assets if latest_snapshot else None),
            latest_snapshot.current_liabilities if latest_snapshot else None,
        ),
        interest_coverage_ratio=_safe_div(operating_income_ttm, latest_snapshot.interest_expense if latest_snapshot else None),
    )
    growth = GrowthMetrics()
    if latest_q4 and len(quarters) >= 8:
        previous_q4 = quarters[4:8]
        if len(previous_q4) == 4 and _latest_contiguous_four_quarters(previous_q4):
            prev_revenue_ttm = _sum_attr(previous_q4, "revenue")
            prev_net_income_ttm = _sum_attr(previous_q4, "net_income")
            growth.revenue_growth = _growth_percent(revenue_ttm, prev_revenue_ttm)
            prev_eps = _safe_div(prev_net_income_ttm, shares_outstanding)
            growth.eps_growth = _growth_percent(eps, prev_eps)
    elif len(annuals) >= 2:
        growth.revenue_growth = _growth_percent(annuals[0].revenue, annuals[1].revenue)
        growth.eps_growth = _growth_percent(
            _safe_div(annuals[0].net_income, shares_outstanding),
            _safe_div(annuals[1].net_income, shares_outstanding),
        )
    growth.free_cash_flow = None
    if latest_snapshot and latest_snapshot.operating_cash_flow is not None and latest_snapshot.capex is not None:
        growth.free_cash_flow = latest_snapshot.operating_cash_flow - latest_snapshot.capex

    per_share = PerShareMetrics(eps=eps, bps=bps)
    snapshot = MarketSnapshotMetrics(
        as_of=as_of,
        currency=market_context.currency if market_context else None,
        valuation=valuation,
        profitability=profitability,
        stability=stability,
        growth=growth,
        per_share=per_share,
    )

    null_checks = {
        "MARKET_SNAPSHOT_AS_OF_MISSING": snapshot.as_of,
        "MARKET_SNAPSHOT_CURRENCY_MISSING": snapshot.currency,
        "MARKET_SNAPSHOT_PER_MISSING": snapshot.valuation.per,
        "MARKET_SNAPSHOT_PBR_MISSING": snapshot.valuation.pbr,
        "MARKET_SNAPSHOT_PSR_MISSING": snapshot.valuation.psr,
        "MARKET_SNAPSHOT_MARKET_CAP_MISSING": snapshot.valuation.market_cap,
        "MARKET_SNAPSHOT_EPS_MISSING": snapshot.per_share.eps,
        "MARKET_SNAPSHOT_BPS_MISSING": snapshot.per_share.bps,
        "MARKET_SNAPSHOT_ROE_MISSING": snapshot.profitability.roe,
        "MARKET_SNAPSHOT_ROA_MISSING": snapshot.profitability.roa,
        "MARKET_SNAPSHOT_OPERATING_MARGIN_MISSING": snapshot.profitability.operating_margin,
        "MARKET_SNAPSHOT_NET_MARGIN_MISSING": snapshot.profitability.net_margin,
        "MARKET_SNAPSHOT_DEBT_RATIO_MISSING": snapshot.stability.debt_ratio,
        "MARKET_SNAPSHOT_CURRENT_RATIO_MISSING": snapshot.stability.current_ratio,
        "MARKET_SNAPSHOT_QUICK_RATIO_MISSING": snapshot.stability.quick_ratio,
        "MARKET_SNAPSHOT_INTEREST_COVERAGE_RATIO_MISSING": snapshot.stability.interest_coverage_ratio,
        "MARKET_SNAPSHOT_FREE_CASH_FLOW_MISSING": snapshot.growth.free_cash_flow,
        "MARKET_SNAPSHOT_REVENUE_GROWTH_MISSING": snapshot.growth.revenue_growth,
        "MARKET_SNAPSHOT_EPS_GROWTH_MISSING": snapshot.growth.eps_growth,
    }
    missing_found = False
    for warning_code, value in null_checks.items():
        if value is None:
            warnings.append(warning_code)
            missing_found = True
    if missing_found:
        warnings.append("MARKET_SNAPSHOT_PARTIAL")

    return snapshot


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
    financial_series = _build_financial_series(req.financials)
    market_snapshot = _build_market_snapshot(req, warnings)

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
        financial_series=financial_series,
        market_snapshot=market_snapshot,
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


@router.post("/analysis", response_model=Feature1Response)
async def analyze_stock_contract(req: Feature1AnalysisRequest) -> Feature1Response:
    return await analyze_stock(req.to_feature1_request())
