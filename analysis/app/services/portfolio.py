from __future__ import annotations

from datetime import datetime, timedelta, timezone
import re

import numpy as np

from app.models.feature3 import (
    Feature3AdvancedResult,
    Feature3CovarianceDiagnostics,
    Feature3DataPolicy,
    Feature3DataQuality,
    Feature3ExcludedHolding,
    Feature3Freshness,
    Feature3OverlayResult,
    Feature3OverlaySignal,
    Feature3ProvidedBenchmarkSeries,
    Feature3ProvidedPriceSeries,
    Feature3PolicyEcho,
    Feature3PortfolioResult,
    Feature3PricePolicy,
    Feature3PriceSeriesQuality,
    Feature3RiskFreePolicy,
    Feature3RiskContribution,
    Feature3RiskDriver,
    Feature3RiskProfileEcho,
    Feature3Summary,
    Feature3Warning,
    Feature3Weight,
    PortfolioAnalyzeRequest,
    PortfolioAnalyzeResponse,
    ProfileType,
    RiskLevel,
)
from app.services.feature3_benchmark_data import Feature3BenchmarkPoint, Feature3BenchmarkSeriesResult, fetch_feature3_benchmark_series
from app.services.feature3_price_data import Feature3PricePoint, Feature3PriceSeriesResult, fetch_feature3_price_series

KST = timezone(timedelta(hours=9))
MIN_OBSERVATIONS = 120
CAPM_PARTIAL_MIN_SAMPLE = 60
MAX_MISSING_RATE = 0.20
MAX_COMMON_MISSING_RATE = 0.20
MAX_ASSET_WEIGHT = 0.40
FRONTIER_POINT_COUNT = 55
EQUITY_RISK_PREMIUM = 0.04
WINSORIZE_LOWER_QUANTILE = 0.01
WINSORIZE_UPPER_QUANTILE = 0.99
DISPLAY_EXPECTED_RETURN_CAP_LOWER = -0.30
DISPLAY_EXPECTED_RETURN_CAP_UPPER = 0.60
HISTORICAL_CONFIDENCE_MIN = 0.20
HISTORICAL_CONFIDENCE_MAX = 0.65
HISTORICAL_CONFIDENCE_SAMPLE_DAYS = 504
VOLATILITY_CONFIDENCE_REFERENCE = 0.30
VOLATILITY_PENALTY_MIN = 0.40
MISSING_PENALTY_MIN = 0.50
DEFAULT_CAPM_BENCHMARK_CODE = "00001"
KOSDAQ_CAPM_BENCHMARK_CODE = "11001"
KOSPI_EXCHANGE_CODES = {"KOSPI", "XKRX", "KRX", "STK", "KS"}
KOSDAQ_EXCHANGE_CODES = {"KOSDAQ", "XKOS", "KQ", "KOSDAQ_GLOBAL"}


def analyze_portfolio(req: PortfolioAnalyzeRequest) -> PortfolioAnalyzeResponse:
    score = req.risk_profile.risk_tolerance_score
    gamma = round(req.risk_profile.risk_aversion_gamma or (10 - 9 * score), 4)
    profile_type = req.risk_profile.profile_type or _profile_type(score)
    target_volatility = round(req.risk_profile.target_volatility or (0.08 + 0.12 * score), 4)
    risk_free_rate = _risk_free_rate(req)
    cash_limit = _resolved_max_cash_weight(req, gamma)
    low_target_volatility = round(max(target_volatility * 0.70, 0.06), 4)
    high_target_volatility = round(min(target_volatility * 1.35, 0.25), 4)

    provided_price_results = _provided_price_results(req)
    price_results = [
        provided_price_results.get(holding.stock_code)
        or fetch_feature3_price_series(
            stock_code=holding.stock_code,
            company_name=holding.company_name,
            requested_price_basis=req.options.price_basis,
            lookback_trading_days=req.options.lookback_trading_days,
            fetch_calendar_days=req.options.fetch_calendar_days,
        )
        for holding in req.holdings
    ]
    price_warnings = [
        warning
        for result in price_results
        for warning in result.warnings
    ]
    benchmark_selections, benchmark_selection_warnings = _benchmark_selections_for_holdings(req.holdings)
    benchmark_codes = {selection["benchmarkCode"] for selection in benchmark_selections.values()}
    benchmark_results = _provided_benchmark_results(req)
    missing_benchmark_codes = benchmark_codes - set(benchmark_results.keys())
    if missing_benchmark_codes:
        benchmark_results.update(_fetch_capm_benchmarks(
            benchmark_codes=missing_benchmark_codes,
            lookback_trading_days=req.options.lookback_trading_days,
            fetch_calendar_days=req.options.fetch_calendar_days,
        ))
    benchmark_warnings = [
        warning
        for result in benchmark_results.values()
        for warning in result.warnings
    ] + benchmark_selection_warnings

    # 현재 구현: 가격 시계열이 충분하면 실제 Ledoit-Wolf 기반 CURRENT 리스크를 계산한다.
    # 진행 예정: Phase 9에서 Feature1/2 overlay를 이 core 계산과 분리된 설명 레이어로 붙인다.
    risk_context = _build_risk_context(req, price_results, risk_free_rate, benchmark_results, benchmark_selections)
    current = _compute_current_portfolio(req, price_results, target_volatility, risk_context, risk_free_rate)
    weights = current.weights
    current_volatility = current.volatility
    risk_level = _risk_level(current_volatility, target_volatility)
    suitability = (
        "AGGRESSIVE_THAN_PROFILE"
        if current_volatility > target_volatility * 1.10
        else "CONSERVATIVE_THAN_PROFILE"
        if current_volatility < target_volatility * 0.90
        else "ALIGNED"
    )

    stable_target, balanced_target, aggressive_target = _basic_profile_target_volatilities(
        risk_context,
        low_target_volatility,
        target_volatility,
        high_target_volatility,
        cash_limit,
    )
    stable = _target_volatility_candidate(req, risk_context, "STABLE", "안정형", stable_target, weights, risk_free_rate, cash_limit)
    balanced = _target_volatility_candidate(req, risk_context, "BALANCED", "균형형", balanced_target, weights, risk_free_rate, cash_limit)
    aggressive = _target_volatility_candidate(req, risk_context, "AGGRESSIVE", "공격형", aggressive_target, weights, risk_free_rate, cash_limit)
    psychological = _target_volatility_candidate(req, risk_context, "PSYCHOLOGICAL", "심리형", target_volatility, weights, risk_free_rate, 1.0)
    basic_portfolios = [current, stable, balanced, aggressive, psychological]
    advanced_candidates, frontier, expected_return_policy, advanced_warnings = _advanced_mean_variance_outputs(
        req,
        risk_context,
        target_volatility,
        risk_free_rate,
        cash_limit,
    )
    overlay_adjusted_portfolios, overlay_visualizations, overlay_explanations = _overlay_adjusted_outputs(
        req,
        risk_context,
        target_volatility,
        risk_free_rate,
    )

    candidate_warnings = [
        _target_volatility_warning(portfolio)
        for portfolio in [stable, balanced, aggressive, psychological]
        if portfolio.optimization_status == "NEAREST_FEASIBLE"
    ]
    covariance_warnings = _risk_context_warnings(risk_context) + _covariance_warnings(req, risk_context)
    excluded_holdings = _excluded_holdings(price_results)
    warnings = [
        warning
        for warning in _dedupe_warnings(
            price_warnings
            + _core_warning_if_dummy(current)
            + covariance_warnings
            + (risk_context.get("capm_warnings") or [])
            + benchmark_warnings
            + _excluded_holding_warnings(excluded_holdings)
            + candidate_warnings
            + advanced_warnings
        )
        if warning is not None
    ]
    risk_drivers = _build_risk_drivers(current, suitability)

    now = datetime.now(timezone.utc).isoformat()
    price_policy_used = _portfolio_price_basis(req.options.price_basis, risk_context["eligible_results"], price_results)
    price_policy_warnings = _warning_codes(price_warnings)
    if price_policy_used == "YAHOO_ADJ_CLOSE_WITH_KIS_FALLBACK" and "MIXED_PRICE_BASIS_USED" not in price_policy_warnings:
        price_policy_warnings.append("MIXED_PRICE_BASIS_USED")

    policy = Feature3PolicyEcho(
        price_policy=Feature3PricePolicy(
            requested=req.options.price_basis,
            used=price_policy_used,
            warnings=price_policy_warnings,
        ),
        risk_profile=Feature3RiskProfileEcho(
            risk_tolerance_score=round(score, 4),
            risk_aversion_gamma=gamma,
            profile_type=profile_type,
            target_volatility=target_volatility,
        ),
        data_policy=Feature3DataPolicy(
            return_type=req.options.return_type,
            lookback_trading_days=req.options.lookback_trading_days,
            fetch_calendar_days=req.options.fetch_calendar_days,
            annualization_factor=req.options.annualization_factor,
            min_observations=MIN_OBSERVATIONS,
            max_missing_rate=MAX_MISSING_RATE,
            max_common_missing_rate=MAX_COMMON_MISSING_RATE,
        ),
            data_quality=Feature3DataQuality(
            expected_trading_day_count=req.options.lookback_trading_days,
            common_price_count=int(risk_context["common_price_count"]),
            common_return_sample_size=int(risk_context["sample_size"]),
            common_missing_rate=float(risk_context["common_missing_rate"]),
            included_holding_count=len(risk_context["eligible_results"]),
            excluded_holding_count=len(excluded_holdings),
            price_series=[
                Feature3PriceSeriesQuality(
                    stock_code=result.stock_code,
                    company_name=result.company_name,
                    requested_price_basis=result.requested_price_basis,
                    used_price_basis=result.used_price_basis,
                    source=result.source,
                    cache_status=result.cache_status,
                    expected_trading_day_count=result.expected_trading_day_count,
                    available_price_count=result.available_price_count,
                    missing_rate=result.missing_rate,
                    fallback_used=result.fallback_used,
                    warnings=result.warnings,
                )
                for result in price_results
            ],
                excluded_holdings=excluded_holdings,
            ),
            risk_free_policy=Feature3RiskFreePolicy(
                rate=risk_free_rate,
                source=req.options.risk_free_rate_source or "DEFAULT_ZERO",
                as_of=req.options.risk_free_rate_as_of,
                instrument_code="KR3Y" if req.options.risk_free_rate_source and req.options.risk_free_rate_source != "DEFAULT_ZERO" else None,
                instrument_name="국고채 3년" if req.options.risk_free_rate_source and req.options.risk_free_rate_source != "DEFAULT_ZERO" else None,
            ),
        )

    return PortfolioAnalyzeResponse(
        policy=policy,
        summary=Feature3Summary(
            risk_level=risk_level,
            suitability=suitability,
            annualized_volatility=current_volatility,
            target_volatility=target_volatility,
            volatility_gap=round(current_volatility - target_volatility, 4),
            main_risk_drivers=[driver.code for driver in risk_drivers],
        ),
        current_portfolio=current,
        basic_portfolios=basic_portfolios,
        risk_drivers=risk_drivers,
        advanced=Feature3AdvancedResult(
            candidate_portfolios=advanced_candidates,
            covariance_diagnostics=Feature3CovarianceDiagnostics(
                requested_covariance_model=req.options.covariance_model,
                used_covariance_model=risk_context["used_model"] or req.options.covariance_model,
                sample_size=int(risk_context["sample_size"]),
                asset_count=len(risk_context["eligible_results"]),
                shrinkage_lambda=risk_context["shrinkage_lambda"],
                min_eigenvalue=risk_context["min_eigenvalue"],
                condition_number=risk_context["condition_number"],
                positive_definite=risk_context["positive_definite"],
            ),
            correlation_matrix=risk_context["correlation_matrix"],
            frontier=frontier,
            expected_return_policy=expected_return_policy,
            benchmark_policy=risk_context.get("benchmark_policy"),
            capm_policy=risk_context.get("capm_policy"),
            scl=risk_context.get("scl"),
            sml=risk_context.get("sml"),
        ),
        overlays=Feature3OverlayResult(
            overlay_signals=req.overlay_signals,
            adjusted_portfolios=overlay_adjusted_portfolios,
            visualizations=overlay_visualizations,
            explanations=overlay_explanations,
        ),
        warnings=warnings,
        freshness=Feature3Freshness(
            core_risk_as_of=now,
            price_series_as_of=now,
            has_mixed_freshness=False,
            newest_data_at=now,
            oldest_data_at=now,
            user_message="가격 데이터와 보조 분석 데이터의 기준 시점이 다를 수 있어 freshness 정보를 함께 표시합니다.",
            overlays=[],
        ),
    )


def _provided_price_results(req: PortfolioAnalyzeRequest) -> dict[str, Feature3PriceSeriesResult]:
    rows = req.input_data.price_series if req.input_data else []
    return {
        row.stock_code: _provided_price_result(row)
        for row in rows
        if row.stock_code
    }


def _provided_price_result(row: Feature3ProvidedPriceSeries) -> Feature3PriceSeriesResult:
    points = [
        point for point in (_provided_price_point(item) for item in row.data)
        if point is not None
    ]
    return Feature3PriceSeriesResult(
        stock_code=row.stock_code,
        company_name=row.company_name,
        requested_price_basis=row.requested_price_basis,
        used_price_basis=row.used_price_basis,
        source=row.source,
        cache_status=row.cache_status,
        expected_trading_day_count=row.expected_trading_day_count,
        available_price_count=row.available_price_count,
        missing_rate=row.missing_rate,
        fallback_used=row.fallback_used,
        points=points,
        warnings=row.warnings,
    )


def _provided_price_point(row) -> Feature3PricePoint | None:
    try:
        return Feature3PricePoint(
            ts=datetime.fromisoformat(str(row.ts).replace("Z", "+00:00")),
            close=float(row.close),
        )
    except Exception:
        return None


def _provided_benchmark_results(req: PortfolioAnalyzeRequest) -> dict[str, Feature3BenchmarkSeriesResult]:
    rows = req.input_data.benchmark_series if req.input_data else []
    return {
        row.benchmark_code: _provided_benchmark_result(row)
        for row in rows
        if row.benchmark_code
    }


def _provided_benchmark_result(row: Feature3ProvidedBenchmarkSeries) -> Feature3BenchmarkSeriesResult:
    points = [
        point for point in (_provided_benchmark_point(item) for item in row.data)
        if point is not None
    ]
    return Feature3BenchmarkSeriesResult(
        benchmark_code=row.benchmark_code,
        benchmark_name=row.benchmark_name,
        source=row.source,
        benchmark_available=row.benchmark_available,
        expected_trading_day_count=row.expected_trading_day_count,
        available_price_count=row.available_price_count,
        missing_rate=row.missing_rate,
        points=points,
        warnings=row.warnings,
    )


def _provided_benchmark_point(row) -> Feature3BenchmarkPoint | None:
    try:
        return Feature3BenchmarkPoint(
            ts=datetime.fromisoformat(str(row.ts).replace("Z", "+00:00")),
            close=float(row.close),
        )
    except Exception:
        return None


def _profile_type(score: float) -> ProfileType:
    if score < 0.34:
        return "CONSERVATIVE"
    if score < 0.67:
        return "NEUTRAL"
    return "AGGRESSIVE"


def _compute_current_portfolio(
    req: PortfolioAnalyzeRequest,
    price_results: list[Feature3PriceSeriesResult],
    target_volatility: float,
    risk_context: dict,
    risk_free_rate: float,
) -> Feature3PortfolioResult:
    holding_by_code = {holding.stock_code: holding for holding in req.holdings}
    eligible_results = risk_context["eligible_results"]

    if len(eligible_results) == 0:
        weights = _dummy_weights(req)
        return Feature3PortfolioResult(
            type="CURRENT",
            label="현재 구성",
            volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            target_volatility=target_volatility,
            achieved_volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            risk_level=_risk_level(round(min(0.30, max(0.06, target_volatility * 1.18)), 4), target_volatility),
            optimization_status="NEAREST_FEASIBLE",
            weights=weights,
            risk_contributions=_dummy_risk_contributions(weights),
            user_description="가격 시계열이 부족해 입력 평가금액 비중 기준 임시 분석을 표시합니다.",
        )

    if int(risk_context["sample_size"]) < MIN_OBSERVATIONS - 1:
        weights = _dummy_weights(req)
        return Feature3PortfolioResult(
            type="CURRENT",
            label="현재 구성",
            volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            target_volatility=target_volatility,
            achieved_volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            risk_level=_risk_level(round(min(0.30, max(0.06, target_volatility * 1.18)), 4), target_volatility),
            optimization_status="NEAREST_FEASIBLE",
            weights=weights,
            risk_contributions=_dummy_risk_contributions(weights),
            user_description="공통 거래일이 부족해 입력 평가금액 비중 기준 임시 분석을 표시합니다.",
        )

    covariance_annual = risk_context["covariance_annual"]

    risky_values = []
    holding_valuation_by_code = {}
    for result in eligible_results:
        holding = holding_by_code[result.stock_code]
        current_price = _current_valuation_price(holding, result)
        market_value = max(0.0, holding.quantity * current_price)
        cost_basis_value = max(0.0, holding.quantity * holding.avg_price)
        risky_values.append(market_value)
        holding_valuation_by_code[result.stock_code] = {
            "quantity": holding.quantity,
            "avg_price": holding.avg_price,
            "current_price": current_price,
            "cost_basis_value": cost_basis_value,
            "market_value": market_value,
            "unrealized_pnl": market_value - cost_basis_value,
            "unrealized_return_rate": (market_value - cost_basis_value) / cost_basis_value if cost_basis_value > 0 else None,
        }

    cash_values = [cash.amount for cash in req.cash_positions]
    total_value = sum(risky_values) + sum(cash_values)
    if total_value <= 0:
        weights = _dummy_weights(req)
        return Feature3PortfolioResult(
            type="CURRENT",
            label="현재 구성",
            volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            target_volatility=target_volatility,
            achieved_volatility=round(min(0.30, max(0.06, target_volatility * 1.18)), 4),
            risk_level=_risk_level(round(min(0.30, max(0.06, target_volatility * 1.18)), 4), target_volatility),
            optimization_status="NEAREST_FEASIBLE",
            weights=weights,
            risk_contributions=_dummy_risk_contributions(weights),
            user_description="평가금액을 계산할 수 없어 입력 평가금액 비중 기준 임시 분석을 표시합니다.",
        )

    risky_weights = np.array([value / total_value for value in risky_values], dtype=float)
    variance = float(risky_weights.T @ covariance_annual @ risky_weights)
    volatility = round(float(np.sqrt(max(variance, 0.0))), 6)
    risk_level = _risk_level(volatility, target_volatility)
    weights = [
        Feature3Weight(
            stock_code=result.stock_code,
            company_name=result.company_name,
            asset_type="EQUITY",
            weight=round(float(weight), 6),
            quantity=round(float(holding_valuation_by_code[result.stock_code]["quantity"]), 6),
            avg_price=round(float(holding_valuation_by_code[result.stock_code]["avg_price"]), 6),
            current_price=round(float(holding_valuation_by_code[result.stock_code]["current_price"]), 6),
            cost_basis_value=round(float(holding_valuation_by_code[result.stock_code]["cost_basis_value"]), 6),
            market_value=round(float(holding_valuation_by_code[result.stock_code]["market_value"]), 6),
            unrealized_pnl=round(float(holding_valuation_by_code[result.stock_code]["unrealized_pnl"]), 6),
            unrealized_return_rate=round(float(holding_valuation_by_code[result.stock_code]["unrealized_return_rate"]), 6)
            if holding_valuation_by_code[result.stock_code]["unrealized_return_rate"] is not None else None,
        )
        for result, weight in zip(eligible_results, risky_weights)
    ]
    weights.extend(
        Feature3Weight(
            stock_code=f"CASH_{cash.currency}",
            company_name=f"{cash.currency} 현금",
            asset_type="CASH",
            weight=round(float(cash.amount / total_value), 6),
        )
        for cash in req.cash_positions
        if cash.amount > 0
    )

    risk_contributions = _risk_contributions_from_covariance(
        eligible_results,
        risky_weights,
        covariance_annual,
        volatility,
    )
    risk_contributions.extend(
        Feature3RiskContribution(
            stock_code=f"CASH_{cash.currency}",
            company_name=f"{cash.currency} 현금",
            weight=round(float(cash.amount / total_value), 6),
            volatility=0.0,
            marginal_risk_contribution=0.0,
            risk_contribution=0.0,
            risk_contribution_pct=0.0,
        )
        for cash in req.cash_positions
        if cash.amount > 0
    )
    expected_return = _expected_return_for_weights(risk_context, risky_weights, _cash_weight(req, total_value), risk_free_rate)
    raw_historical_return = _expected_return_for_weights(
        risk_context,
        risky_weights,
        _cash_weight(req, total_value),
        risk_free_rate,
        mu_key="raw_historical_annual_mu",
    )
    display_expected_return = _display_expected_return(expected_return)

    return Feature3PortfolioResult(
        type="CURRENT",
        label="현재 구성",
        expected_return=expected_return,
        raw_historical_return=raw_historical_return,
        display_expected_return=display_expected_return,
        is_display_capped=_is_display_capped(expected_return, display_expected_return),
        sharpe_ratio=_sharpe_ratio(expected_return, volatility, risk_free_rate),
        volatility=volatility,
        target_volatility=target_volatility,
        achieved_volatility=volatility,
        risk_level=risk_level,
        optimization_status="SUCCESS",
        weights=weights,
        risk_contributions=risk_contributions,
        user_description="가격 시계열 기반 현재 포트폴리오 분석입니다.",
    )


def _benchmark_selections_for_holdings(holdings) -> tuple[dict[str, dict], list[Feature3Warning]]:
    selections: dict[str, dict] = {}
    warnings: list[Feature3Warning] = []
    selected_codes: set[str] = set()
    for holding in holdings:
        stock_code = holding.stock_code
        benchmark_code, reason, warning_code = _benchmark_code_for_exchange(holding.exchange_code)
        selected_codes.add(benchmark_code)
        selections[stock_code] = {
            "stockCode": stock_code,
            "exchangeCode": holding.exchange_code,
            "benchmarkCode": benchmark_code,
            "selectionReason": reason,
        }
        if warning_code:
            warnings.append(Feature3Warning(
                code=warning_code,
                message="Holding exchange was unavailable; KOSPI benchmark proxy was selected for CAPM.",
                user_message="상장시장 정보를 확인하지 못해 KOSPI 벤치마크를 대체 기준으로 사용합니다.",
                severity="WARN",
                target=stock_code,
            ))
    if len(selected_codes) > 1:
        warnings.append(Feature3Warning(
            code="MIXED_MARKET_BENCHMARKS_USED",
            message="Portfolio holdings use more than one listing-market benchmark for CAPM.",
            user_message="KOSPI/KOSDAQ 종목이 함께 있어 상장시장별 벤치마크로 CAPM을 계산합니다.",
            severity="INFO",
            target="advanced.benchmarkPolicy",
        ))
    return selections, warnings


def _benchmark_code_for_exchange(exchange_code: str | None) -> tuple[str, str, str | None]:
    normalized = _normalize_exchange_code(exchange_code)
    if normalized in KOSDAQ_EXCHANGE_CODES:
        return KOSDAQ_CAPM_BENCHMARK_CODE, "KOSDAQ_LISTING_MARKET", None
    if normalized in KOSPI_EXCHANGE_CODES:
        return DEFAULT_CAPM_BENCHMARK_CODE, "KOSPI_LISTING_MARKET", None
    return DEFAULT_CAPM_BENCHMARK_CODE, "UNKNOWN_MARKET_KOSPI_PROXY", "UNKNOWN_MARKET_KOSPI_PROXY"


def _normalize_exchange_code(exchange_code: str | None) -> str:
    if not exchange_code:
        return ""
    return re.sub(r"[^A-Z0-9]", "", exchange_code.upper())


def _fetch_capm_benchmarks(
    benchmark_codes: set[str],
    lookback_trading_days: int,
    fetch_calendar_days: int,
) -> dict[str, Feature3BenchmarkSeriesResult]:
    results: dict[str, Feature3BenchmarkSeriesResult] = {}
    for benchmark_code in sorted(benchmark_codes or {DEFAULT_CAPM_BENCHMARK_CODE}):
        results[benchmark_code] = fetch_feature3_benchmark_series(
            benchmark_code=benchmark_code,
            lookback_trading_days=lookback_trading_days,
            fetch_calendar_days=fetch_calendar_days,
        )
    return results


def _build_risk_context(
    req: PortfolioAnalyzeRequest,
    price_results: list[Feature3PriceSeriesResult],
    risk_free_rate: float,
    benchmark_results: dict[str, Feature3BenchmarkSeriesResult] | None = None,
    benchmark_selections: dict[str, dict] | None = None,
) -> dict:
    eligible_results = [
        result
        for result in price_results
        if result.available_price_count >= MIN_OBSERVATIONS
        and result.missing_rate <= MAX_MISSING_RATE
        and len(result.points) >= MIN_OBSERVATIONS
        and all(point.close > 0 and np.isfinite(point.close) for point in result.points)
    ]
    empty = {
        "eligible_results": eligible_results,
        "sample_size": 0,
        "common_price_count": 0,
        "common_missing_rate": 1.0,
        "fallback_reason": None,
        "covariance_annual": None,
        "used_model": None,
        "shrinkage_lambda": None,
        "min_eigenvalue": None,
        "condition_number": None,
        "positive_definite": None,
        "correlation_matrix": None,
        "annual_mu": None,
        "raw_historical_annual_mu": None,
        "display_annual_mu": None,
        "is_display_capped_by_asset": None,
        "expected_return_estimation": None,
        "benchmark_policy": _benchmark_policy_multi(benchmark_results or {}, benchmark_selections or {}, req.options.lookback_trading_days),
        "capm_policy": _empty_capm_policy("UNAVAILABLE"),
        "scl": None,
        "sml": None,
        "capm_warnings": [],
    }
    if not eligible_results:
        return {**empty, "fallback_reason": "NO_ELIGIBLE_HOLDINGS"}

    common_dates = _common_dates(eligible_results)
    common_missing_rate = _common_missing_rate(req.options.lookback_trading_days, len(common_dates))
    common_empty = {
        **empty,
        "common_price_count": len(common_dates),
        "common_missing_rate": common_missing_rate,
    }
    if common_missing_rate > MAX_COMMON_MISSING_RATE:
        return {**common_empty, "fallback_reason": "HIGH_COMMON_MISSING_RATE"}
    if len(common_dates) < MIN_OBSERVATIONS:
        return {**common_empty, "fallback_reason": "INSUFFICIENT_COMMON_OBSERVATIONS"}

    returns_matrix = _log_return_matrix(eligible_results, common_dates)
    if returns_matrix.shape[0] < MIN_OBSERVATIONS - 1:
        return {**common_empty, "fallback_reason": "INSUFFICIENT_COMMON_RETURN_SAMPLE"}

    if not np.isfinite(returns_matrix).all():
        return {**common_empty, "fallback_reason": "INVALID_COMMON_RETURN_SAMPLE"}

    covariance_daily, used_model, shrinkage_lambda = _covariance_matrix(returns_matrix, req.options.covariance_model)
    covariance_annual = covariance_daily * req.options.annualization_factor
    expected_return_estimation = _estimate_expected_returns(
        returns_matrix=returns_matrix,
        eligible_results=eligible_results,
        covariance_annual=covariance_annual,
        annualization_factor=req.options.annualization_factor,
        risk_free_rate=risk_free_rate,
        benchmark_results=benchmark_results or {},
        benchmark_selections=benchmark_selections or {},
    )
    annual_mu = expected_return_estimation["annual_mu"]
    raw_historical_annual_mu = expected_return_estimation["raw_historical_annual_mu"]
    display_annual_mu = expected_return_estimation["display_annual_mu"]
    is_display_capped_by_asset = expected_return_estimation["is_display_capped_by_asset"]
    eigenvalues = np.linalg.eigvalsh(covariance_annual)
    min_eigenvalue = float(np.min(eigenvalues)) if eigenvalues.size else None
    condition_number = float(np.linalg.cond(covariance_annual)) if covariance_annual.size else None

    return {
        "eligible_results": eligible_results,
        "sample_size": int(returns_matrix.shape[0]),
        "common_price_count": len(common_dates),
        "common_missing_rate": common_missing_rate,
        "fallback_reason": None,
        "covariance_annual": covariance_annual,
        "used_model": used_model,
        "shrinkage_lambda": shrinkage_lambda,
        "min_eigenvalue": round(min_eigenvalue, 10) if min_eigenvalue is not None else None,
        "condition_number": round(condition_number, 6) if condition_number is not None and np.isfinite(condition_number) else None,
        "positive_definite": bool(min_eigenvalue is not None and min_eigenvalue > 0),
        "correlation_matrix": _correlation_matrix_payload(eligible_results, covariance_annual),
        "annual_mu": annual_mu,
        "raw_historical_annual_mu": raw_historical_annual_mu,
        "historical_annual_mu": expected_return_estimation["historical_annual_mu"],
        "capm_annual_mu": expected_return_estimation["capm_annual_mu"],
        "display_annual_mu": display_annual_mu,
        "is_display_capped_by_asset": is_display_capped_by_asset,
        "expected_return_estimation": expected_return_estimation["policy"],
        "benchmark_policy": expected_return_estimation["benchmark_policy"],
        "capm_policy": expected_return_estimation["capm_policy"],
        "scl": expected_return_estimation["scl"],
        "sml": expected_return_estimation["sml"],
        "capm_warnings": expected_return_estimation["capm_warnings"],
    }


def _common_dates(price_results: list[Feature3PriceSeriesResult]) -> list:
    common = None
    for result in price_results:
        dates = set(_close_by_trading_date(result.points))
        common = dates if common is None else common & dates
    return sorted(common or [])


def _common_missing_rate(expected_trading_day_count: int, common_price_count: int) -> float:
    expected = max(1, int(expected_trading_day_count))
    return round(max(0.0, 1.0 - (common_price_count / expected)), 6)


def _log_return_matrix(price_results: list[Feature3PriceSeriesResult], common_dates: list) -> np.ndarray:
    close_by_result = [
        _close_by_trading_date(result.points)
        for result in price_results
    ]
    closes = np.array(
        [
            [close_map[date] for close_map in close_by_result]
            for date in common_dates
        ],
        dtype=float,
    )
    return np.diff(np.log(closes), axis=0)


def _trading_date(ts) -> object:
    if ts is None:
        return None
    try:
        if ts.tzinfo is None:
            return ts.date()
        return ts.astimezone(KST).date()
    except Exception:
        return None


def _close_by_trading_date(points) -> dict:
    by_date = {}
    for point in sorted(
        [point for point in points if point.close > 0 and np.isfinite(point.close)],
        key=lambda item: item.ts,
    ):
        trading_date = _trading_date(point.ts)
        if trading_date is None:
            continue
        by_date[trading_date] = point.close
    return by_date


def _estimate_expected_returns(
    returns_matrix: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    covariance_annual: np.ndarray,
    annualization_factor: int,
    risk_free_rate: float,
    benchmark_results: dict[str, Feature3BenchmarkSeriesResult] | None = None,
    benchmark_selections: dict[str, dict] | None = None,
) -> dict:
    raw_historical_annual_mu = np.mean(returns_matrix, axis=0) * annualization_factor
    winsorized = np.apply_along_axis(
        lambda column: np.clip(
            column,
            np.quantile(column, WINSORIZE_LOWER_QUANTILE),
            np.quantile(column, WINSORIZE_UPPER_QUANTILE),
        ),
        axis=0,
        arr=returns_matrix,
    )
    winsorized_annual_mu = np.mean(winsorized, axis=0) * annualization_factor
    optimizer_annual_volatility = np.sqrt(np.maximum(np.diag(covariance_annual), 0.0))
    realized_annual_volatility = np.std(returns_matrix, axis=0, ddof=1) * np.sqrt(annualization_factor)
    sample_size = int(returns_matrix.shape[0])
    base_confidence = _clamp_float(
        sample_size / HISTORICAL_CONFIDENCE_SAMPLE_DAYS,
        HISTORICAL_CONFIDENCE_MIN,
        HISTORICAL_CONFIDENCE_MAX,
    )
    vol_penalty = np.array([
        _clamp_float(VOLATILITY_CONFIDENCE_REFERENCE / vol, VOLATILITY_PENALTY_MIN, 1.0)
        if vol > 1e-9 and np.isfinite(vol) else VOLATILITY_PENALTY_MIN
        for vol in optimizer_annual_volatility
    ], dtype=float)
    missing_penalty = np.array([
        _clamp_float(1.0 - result.missing_rate, MISSING_PENALTY_MIN, 1.0)
        for result in eligible_results
    ], dtype=float)
    historical_confidence = np.clip(base_confidence * vol_penalty * missing_penalty, 0.0, HISTORICAL_CONFIDENCE_MAX)
    prior_return = risk_free_rate + EQUITY_RISK_PREMIUM
    historical_annual_mu = historical_confidence * winsorized_annual_mu + (1.0 - historical_confidence) * prior_return
    capm_estimation = _estimate_capm_blended_returns(
        eligible_results=eligible_results,
        benchmark_results=benchmark_results or {},
        benchmark_selections=benchmark_selections or {},
        historical_annual_mu=historical_annual_mu,
        historical_confidence=historical_confidence,
        realized_annual_volatility=realized_annual_volatility,
        optimizer_annual_volatility=optimizer_annual_volatility,
        annualization_factor=annualization_factor,
        risk_free_rate=risk_free_rate,
    )
    annual_mu = capm_estimation["blended_annual_mu"]
    display_annual_mu = np.clip(annual_mu, DISPLAY_EXPECTED_RETURN_CAP_LOWER, DISPLAY_EXPECTED_RETURN_CAP_UPPER)
    is_display_capped = np.abs(display_annual_mu - annual_mu) > 1e-12

    return {
        "annual_mu": annual_mu,
        "raw_historical_annual_mu": raw_historical_annual_mu,
        "historical_annual_mu": historical_annual_mu,
        "capm_annual_mu": capm_estimation["capm_annual_mu"],
        "display_annual_mu": display_annual_mu,
        "is_display_capped_by_asset": is_display_capped,
        "policy": {
            "estimator": "BLENDED_HISTORICAL_CAPM",
            "returnType": "LOG_RETURN",
            "annualizationFactor": annualization_factor,
            "winsorizeLowerQuantile": WINSORIZE_LOWER_QUANTILE,
            "winsorizeUpperQuantile": WINSORIZE_UPPER_QUANTILE,
            "priorType": "RISK_FREE_PLUS_EQUITY_PREMIUM",
            "riskFreeRate": risk_free_rate,
            "equityRiskPremium": EQUITY_RISK_PREMIUM,
            "priorReturn": round(float(prior_return), 6),
            "baseHistoricalConfidence": round(float(base_confidence), 6),
            "historicalConfidenceByAsset": np.round(historical_confidence, 6).tolist(),
            "averageHistoricalConfidence": round(float(np.mean(historical_confidence)), 6),
            "averageVolPenalty": round(float(np.mean(vol_penalty)), 6),
            "averageMissingPenalty": round(float(np.mean(missing_penalty)), 6),
            "capmFormula": "capmExpectedReturnAnnual = riskFreeRateAnnual + betaDaily * equityRiskPremiumAnnual",
            "blendFormula": "blendedExpectedReturn = historicalWeight * historicalExpectedReturn + capmWeight * capmExpectedReturn",
            "capmWeightFormula": capm_estimation["weight_formula"],
            "capmSamplePolicy": {
                "normalMinCommonSampleSize": MIN_OBSERVATIONS,
                "partialMinCommonSampleSize": CAPM_PARTIAL_MIN_SAMPLE,
                "belowPartialMin": "CAPM_EXCLUDED",
            },
            "benchmarkPolicy": capm_estimation["benchmark_policy"],
            "assets": capm_estimation["assets"],
            "averageCapmWeight": capm_estimation["average_capm_weight"],
            "averageBlendConfidence": capm_estimation["average_confidence"],
            "displayCapLower": DISPLAY_EXPECTED_RETURN_CAP_LOWER,
            "displayCapUpper": DISPLAY_EXPECTED_RETURN_CAP_UPPER,
            "hasDisplayCappedAssets": bool(np.any(is_display_capped)),
            "warning": "EXPECTED_RETURN_ESTIMATION_UNSTABLE",
        },
        "benchmark_policy": capm_estimation["benchmark_policy"],
        "capm_policy": capm_estimation["capm_policy"],
        "scl": capm_estimation["scl"],
        "sml": capm_estimation["sml"],
        "capm_warnings": capm_estimation["warnings"],
    }


def _estimate_capm_blended_returns(
    eligible_results: list[Feature3PriceSeriesResult],
    benchmark_results: dict[str, Feature3BenchmarkSeriesResult],
    benchmark_selections: dict[str, dict],
    historical_annual_mu: np.ndarray,
    historical_confidence: np.ndarray,
    realized_annual_volatility: np.ndarray,
    optimizer_annual_volatility: np.ndarray,
    annualization_factor: int,
    risk_free_rate: float,
) -> dict:
    asset_count = len(eligible_results)
    capm_annual_mu = np.full(asset_count, np.nan, dtype=float)
    blended_annual_mu = np.array(historical_annual_mu, dtype=float)
    capm_weights = np.zeros(asset_count, dtype=float)
    warnings: list[Feature3Warning] = []
    asset_rows: list[dict] = []
    scl_series: list[dict] = []
    benchmark_policy = _benchmark_policy_multi(benchmark_results, benchmark_selections, 0)
    weight_formula = (
        "capmWeight = sampleFactor * benchmarkSourceFactor * benchmarkCoverageFactor "
        "* rSquaredFactor * correlationFactor * volatilityReliabilityFactor; "
        "historicalWeight = 1 - capmWeight"
    )
    market_returns_by_benchmark = {
        code: _dated_log_returns(result.points)
        for code, result in benchmark_results.items()
    }

    for idx, result in enumerate(eligible_results):
        asset_warnings: list[str] = []
        selection = _benchmark_selection_for_stock(result.stock_code, benchmark_selections)
        benchmark_code = selection["benchmarkCode"]
        benchmark_selection_reason = selection["selectionReason"]
        benchmark_result = benchmark_results.get(benchmark_code)
        benchmark_name = benchmark_result.benchmark_name if benchmark_result else None
        benchmark_source = benchmark_result.source if benchmark_result else "UNAVAILABLE"
        if benchmark_selection_reason == "UNKNOWN_MARKET_KOSPI_PROXY":
            asset_warnings.append("UNKNOWN_MARKET_KOSPI_PROXY")
        if benchmark_result is not None and any(warning.code == "BENCHMARK_FETCH_FAILED" for warning in benchmark_result.warnings):
            asset_warnings.append("BENCHMARK_FETCH_FAILED_FOR_MARKET")
        asset_returns = _dated_log_returns(result.points)
        market_returns = market_returns_by_benchmark.get(benchmark_code, {})
        common_dates = sorted(set(asset_returns) & set(market_returns))
        common_sample_size = len(common_dates)
        capm_expected_return: float | None = None
        beta: float | None = None
        daily_alpha: float | None = None
        annual_alpha: float | None = None
        correlation: float | None = None
        r_squared: float | None = None
        volatility_factor: float | None = None
        confidence = 0.0
        status = "EXCLUDED"

        if not _benchmark_candidate_for_capm(benchmark_result):
            asset_warnings.append("CAPM_DISABLED_BENCHMARK_UNAVAILABLE")
        elif common_sample_size < CAPM_PARTIAL_MIN_SAMPLE:
            asset_warnings.append("CAPM_COMMON_SAMPLE_INSUFFICIENT")
        else:
            asset_vector = np.array([asset_returns[date] for date in common_dates], dtype=float)
            market_vector = np.array([market_returns[date] for date in common_dates], dtype=float)
            market_variance = float(np.var(market_vector, ddof=1)) if common_sample_size > 1 else 0.0
            if market_variance <= 1e-12 or not np.isfinite(market_variance):
                asset_warnings.append("CAPM_BETA_UNAVAILABLE")
            else:
                covariance = float(np.cov(asset_vector, market_vector, ddof=1)[0, 1])
                beta = covariance / market_variance
                daily_alpha = float(np.mean(asset_vector) - beta * np.mean(market_vector))
                annual_alpha = daily_alpha * annualization_factor
                asset_std = float(np.std(asset_vector, ddof=1))
                market_std = float(np.std(market_vector, ddof=1))
                if asset_std > 1e-12 and market_std > 1e-12:
                    correlation = float(np.corrcoef(asset_vector, market_vector)[0, 1])
                    if np.isfinite(correlation):
                        r_squared = correlation * correlation
                if r_squared is None:
                    asset_warnings.append("CAPM_CORRELATION_UNAVAILABLE")
                elif r_squared < 0.10:
                    asset_warnings.append("CAPM_LOW_R_SQUARED")

                capm_expected_return = risk_free_rate + beta * EQUITY_RISK_PREMIUM
                source_factor = _benchmark_source_factor(benchmark_result.source)
                sample_factor = _capm_sample_factor(common_sample_size)
                coverage_factor = _clamp_float(1.0 - benchmark_result.missing_rate, 0.50, 1.0)
                r_squared_factor = _clamp_float((r_squared or 0.0) / 0.25, 0.20, 1.0)
                correlation_factor = _clamp_float(abs(correlation or 0.0) / 0.60, 0.20, 1.0)
                volatility_factor = (
                    _clamp_float(VOLATILITY_CONFIDENCE_REFERENCE / realized_annual_volatility[idx], VOLATILITY_PENALTY_MIN, 1.0)
                    if realized_annual_volatility[idx] > 1e-9 and np.isfinite(realized_annual_volatility[idx])
                    else VOLATILITY_PENALTY_MIN
                )
                if volatility_factor < 0.75:
                    asset_warnings.append("CAPM_HIGH_VOLATILITY")
                confidence = _clamp_float(
                    sample_factor * source_factor * coverage_factor * r_squared_factor * correlation_factor * volatility_factor,
                    0.0,
                    1.0,
                )
                capm_weights[idx] = confidence
                historical_weight = 1.0 - capm_weights[idx]
                blended_annual_mu[idx] = historical_weight * historical_annual_mu[idx] + capm_weights[idx] * capm_expected_return
                capm_annual_mu[idx] = capm_expected_return
                status = "APPLIED" if common_sample_size >= MIN_OBSERVATIONS else "PARTIAL"
                if status == "PARTIAL":
                    asset_warnings.append("CAPM_PARTIAL_LOW_COMMON_SAMPLE")
                if confidence < 0.30:
                    asset_warnings.append("BLENDED_RETURN_CONFIDENCE_LOW")

                scl_series.append({
                        "stockCode": result.stock_code,
                        "companyName": result.company_name,
                        "benchmarkCode": benchmark_code,
                        "benchmarkName": benchmark_name,
                        "benchmarkSource": benchmark_source,
                        "benchmarkSelectionReason": benchmark_selection_reason,
                    "line": {
                        "dailyAlpha": _round_optional(daily_alpha),
                        "annualAlpha": _round_optional(annual_alpha),
                        "beta": _round_optional(beta),
                    },
                    "points": [
                        {
                            "date": date.isoformat(),
                            "marketReturn": round(float(market_returns[date]), 6),
                            "assetReturn": round(float(asset_returns[date]), 6),
                        }
                        for date in common_dates
                    ],
                })

        historical_weight = 1.0 - capm_weights[idx]
        asset_rows.append(_capm_asset_row(
            result=result,
            benchmark_code=benchmark_code,
            benchmark_name=benchmark_name,
            benchmark_source=benchmark_source,
            benchmark_selection_reason=benchmark_selection_reason,
            benchmark_mode=benchmark_policy.get("mode"),
            historical_expected_return=historical_annual_mu[idx],
            capm_expected_return=capm_expected_return,
            blended_expected_return=blended_annual_mu[idx],
            historical_weight=historical_weight,
            capm_weight=capm_weights[idx],
            confidence=confidence,
            status=status,
            warnings=asset_warnings,
            beta=beta,
            daily_alpha=daily_alpha,
            annual_alpha=annual_alpha,
            correlation=correlation,
            r_squared=r_squared,
            common_sample_size=common_sample_size,
            historical_confidence=historical_confidence[idx],
            realized_annual_volatility=realized_annual_volatility[idx],
            optimizer_annual_volatility=optimizer_annual_volatility[idx],
            volatility_reliability_factor=volatility_factor,
        ))

    if benchmark_policy.get("mode") == "MULTI_BENCHMARK":
        warnings.append(Feature3Warning(
            code="MIXED_MARKET_BENCHMARKS_USED",
            message="CAPM used listing-market benchmarks by asset.",
            user_message="상장시장별 벤치마크를 분리해 CAPM을 계산했습니다.",
            severity="INFO",
            target="advanced.benchmarkPolicy",
        ))
    warnings.extend(_capm_asset_warnings(asset_rows))
    return _capm_payload(
        benchmark_policy=benchmark_policy,
        capm_annual_mu=np.nan_to_num(capm_annual_mu, nan=0.0),
        blended_annual_mu=blended_annual_mu,
        capm_weights=capm_weights,
        asset_rows=asset_rows,
        scl_series=scl_series,
        risk_free_rate=risk_free_rate,
        weight_formula=weight_formula,
        warnings=warnings,
    )


def _capm_payload(
    benchmark_policy: dict,
    capm_annual_mu: np.ndarray,
    blended_annual_mu: np.ndarray,
    capm_weights: np.ndarray,
    asset_rows: list[dict],
    scl_series: list[dict],
    risk_free_rate: float,
    weight_formula: str,
    warnings: list[Feature3Warning],
) -> dict:
    applied = [row for row in asset_rows if row["status"] in {"APPLIED", "PARTIAL"}]
    benchmark_items = benchmark_policy.get("benchmarks") if isinstance(benchmark_policy, dict) else []
    groups = []
    for item in benchmark_items if isinstance(benchmark_items, list) else []:
        if not isinstance(item, dict):
            continue
        code = item.get("benchmarkCode")
        group_rows = [row for row in asset_rows if row.get("benchmarkCode") == code]
        group_applied = [row for row in group_rows if row["status"] in {"APPLIED", "PARTIAL"}]
        max_beta = max([abs(float(row.get("beta") or 0.0)) for row in group_applied] + [1.0])
        line_betas = [-max_beta, 0.0, max_beta]
        groups.append({
            "benchmarkCode": code,
            "benchmarkName": item.get("benchmarkName"),
            "source": item.get("source"),
            "availablePriceCount": item.get("availablePriceCount"),
            "missingRate": item.get("missingRate"),
            "capmCandidate": item.get("capmCandidate"),
            "warnings": item.get("warnings") or [],
            "line": _sml_line(line_betas, risk_free_rate),
            "assets": _sml_assets(group_rows),
        })
    if not groups:
        max_beta = max([abs(float(row.get("beta") or 0.0)) for row in applied] + [1.0])
        groups.append({
            "benchmarkCode": benchmark_policy.get("benchmarkCode") if isinstance(benchmark_policy, dict) else DEFAULT_CAPM_BENCHMARK_CODE,
            "benchmarkName": benchmark_policy.get("benchmarkName") if isinstance(benchmark_policy, dict) else None,
            "source": benchmark_policy.get("source") if isinstance(benchmark_policy, dict) else "UNAVAILABLE",
            "line": _sml_line([-max_beta, 0.0, max_beta], risk_free_rate),
            "assets": _sml_assets(asset_rows),
        })
    primary_group = groups[0]
    sml = {
        "mode": benchmark_policy.get("mode", "SINGLE_BENCHMARK") if isinstance(benchmark_policy, dict) else "SINGLE_BENCHMARK",
        "summary": {
            "riskFreeRate": risk_free_rate,
            "equityRiskPremium": EQUITY_RISK_PREMIUM,
            "returnUnit": "ANNUAL",
            "lineFormula": "expectedReturn = riskFreeRate + beta * equityRiskPremium",
            "equityRiskPremiumPolicy": "SHARED_POLICY_VALUE",
        },
        "line": primary_group.get("line") or [],
        "assets": _sml_assets(asset_rows),
        "groups": groups,
    }
    return {
        "capm_annual_mu": capm_annual_mu,
        "blended_annual_mu": blended_annual_mu,
        "average_capm_weight": round(float(np.mean(capm_weights)) if capm_weights.size else 0.0, 6),
        "average_confidence": round(float(np.mean([row["confidence"] for row in asset_rows])) if asset_rows else 0.0, 6),
        "assets": asset_rows,
        "benchmark_policy": benchmark_policy,
        "capm_policy": {
            "status": "AVAILABLE" if applied else "DISABLED",
            "model": "CAPM",
            "returnUnit": "ANNUAL",
            "betaReturnUnit": "DAILY_LOG_RETURN",
            "alphaFields": ["dailyAlpha", "annualAlpha"],
            "riskFreeRate": risk_free_rate,
            "equityRiskPremium": EQUITY_RISK_PREMIUM,
            "weightFormula": weight_formula,
            "weightsSumToOne": True,
            "appliedAssetCount": len(applied),
            "partialAssetCount": sum(1 for row in asset_rows if row["status"] == "PARTIAL"),
            "excludedAssetCount": sum(1 for row in asset_rows if row["status"] == "EXCLUDED"),
            "warnings": _warning_codes(warnings),
        },
        "scl": {
            "summary": {
                "model": "Security Characteristic Line",
                "x": "benchmarkDailyLogReturn",
                "y": "assetDailyLogReturn",
                "alphaUnit": "DAILY_AND_ANNUAL",
                "assetCount": len(asset_rows),
            },
            "assets": [
                {
                    "stockCode": row["stockCode"],
                    "companyName": row.get("companyName"),
                    "benchmarkCode": row.get("benchmarkCode"),
                    "benchmarkName": row.get("benchmarkName"),
                    "benchmarkSource": row.get("benchmarkSource"),
                    "beta": row.get("beta"),
                    "dailyAlpha": row.get("dailyAlpha"),
                    "annualAlpha": row.get("annualAlpha"),
                    "rSquared": row.get("rSquared"),
                    "correlation": row.get("correlation"),
                    "commonSampleSize": row.get("commonSampleSize"),
                    "status": row.get("status"),
                    "warnings": row.get("warnings") or [],
                }
                for row in asset_rows
            ],
            "series": scl_series,
        },
        "sml": sml,
        "warnings": warnings,
        "weight_formula": weight_formula,
    }


def _sml_line(line_betas: list[float], risk_free_rate: float) -> list[dict]:
    return [
        {
            "beta": round(float(beta), 6),
            "expectedReturn": round(float(risk_free_rate + beta * EQUITY_RISK_PREMIUM), 6),
        }
        for beta in line_betas
    ]


def _sml_assets(asset_rows: list[dict]) -> list[dict]:
    return [
        {
            "stockCode": row["stockCode"],
            "companyName": row.get("companyName"),
            "benchmarkCode": row.get("benchmarkCode"),
            "benchmarkName": row.get("benchmarkName"),
            "benchmarkSource": row.get("benchmarkSource"),
            "beta": row.get("beta"),
            "historicalExpectedReturn": row.get("historicalExpectedReturn"),
            "capmExpectedReturn": row.get("capmExpectedReturn"),
            "blendedExpectedReturn": row.get("blendedExpectedReturn"),
            "capmWeight": row.get("capmWeight"),
            "status": row.get("status"),
        }
        for row in asset_rows
    ]


def _capm_asset_row(
    result: Feature3PriceSeriesResult,
    historical_expected_return: float,
    capm_expected_return: float | None,
    blended_expected_return: float,
    historical_weight: float,
    capm_weight: float,
    confidence: float,
    status: str,
    warnings: list[str],
    benchmark_code: str | None = None,
    benchmark_name: str | None = None,
    benchmark_source: str | None = None,
    benchmark_selection_reason: str | None = None,
    benchmark_mode: str | None = None,
    beta: float | None = None,
    daily_alpha: float | None = None,
    annual_alpha: float | None = None,
    correlation: float | None = None,
    r_squared: float | None = None,
    common_sample_size: int = 0,
    historical_confidence: float | None = None,
    realized_annual_volatility: float | None = None,
    optimizer_annual_volatility: float | None = None,
    volatility_reliability_factor: float | None = None,
) -> dict:
    return {
        "stockCode": result.stock_code,
        "companyName": result.company_name,
        "benchmarkCode": benchmark_code,
        "benchmarkName": benchmark_name,
        "benchmarkSource": benchmark_source,
        "benchmarkSelectionReason": benchmark_selection_reason,
        "benchmarkMode": benchmark_mode,
        "historicalExpectedReturn": round(float(historical_expected_return), 6),
        "capmExpectedReturn": _round_optional(capm_expected_return),
        "blendedExpectedReturn": round(float(blended_expected_return), 6),
        "historicalWeight": round(float(historical_weight), 6),
        "capmWeight": round(float(capm_weight), 6),
        "confidence": round(float(confidence), 6),
        "historicalConfidence": _round_optional(historical_confidence),
        "beta": _round_optional(beta),
        "dailyAlpha": _round_optional(daily_alpha),
        "annualAlpha": _round_optional(annual_alpha),
        "correlation": _round_optional(correlation),
        "rSquared": _round_optional(r_squared),
        "commonSampleSize": int(common_sample_size),
        "annualVolatility": _round_optional(realized_annual_volatility),
        "realizedAnnualVolatility": _round_optional(realized_annual_volatility),
        "optimizerAnnualVolatility": _round_optional(optimizer_annual_volatility),
        "volatilityReliabilityFactor": _round_optional(volatility_reliability_factor),
        "status": status,
        "warnings": list(dict.fromkeys(warnings)),
    }


def _benchmark_policy(benchmark_result: Feature3BenchmarkSeriesResult | None, fallback_expected: int) -> dict:
    if benchmark_result is None:
        return {
            "benchmarkCode": DEFAULT_CAPM_BENCHMARK_CODE,
            "benchmarkName": None,
            "source": "UNAVAILABLE",
            "benchmarkAvailable": False,
            "expectedTradingDayCount": fallback_expected,
            "availablePriceCount": 0,
            "missingRate": 1.0,
            "capmCandidate": False,
            "candidateRule": "source in DB,KIS_BACKFILLED or source DB_INSUFFICIENT with availablePriceCount > 0",
            "warnings": ["CAPM_DISABLED_BENCHMARK_UNAVAILABLE"],
        }
    return {
        "benchmarkCode": benchmark_result.benchmark_code,
        "benchmarkName": benchmark_result.benchmark_name,
        "source": benchmark_result.source,
        "benchmarkAvailable": benchmark_result.benchmark_available,
        "expectedTradingDayCount": benchmark_result.expected_trading_day_count,
        "availablePriceCount": benchmark_result.available_price_count,
        "missingRate": round(float(benchmark_result.missing_rate), 6),
        "capmCandidate": _benchmark_candidate_for_capm(benchmark_result),
        "candidateRule": "source in DB,KIS_BACKFILLED or source DB_INSUFFICIENT with availablePriceCount > 0",
        "warnings": _warning_codes(benchmark_result.warnings),
    }


def _benchmark_policy_multi(
    benchmark_results: dict[str, Feature3BenchmarkSeriesResult],
    benchmark_selections: dict[str, dict],
    fallback_expected: int,
) -> dict:
    holding_counts: dict[str, int] = {}
    warnings: list[str] = []
    for selection in benchmark_selections.values():
        code = selection.get("benchmarkCode") or DEFAULT_CAPM_BENCHMARK_CODE
        holding_counts[code] = holding_counts.get(code, 0) + 1
        if selection.get("selectionReason") == "UNKNOWN_MARKET_KOSPI_PROXY":
            warnings.append("UNKNOWN_MARKET_KOSPI_PROXY")
    if len(holding_counts) > 1:
        warnings.append("MIXED_MARKET_BENCHMARKS_USED")

    benchmark_items = []
    for code in sorted(holding_counts or {DEFAULT_CAPM_BENCHMARK_CODE: 0}):
        item = _benchmark_policy(benchmark_results.get(code), fallback_expected)
        item["benchmarkCode"] = item.get("benchmarkCode") or code
        item["holdingCount"] = holding_counts.get(code, 0)
        if not item.get("capmCandidate"):
            item_warnings = list(item.get("warnings") or [])
            if "CAPM_DISABLED_BENCHMARK_UNAVAILABLE" not in item_warnings:
                item_warnings.append("CAPM_DISABLED_BENCHMARK_UNAVAILABLE")
            item["warnings"] = item_warnings
        benchmark_items.append(item)
        warnings.extend(item.get("warnings") or [])

    primary = benchmark_items[0] if benchmark_items else _benchmark_policy(None, fallback_expected)
    return {
        "mode": "MULTI_BENCHMARK" if len(benchmark_items) > 1 else "SINGLE_BENCHMARK",
        "primaryBenchmarkCode": primary.get("benchmarkCode"),
        "primaryBenchmarkName": primary.get("benchmarkName"),
        "benchmarks": benchmark_items,
        "warnings": list(dict.fromkeys(warnings)),
        # Backward-compatible top-level fields for existing DTO/UI paths.
        "benchmarkCode": primary.get("benchmarkCode"),
        "benchmarkName": primary.get("benchmarkName"),
        "source": primary.get("source"),
        "benchmarkAvailable": primary.get("benchmarkAvailable"),
        "expectedTradingDayCount": primary.get("expectedTradingDayCount"),
        "availablePriceCount": primary.get("availablePriceCount"),
        "missingRate": primary.get("missingRate"),
        "capmCandidate": primary.get("capmCandidate"),
        "candidateRule": primary.get("candidateRule"),
    }


def _benchmark_selection_for_stock(stock_code: str, benchmark_selections: dict[str, dict]) -> dict:
    selection = benchmark_selections.get(stock_code)
    if selection:
        return selection
    return {
        "stockCode": stock_code,
        "exchangeCode": None,
        "benchmarkCode": DEFAULT_CAPM_BENCHMARK_CODE,
        "selectionReason": "UNKNOWN_MARKET_KOSPI_PROXY",
    }


def _empty_capm_policy(status: str) -> dict:
    return {
        "status": status,
        "model": "CAPM",
        "returnUnit": "ANNUAL",
        "weightsSumToOne": True,
    }


def _benchmark_candidate_for_capm(benchmark_result: Feature3BenchmarkSeriesResult | None) -> bool:
    if benchmark_result is None or len(benchmark_result.points) < 2:
        return False
    if benchmark_result.source in {"DB", "KIS_BACKFILLED"}:
        return True
    return benchmark_result.source == "DB_INSUFFICIENT" and benchmark_result.available_price_count > 0


def _benchmark_source_factor(source: str) -> float:
    if source in {"DB", "KIS_BACKFILLED"}:
        return 1.0
    if source == "DB_INSUFFICIENT":
        return 0.75
    return 0.0


def _capm_sample_factor(common_sample_size: int) -> float:
    if common_sample_size < CAPM_PARTIAL_MIN_SAMPLE:
        return 0.0
    if common_sample_size < MIN_OBSERVATIONS:
        return _clamp_float(common_sample_size / MIN_OBSERVATIONS, 0.35, 0.64)
    return _clamp_float(common_sample_size / 252.0, 0.65, 1.0)


def _dated_log_returns(points) -> dict:
    close_by_date = _close_by_trading_date(points)
    ordered = sorted(close_by_date.items(), key=lambda item: item[0])
    returns = {}
    for previous, current in zip(ordered, ordered[1:]):
        try:
            previous_date, previous_close = previous
            current_date, current_close = current
            returns[current_date] = float(np.log(current_close / previous_close))
        except Exception:
            continue
    return returns


def _capm_asset_warnings(asset_rows: list[dict]) -> list[Feature3Warning]:
    warning_messages = {
        "UNKNOWN_MARKET_KOSPI_PROXY": "Holding exchange was unknown, so KOSPI was used as a CAPM proxy benchmark.",
        "BENCHMARK_FETCH_FAILED_FOR_MARKET": "Selected listing-market benchmark fetch failed.",
        "CAPM_DISABLED_BENCHMARK_UNAVAILABLE": "Selected benchmark was unavailable for CAPM.",
        "MIXED_MARKET_BENCHMARKS_USED": "Multiple listing-market benchmarks were used for CAPM.",
        "CAPM_COMMON_SAMPLE_INSUFFICIENT": "Common asset/benchmark return sample was too short for CAPM.",
        "CAPM_PARTIAL_LOW_COMMON_SAMPLE": "CAPM was partially applied with fewer than 120 common return samples.",
        "CAPM_BETA_UNAVAILABLE": "Market return variance was too low to estimate beta.",
        "CAPM_CORRELATION_UNAVAILABLE": "Correlation could not be estimated for CAPM diagnostics.",
        "CAPM_LOW_R_SQUARED": "CAPM explanatory power was low for this asset.",
        "CAPM_HIGH_VOLATILITY": "Asset volatility reduced CAPM blend confidence.",
        "BLENDED_RETURN_CONFIDENCE_LOW": "Blended expected return confidence was low.",
    }
    user_messages = {
        "UNKNOWN_MARKET_KOSPI_PROXY": "상장시장 정보를 확인하지 못해 KOSPI를 대체 벤치마크로 사용했습니다.",
        "BENCHMARK_FETCH_FAILED_FOR_MARKET": "선택된 시장 벤치마크를 가져오지 못해 해당 종목의 CAPM 반영을 제한했습니다.",
        "CAPM_DISABLED_BENCHMARK_UNAVAILABLE": "선택된 시장 벤치마크 데이터를 사용할 수 없어 CAPM을 제외했습니다.",
        "MIXED_MARKET_BENCHMARKS_USED": "상장시장별 벤치마크를 분리해 CAPM을 계산했습니다.",
        "CAPM_COMMON_SAMPLE_INSUFFICIENT": "시장 벤치마크와 종목의 공통 수익률 표본이 부족해 CAPM을 제외했습니다.",
        "CAPM_PARTIAL_LOW_COMMON_SAMPLE": "공통 수익률 표본이 120개 미만이라 CAPM을 낮은 비중으로만 반영했습니다.",
        "CAPM_BETA_UNAVAILABLE": "시장 변동 표본이 충분하지 않아 beta를 계산하지 못했습니다.",
        "CAPM_CORRELATION_UNAVAILABLE": "시장과 종목 수익률의 상관 설명력을 계산하지 못했습니다.",
        "CAPM_LOW_R_SQUARED": "시장수익률이 이 종목 수익률을 설명하는 정도가 낮아 CAPM 반영 비중을 낮췄습니다.",
        "CAPM_HIGH_VOLATILITY": "종목 변동성이 높아 CAPM 반영 비중을 낮췄습니다.",
        "BLENDED_RETURN_CONFIDENCE_LOW": "기대수익률 혼합 신뢰도가 낮아 해석에 주의가 필요합니다.",
    }
    warnings: list[Feature3Warning] = []
    for row in asset_rows:
        for code in row.get("warnings") or []:
            warnings.append(Feature3Warning(
                code=code,
                message=warning_messages.get(code, code),
                user_message=user_messages.get(code),
                severity="WARN",
                target=row["stockCode"],
            ))
    return warnings


def _round_optional(value: float | None) -> float | None:
    if value is None or not np.isfinite(value):
        return None
    return round(float(value), 6)


def _covariance_matrix(returns_matrix: np.ndarray, requested_model: str) -> tuple[np.ndarray, str, float | None]:
    if requested_model == "SAMPLE_COVARIANCE":
        covariance = np.cov(returns_matrix, rowvar=False)
        if covariance.ndim == 0:
            covariance = np.array([[float(covariance)]])
        return covariance, "SAMPLE_COVARIANCE", None

    try:
        from sklearn.covariance import LedoitWolf

        model = LedoitWolf().fit(returns_matrix)
        return model.covariance_, "LEDOIT_WOLF", float(model.shrinkage_)
    except Exception:
        covariance = np.cov(returns_matrix, rowvar=False)
        if covariance.ndim == 0:
            covariance = np.array([[float(covariance)]])
        return covariance, "SAMPLE_COVARIANCE", None


def _correlation_matrix_payload(
    price_results: list[Feature3PriceSeriesResult],
    covariance_annual: np.ndarray,
) -> dict | None:
    diagonal = np.sqrt(np.maximum(np.diag(covariance_annual), 0.0))
    denom = np.outer(diagonal, diagonal)
    with np.errstate(divide="ignore", invalid="ignore"):
        corr = np.divide(covariance_annual, denom, out=np.zeros_like(covariance_annual), where=denom > 0)
    return {
        "labels": [result.company_name or result.stock_code for result in price_results],
        "stockCodes": [result.stock_code for result in price_results],
        "values": np.round(corr, 6).tolist(),
    }


def _risk_contributions_from_covariance(
    price_results: list[Feature3PriceSeriesResult],
    weights: np.ndarray,
    covariance_annual: np.ndarray,
    portfolio_volatility: float,
) -> list[Feature3RiskContribution]:
    if portfolio_volatility <= 0:
        return [
            Feature3RiskContribution(
                stock_code=result.stock_code,
                company_name=result.company_name,
                weight=round(float(weight), 6),
                volatility=None,
                marginal_risk_contribution=0.0,
                risk_contribution=0.0,
                risk_contribution_pct=0.0,
            )
            for result, weight in zip(price_results, weights)
        ]

    marginal = covariance_annual @ weights / portfolio_volatility
    contribution = weights * marginal
    contribution_sum = float(np.sum(contribution)) or 1.0
    return [
        Feature3RiskContribution(
            stock_code=result.stock_code,
            company_name=result.company_name,
            weight=round(float(weight), 6),
            volatility=round(float(np.sqrt(max(covariance_annual[idx, idx], 0.0))), 6),
            marginal_risk_contribution=round(float(marginal[idx]), 6),
            risk_contribution=round(float(contribution[idx]), 6),
            risk_contribution_pct=round(float(contribution[idx] / contribution_sum), 6),
        )
        for idx, (result, weight) in enumerate(zip(price_results, weights))
    ]


def _risk_level(volatility: float, target_volatility: float) -> RiskLevel:
    if volatility <= target_volatility * 0.90:
        return "LOW"
    if volatility <= target_volatility * 1.15:
        return "MID"
    return "HIGH"


def _risk_free_rate(req: PortfolioAnalyzeRequest) -> float:
    value = req.options.risk_free_rate
    if value is None or not np.isfinite(value):
        return 0.0
    return round(max(0.0, float(value)), 6)


def _current_valuation_price(holding, price_result: Feature3PriceSeriesResult) -> float:
    if holding.current_price is not None and np.isfinite(holding.current_price):
        return float(holding.current_price)
    if price_result.points:
        latest_close = price_result.points[-1].close
        if latest_close > 0 and np.isfinite(latest_close):
            return float(latest_close)
    return float(holding.avg_price)


def _max_cash_weight(gamma: float) -> float:
    normalized = (max(1.0, min(10.0, float(gamma))) - 1.0) / 9.0
    return round(normalized, 4)


def _resolved_max_cash_weight(req: PortfolioAnalyzeRequest, gamma: float) -> float:
    if req.options.max_cash_weight is None:
        return _max_cash_weight(gamma)
    return round(max(0.0, min(1.0, float(req.options.max_cash_weight))), 4)


def _sharpe_ratio(expected_return: float | None, volatility: float | None, risk_free_rate: float) -> float | None:
    if expected_return is None or volatility is None or volatility <= 1e-9:
        return None
    return round(float((expected_return - risk_free_rate) / volatility), 6)


def _mu_with_cash(annual_mu: np.ndarray | None, risk_free_rate: float, asset_count: int) -> np.ndarray | None:
    if annual_mu is None:
        return None
    return np.concatenate([annual_mu, np.array([risk_free_rate])])


def _expected_return_for_weights(
    risk_context: dict,
    risky_weights: np.ndarray,
    cash_weight: float,
    risk_free_rate: float,
    mu_key: str = "annual_mu",
) -> float | None:
    annual_mu = risk_context.get(mu_key)
    if annual_mu is None:
        return None
    return round(float(annual_mu @ risky_weights + cash_weight * risk_free_rate), 6)


def _is_display_capped(expected_return: float | None, display_expected_return: float | None) -> bool:
    if expected_return is None or display_expected_return is None:
        return False
    return abs(float(expected_return) - float(display_expected_return)) > 1e-9


def _display_expected_return(expected_return: float | None) -> float | None:
    if expected_return is None:
        return None
    return round(
        _clamp_float(
            float(expected_return),
            DISPLAY_EXPECTED_RETURN_CAP_LOWER,
            DISPLAY_EXPECTED_RETURN_CAP_UPPER,
        ),
        6,
    )


def _cash_weight(req: PortfolioAnalyzeRequest, total_value: float) -> float:
    if total_value <= 0:
        return 0.0
    return round(float(sum(cash.amount for cash in req.cash_positions if cash.amount > 0) / total_value), 6)


def _dummy_weights(req: PortfolioAnalyzeRequest) -> list[Feature3Weight]:
    values = [
        max(0.0, holding.quantity * (holding.current_price or holding.avg_price))
        for holding in req.holdings
    ]
    cash_values = [cash.amount for cash in req.cash_positions]
    total = sum(values) + sum(cash_values)
    if total <= 0:
        total = 1.0

    weights = [
        Feature3Weight(
            stock_code=holding.stock_code,
            company_name=holding.company_name,
            asset_type="EQUITY",
            weight=round(value / total, 6),
            quantity=round(float(holding.quantity), 6),
            avg_price=round(float(holding.avg_price), 6),
            current_price=round(float(holding.current_price or holding.avg_price), 6),
            cost_basis_value=round(float(holding.quantity * holding.avg_price), 6),
            market_value=round(float(value), 6),
            unrealized_pnl=round(float(value - holding.quantity * holding.avg_price), 6),
            unrealized_return_rate=round(float((value - holding.quantity * holding.avg_price) / (holding.quantity * holding.avg_price)), 6)
            if holding.quantity * holding.avg_price > 0 else None,
        )
        for holding, value in zip(req.holdings, values)
    ]
    weights.extend(
        Feature3Weight(
            stock_code=f"CASH_{cash.currency}",
            company_name=f"{cash.currency} 현금",
            asset_type="CASH",
            weight=round(cash.amount / total, 6),
        )
        for cash in req.cash_positions
        if cash.amount > 0
    )
    return weights


def _dummy_risk_contributions(weights: list[Feature3Weight]) -> list[Feature3RiskContribution]:
    risky_weight_sum = sum(weight.weight for weight in weights if weight.asset_type != "CASH") or 1.0
    return [
        Feature3RiskContribution(
            stock_code=weight.stock_code,
            company_name=weight.company_name,
            weight=weight.weight,
            volatility=0.0 if weight.asset_type == "CASH" else None,
            marginal_risk_contribution=None,
            risk_contribution=0.0 if weight.asset_type == "CASH" else round(weight.weight / risky_weight_sum, 6),
            risk_contribution_pct=0.0 if weight.asset_type == "CASH" else round(weight.weight / risky_weight_sum, 6),
        )
        for weight in weights
    ]


def _dummy_candidate(
    portfolio_type: str,
    label: str,
    target_volatility: float,
    weights: list[Feature3Weight],
) -> Feature3PortfolioResult:
    return Feature3PortfolioResult(
        type=portfolio_type,
        label=label,
        volatility=target_volatility,
        target_volatility=target_volatility,
        achieved_volatility=target_volatility,
        risk_level="MID",
        weights=weights,
        risk_contributions=_dummy_risk_contributions(weights),
        user_description=f"{label} 목표 변동성 기준 임시 비교 포트폴리오입니다.",
    )


def _basic_profile_target_volatilities(
    risk_context: dict,
    stable_target: float,
    balanced_target: float,
    aggressive_target: float,
    max_cash_weight: float,
) -> tuple[float, float, float]:
    eligible_results = risk_context["eligible_results"]
    covariance_annual = risk_context["covariance_annual"]
    if not eligible_results or covariance_annual is None:
        return stable_target, balanced_target, aggressive_target

    min_vol, max_vol = _feasible_volatility_range_with_cash(
        covariance_annual,
        len(eligible_results),
        max_cash_weight,
    )
    if not np.isfinite(min_vol) or not np.isfinite(max_vol) or max_vol <= min_vol + 1e-6:
        return stable_target, balanced_target, aggressive_target

    vol_range = max_vol - min_vol
    min_spacing = max(0.003, vol_range * 0.15)

    targets = [
        _clamp_float(stable_target, min_vol, max_vol),
        _clamp_float(balanced_target, min_vol, max_vol),
        _clamp_float(aggressive_target, min_vol, max_vol),
    ]
    if targets[1] - targets[0] >= min_spacing and targets[2] - targets[1] >= min_spacing:
        return tuple(round(target, 4) for target in targets)  # type: ignore[return-value]

    if aggressive_target <= min_vol:
        adjusted = [min_vol, min_vol + vol_range * 0.45, min_vol + vol_range * 0.90]
    elif stable_target >= max_vol:
        adjusted = [min_vol + vol_range * 0.10, min_vol + vol_range * 0.55, max_vol]
    else:
        adjusted = [
            min(targets[0], min_vol + vol_range * 0.20),
            max(targets[1], min_vol + vol_range * 0.50),
            max(targets[2], min_vol + vol_range * 0.85),
        ]
    adjusted = [_clamp_float(target, min_vol, max_vol) for target in adjusted]
    adjusted[1] = max(adjusted[1], min(max_vol, adjusted[0] + min_spacing))
    adjusted[2] = max(adjusted[2], min(max_vol, adjusted[1] + min_spacing))
    return tuple(round(target, 4) for target in adjusted)  # type: ignore[return-value]


def _target_volatility_candidate(
    req: PortfolioAnalyzeRequest,
    risk_context: dict,
    portfolio_type: str,
    label: str,
    target_volatility: float,
    fallback_weights: list[Feature3Weight],
    risk_free_rate: float,
    max_cash_weight: float,
) -> Feature3PortfolioResult:
    # 현재 구현: 기대수익률(mu)을 쓰지 않고 목표 변동성에 가장 가까운 long-only 조합만 찾는다.
    # 진행 예정: Phase 7 이후 Efficient Frontier/MEAN_VARIANCE_ADVANCED는 별도 advanced 모드에서만 다룬다.
    eligible_results = risk_context["eligible_results"]
    covariance_annual = risk_context["covariance_annual"]
    if not eligible_results or covariance_annual is None:
        return _nearest_feasible_from_weights(portfolio_type, label, target_volatility, fallback_weights, max_cash_weight)

    asset_count = len(eligible_results)
    cash_index = asset_count
    dimension = asset_count + 1
    covariance_with_cash = np.zeros((dimension, dimension), dtype=float)
    covariance_with_cash[:asset_count, :asset_count] = covariance_annual
    annual_mu = risk_context.get("annual_mu")
    mu_with_cash = _mu_with_cash(annual_mu, risk_free_rate, asset_count)
    raw_mu_with_cash = _mu_with_cash(risk_context.get("raw_historical_annual_mu"), risk_free_rate, asset_count)
    display_mu_with_cash = _mu_with_cash(risk_context.get("display_annual_mu"), risk_free_rate, asset_count)

    x0 = np.zeros(dimension, dtype=float)
    risky_prior = _inverse_volatility_weights(covariance_annual, asset_count)
    risky_prior_volatility = float(np.sqrt(max(risky_prior.T @ covariance_annual @ risky_prior, 0.0)))
    if risky_prior_volatility <= 1e-9:
        initial_cash_weight = max_cash_weight
    else:
        target_risky_budget = min(1.0, max(0.0, target_volatility / risky_prior_volatility))
        initial_cash_weight = min(max_cash_weight, max(0.0, 1.0 - target_risky_budget))
    x0[:asset_count] = risky_prior * (1.0 - initial_cash_weight)
    x0[cash_index] = initial_cash_weight
    x0 = x0 / float(np.sum(x0) or 1.0)

    risky_max_weight = _risky_max_weight(asset_count)
    bounds = [(0.0, risky_max_weight) for _ in range(asset_count)] + [(0.0, max_cash_weight)]

    def portfolio_volatility(weights: np.ndarray) -> float:
        variance = float(weights.T @ covariance_with_cash @ weights)
        return float(np.sqrt(max(variance, 0.0)))

    def objective(weights: np.ndarray) -> float:
        vol_gap = portfolio_volatility(weights) - target_volatility
        # 목표 변동성만 맞추면 극단해가 나올 수 있어, 종목별 변동성 기반 기준비중과의 거리를 약하게 패널티로 둔다.
        return float(vol_gap * vol_gap + 0.0015 * np.sum((weights - x0) ** 2))

    constraints = [{"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)}]
    try:
        from scipy.optimize import minimize

        result = minimize(
            objective,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=constraints,
            options={"maxiter": 300, "ftol": 1e-10},
        )
        if result.success and _weights_satisfy_constraints(result.x, asset_count, max_cash_weight):
            weights = np.clip(result.x, 0.0, None)
            weights = weights / float(np.sum(weights))
        else:
            weights = _grid_candidate_search(covariance_with_cash, target_volatility, asset_count, max_cash_weight)
    except Exception:
        weights = _grid_candidate_search(covariance_with_cash, target_volatility, asset_count, max_cash_weight)

    achieved = round(portfolio_volatility(weights), 6)
    expected_return = round(float(mu_with_cash @ weights), 6) if mu_with_cash is not None else None
    raw_historical_return = round(float(raw_mu_with_cash @ weights), 6) if raw_mu_with_cash is not None else None
    display_expected_return = _display_expected_return(expected_return)
    status = "SUCCESS" if abs(achieved - target_volatility) <= max(0.005, target_volatility * 0.08) else "NEAREST_FEASIBLE"
    weight_items = [
        Feature3Weight(
            stock_code=result.stock_code,
            company_name=result.company_name,
            asset_type="EQUITY",
            weight=round(float(weights[idx]), 6),
        )
        for idx, result in enumerate(eligible_results)
        if weights[idx] > 0.000001
    ]
    if weights[cash_index] > 0.000001:
        weight_items.append(
            Feature3Weight(
                stock_code="CASH_KRW",
                company_name="KRW 현금",
                asset_type="CASH",
                weight=round(float(weights[cash_index]), 6),
            )
        )

    risky_weights = weights[:asset_count]
    risk_contributions = _risk_contributions_from_covariance(
        eligible_results,
        risky_weights,
        covariance_annual,
        achieved,
    )
    if weights[cash_index] > 0.000001:
        risk_contributions.append(
            Feature3RiskContribution(
                stock_code="CASH_KRW",
                company_name="KRW 현금",
                weight=round(float(weights[cash_index]), 6),
                volatility=0.0,
                marginal_risk_contribution=0.0,
                risk_contribution=0.0,
                risk_contribution_pct=0.0,
            )
        )

    return Feature3PortfolioResult(
        type=portfolio_type,
        label=label,
        expected_return=expected_return,
        raw_historical_return=raw_historical_return,
        display_expected_return=display_expected_return,
        is_display_capped=_is_display_capped(expected_return, display_expected_return),
        sharpe_ratio=_sharpe_ratio(expected_return, achieved, risk_free_rate),
        volatility=achieved,
        target_volatility=target_volatility,
        achieved_volatility=achieved,
        risk_level=_risk_level(achieved, target_volatility),
        optimization_status=status,
        weights=weight_items,
        risk_contributions=risk_contributions,
        user_description=f"{label} 목표 변동성 {target_volatility:.1%}에 맞춘 제약 기반 비교 포트폴리오입니다.",
    )


def _advanced_mean_variance_outputs(
    req: PortfolioAnalyzeRequest,
    risk_context: dict,
    target_volatility: float,
    risk_free_rate: float,
    max_cash_weight: float,
) -> tuple[list[Feature3PortfolioResult], list[dict], dict, list[Feature3Warning]]:
    # Advanced 검증용 frontier/utility point는 risk_context["annual_mu"]를 사용한다.
    # 2-B 이후 이 값은 historical/CAPM confidence 기반 blended expected return이다.
    eligible_results = risk_context["eligible_results"]
    covariance_annual = risk_context["covariance_annual"]
    annual_mu = risk_context.get("annual_mu")
    if not eligible_results or covariance_annual is None or annual_mu is None:
        return [], [], _expected_return_policy("UNAVAILABLE"), []

    warnings = [
        Feature3Warning(
            code="EXPECTED_RETURN_ESTIMATION_UNSTABLE",
            message="Advanced mean-variance outputs use blended historical/CAPM expected returns, which are estimates.",
            user_message="고급 검증의 기대수익률은 과거 수익률과 CAPM 추정치를 데이터 신뢰도에 따라 혼합한 값이라 실제 수익 예측으로 해석하면 안 됩니다.",
            severity="WARN",
            target="advanced.expectedReturnPolicy",
        )
    ]
    if not req.options.include_frontier and req.options.view_mode != "ADVANCED":
        return [], [], _expected_return_policy("BLENDED_HISTORICAL_CAPM", risk_free_rate, req, risk_context), []

    asset_count = len(eligible_results)
    gamma = req.risk_profile.risk_aversion_gamma or (10 - 9 * req.risk_profile.risk_tolerance_score)
    mu_with_cash = np.concatenate([annual_mu, np.array([risk_free_rate])])
    raw_mu_with_cash = np.concatenate([
        risk_context.get("raw_historical_annual_mu", annual_mu),
        np.array([risk_free_rate]),
    ])
    display_mu_with_cash = np.concatenate([
        risk_context.get("display_annual_mu", annual_mu),
        np.array([risk_free_rate]),
    ])

    min_vol_risky_weights = _minimum_variance_weights(covariance_annual, asset_count)
    min_vol = _portfolio_from_weight_vector(
        "MIN_VOL",
        "Minimum Volatility",
        eligible_results,
        np.concatenate([min_vol_risky_weights, np.array([0.0])]),
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        display_mu_with_cash,
        target_volatility,
        risk_free_rate,
    )
    max_sharpe_risky_weights = _max_sharpe_weights(annual_mu, covariance_annual, risk_free_rate, asset_count)
    max_sharpe = _portfolio_from_weight_vector(
        "MAX_SHARPE",
        "Max Sharpe",
        eligible_results,
        np.concatenate([max_sharpe_risky_weights, np.array([0.0])]),
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        display_mu_with_cash,
        target_volatility,
        risk_free_rate,
    )
    risk_allocation_weights = _user_risk_allocation_weights(
        max_sharpe_risky_weights,
        annual_mu,
        covariance_annual,
        risk_free_rate,
        gamma,
        max_cash_weight,
    )
    risk_allocation = _portfolio_from_weight_vector(
        "RISK_ALLOCATION",
        "CAL Risk Allocation",
        eligible_results,
        risk_allocation_weights,
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        display_mu_with_cash,
        target_volatility,
        risk_free_rate,
    )
    risk_allocation.user_description = (
        "최대샤프 위험자산 포트폴리오와 무위험자산을 조합하고, 사용자 위험회피도와 현금 한도에 따라 위험자산 비중을 조절한 포트폴리오입니다."
    )
    utility_weights = _utility_optimal_weights(
        annual_mu,
        covariance_annual,
        risk_free_rate,
        gamma,
        max_cash_weight,
        asset_count,
    )
    utility = _portfolio_from_weight_vector(
        "UTILITY_OPTIMAL",
        "Utility Optimal",
        eligible_results,
        utility_weights,
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        display_mu_with_cash,
        target_volatility,
        risk_free_rate,
    )
    utility.user_description = (
        "현재 현금 한도, 레버리지 금지, 종목별 비중 제한 안에서 U = E[R] - 0.5 * gamma * variance를 직접 최대화한 포트폴리오입니다."
    )
    theoretical_utility = _theoretical_utility_portfolio(
        req,
        eligible_results,
        max_sharpe_risky_weights,
        annual_mu,
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        target_volatility,
        risk_free_rate,
        gamma,
    )
    frontier = _efficient_frontier_points(
        annual_mu,
        covariance_annual,
        asset_count,
        risk_free_rate,
        risk_context.get("raw_historical_annual_mu", annual_mu),
        risk_context.get("display_annual_mu", annual_mu),
    )
    return [
        min_vol,
        max_sharpe,
        risk_allocation,
        utility,
        *([theoretical_utility] if theoretical_utility is not None else []),
    ], frontier, _expected_return_policy("BLENDED_HISTORICAL_CAPM", risk_free_rate, req, risk_context), warnings


def _expected_return_policy(
    status: str,
    risk_free_rate: float | None = None,
    req: PortfolioAnalyzeRequest | None = None,
    risk_context: dict | None = None,
) -> dict:
    if status == "BLENDED_HISTORICAL_CAPM" and risk_context is not None:
        policy = dict(risk_context.get("expected_return_estimation") or {})
        policy["status"] = "AVAILABLE"
        return policy
    gamma = req.risk_profile.risk_aversion_gamma or (10 - 9 * req.risk_profile.risk_tolerance_score) if req is not None else None
    return {
        "estimator": "HISTORICAL_MEAN" if status != "UNAVAILABLE" else None,
        "status": status,
        "warning": "EXPECTED_RETURN_ESTIMATION_UNSTABLE" if status == "HISTORICAL_MEAN" else None,
        "basicModeUsesExpectedReturn": False,
        "riskFreeRate": risk_free_rate,
        "riskFreeRateSource": req.options.risk_free_rate_source if req is not None else None,
        "riskFreeRateAsOf": req.options.risk_free_rate_as_of if req is not None else None,
        "maxCashWeight": _resolved_max_cash_weight(req, gamma) if req is not None and gamma is not None else None,
        "maxCashWeightSource": "USER_OVERRIDE" if req is not None and req.options.max_cash_weight is not None else "GAMMA_AUTO",
        "frontierAssetUniverse": "RISKY_ASSETS_ONLY",
    }


def _overlay_adjusted_outputs(
    req: PortfolioAnalyzeRequest,
    risk_context: dict,
    target_volatility: float,
    risk_free_rate: float,
) -> tuple[list[Feature3PortfolioResult], list[dict], list[dict]]:
    signals = req.overlay_signals or []
    eligible_results = risk_context["eligible_results"]
    covariance_annual = risk_context["covariance_annual"]
    annual_mu = risk_context.get("annual_mu")
    base_weights = _base_overlay_weights(req, eligible_results)
    signals = _augment_overlay_signals_for_visualization(signals, base_weights, eligible_results, risk_context)
    if not signals or not eligible_results or covariance_annual is None or annual_mu is None:
        return [], _overlay_visualizations(signals), _overlay_explanations(signals)

    asset_count = len(eligible_results)
    mu_with_cash = np.concatenate([annual_mu, np.array([risk_free_rate])])
    raw_mu_with_cash = np.concatenate([
        risk_context.get("raw_historical_annual_mu", annual_mu),
        np.array([risk_free_rate]),
    ])
    display_mu_with_cash = np.concatenate([
        risk_context.get("display_annual_mu", annual_mu),
        np.array([risk_free_rate]),
    ])
    selected_types = {signal.overlay_type for signal in signals}
    variants = []
    if len(selected_types) >= 2:
        variants.append(("OVERLAY_BALANCED", "보조 관측 균형 시나리오", selected_types, 0.35))
    if "fundamentals" in selected_types:
        variants.append(("QUALITY_TILT", "종목 건강도 시나리오", {"fundamentals"}, 0.35))
    if "technical" in selected_types:
        variants.append(("MOMENTUM_AWARE", "기술 흐름 시나리오", {"technical"}, 0.25))
    if "news" in selected_types:
        variants.append(("NEWS_GUARDED", "뉴스 리스크 시나리오", {"news"}, 0.25))
    diversification_types = selected_types & {"industry", "correlation"}
    if diversification_types:
        variants.append(("DIVERSIFICATION_TILT", "분산 보강 시나리오", diversification_types, _diversification_strength(diversification_types)))
    portfolios: list[Feature3PortfolioResult] = []
    for portfolio_type, label, overlay_types, strength in variants:
        adjusted = _apply_overlay_tilt(
            base_weights,
            eligible_results,
            signals,
            overlay_types,
            strength,
            risk_context,
            half_turnover_budget=0.15,
            single_delta_cap=0.07,
        )
        portfolio = _portfolio_from_weight_vector(
            portfolio_type,
            label,
            eligible_results,
            adjusted,
            covariance_annual,
            mu_with_cash,
            raw_mu_with_cash,
            display_mu_with_cash,
            target_volatility,
            risk_free_rate,
        )
        portfolio.user_description = _overlay_portfolio_description(label, signals, overlay_types)
        portfolios.append(portfolio)
    return portfolios, _overlay_visualizations(signals), _overlay_explanations(signals)


def _augment_overlay_signals_for_visualization(
    signals: list[Feature3OverlaySignal],
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    risk_context: dict,
) -> list[Feature3OverlaySignal]:
    augmented = list(signals)
    industry_penalties = _industry_concentration_penalties(base_weights, eligible_results, signals)
    correlation_penalties = _risk_contribution_penalties(base_weights, eligible_results, risk_context)
    risk_contribution_pct = _risk_contribution_pct_by_code(base_weights, eligible_results, risk_context)
    for signal in augmented:
        if signal.overlay_type == "industry" and signal.stock_code in industry_penalties:
            signal.score = round(float(industry_penalties[signal.stock_code]), 4)
            signal.evidence = _industry_evidence_with_weight(signal, base_weights, eligible_results, signals)
        if signal.overlay_type == "correlation" and signal.stock_code in correlation_penalties:
            signal.score = round(_clamp_float(correlation_penalties[signal.stock_code], -1.0, 1.0), 4)
            rc_pct = risk_contribution_pct.get(signal.stock_code)
            rc_text = f"위험기여 {rc_pct:.1%}" if rc_pct is not None else "위험기여 확인"
            signal.evidence = f"{signal.evidence or ''} {rc_text}, scenario penalty={correlation_penalties[signal.stock_code]:.2f}".strip()
    return augmented


def _industry_evidence_with_weight(
    signal: Feature3OverlaySignal,
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    signals: list[Feature3OverlaySignal],
) -> str:
    industry_name = (signal.evidence or "").split(" / ")[0].strip()
    if not industry_name:
        return signal.evidence or ""
    industry_by_code = {
        item.stock_code: (item.evidence or "").split(" / ")[0].strip()
        for item in signals
        if item.overlay_type == "industry" and item.evidence
    }
    total = 0.0
    for idx, result in enumerate(eligible_results):
        if industry_by_code.get(result.stock_code) == industry_name:
            total += float(base_weights[idx])
    return f"{signal.evidence or industry_name} · 포트폴리오 업종 비중 {total:.1%}"


def _base_overlay_weights(req: PortfolioAnalyzeRequest, eligible_results: list[Feature3PriceSeriesResult]) -> np.ndarray:
    holding_by_code = {holding.stock_code: holding for holding in req.holdings}
    risky_values = [
        max(0.0, holding_by_code[result.stock_code].quantity * (
            holding_by_code[result.stock_code].current_price
            or (result.points[-1].close if result.points else holding_by_code[result.stock_code].avg_price)
        ))
        for result in eligible_results
    ]
    cash_value = sum(cash.amount for cash in req.cash_positions if cash.amount > 0)
    total = sum(risky_values) + cash_value
    if total <= 0:
        risky = np.full(len(eligible_results), 1.0 / max(1, len(eligible_results)), dtype=float)
        return np.concatenate([risky, np.array([0.0])])
    risky_weights = np.array([value / total for value in risky_values], dtype=float)
    return np.concatenate([risky_weights, np.array([cash_value / total])])


def _apply_overlay_tilt(
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    signals: list[Feature3OverlaySignal],
    overlay_types: set[str],
    strength: float,
    risk_context: dict,
    half_turnover_budget: float,
    single_delta_cap: float,
) -> np.ndarray:
    stock_codes = [result.stock_code for result in eligible_results]
    score_by_code = {code: 0.0 for code in stock_codes}
    count_by_code = {code: 0 for code in stock_codes}
    for signal in signals:
        if signal.overlay_type not in overlay_types or signal.stock_code not in score_by_code:
            continue
        if signal.overlay_type == "correlation":
            continue
        score_by_code[signal.stock_code] += _effective_overlay_score(signal)
        count_by_code[signal.stock_code] += 1
    if "industry" in overlay_types:
        industry_penalties = _industry_concentration_penalties(base_weights, eligible_results, signals)
        for code, penalty in industry_penalties.items():
            score_by_code[code] += penalty
            count_by_code[code] += 1
    if "correlation" in overlay_types:
        correlation_penalties = _risk_contribution_penalties(base_weights, eligible_results, risk_context)
        for code, penalty in correlation_penalties.items():
            score_by_code[code] += penalty
            count_by_code[code] += 1
    modifiers = []
    for code in stock_codes:
        avg_score = score_by_code[code] / count_by_code[code] if count_by_code[code] else 0.0
        modifiers.append(_clamp_float(1.0 + strength * avg_score, 0.55, 1.30))
    adjusted_risky = base_weights[:-1] * np.array(modifiers, dtype=float)
    cash_weight = float(base_weights[-1])
    risky_budget = max(0.0, 1.0 - cash_weight)
    if float(np.sum(adjusted_risky)) <= 1e-12:
        adjusted_risky = base_weights[:-1]
    adjusted_risky = adjusted_risky / float(np.sum(adjusted_risky) or 1.0) * risky_budget
    adjusted_risky = _cap_risky_weights_preserving_cash(adjusted_risky, cash_weight)
    adjusted = np.concatenate([adjusted_risky, np.array([cash_weight])])
    return _limit_overlay_change(base_weights, adjusted, half_turnover_budget, single_delta_cap)


def _diversification_strength(overlay_types: set[str]) -> float:
    if overlay_types == {"industry"}:
        return 0.35
    if overlay_types == {"correlation"}:
        return 0.35
    return 0.35


def _effective_overlay_score(signal: Feature3OverlaySignal) -> float:
    confidence = _overlay_confidence(signal)
    decay = 1.0
    regime_multiplier = 1.0
    return float(signal.score) * confidence * decay * regime_multiplier


def _overlay_confidence(signal: Feature3OverlaySignal) -> float:
    evidence = signal.evidence or ""
    overlay_type = signal.overlay_type
    if overlay_type == "news":
        count = _parse_overlay_number(evidence, "count=")
        return _clamp_float((count or 0.0) / 15.0, 0.30, 1.0)
    if overlay_type == "technical":
        return 0.70
    if overlay_type == "fundamentals":
        has_fundamental_metrics = any(token in evidence for token in ("PER=", "PBR=", "ROE=", "OPM=", "Debt="))
        return 0.90 if has_fundamental_metrics else 0.55
    if overlay_type == "industry":
        return 0.90 if evidence else 0.50
    if overlay_type == "correlation":
        return 1.00
    return 0.75


def _parse_overlay_number(text: str, key: str) -> float | None:
    match = re.search(rf"{re.escape(key)}([-+]?\d+(?:\.\d+)?)", text or "")
    if not match:
        return None
    try:
        return float(match.group(1))
    except ValueError:
        return None


def _industry_concentration_penalties(
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    signals: list[Feature3OverlaySignal],
) -> dict[str, float]:
    industry_by_code = {
        signal.stock_code: (signal.evidence or "").split(" / ")[0].strip()
        for signal in signals
        if signal.overlay_type == "industry" and signal.evidence
    }
    industry_weight: dict[str, float] = {}
    for idx, result in enumerate(eligible_results):
        industry = industry_by_code.get(result.stock_code)
        if not industry:
            continue
        industry_weight[industry] = industry_weight.get(industry, 0.0) + float(base_weights[idx])
    penalties: dict[str, float] = {}
    for idx, result in enumerate(eligible_results):
        industry = industry_by_code.get(result.stock_code)
        if not industry:
            continue
        weight = industry_weight.get(industry, 0.0)
        if weight >= 0.70:
            penalties[result.stock_code] = -0.35
        elif weight >= 0.50:
            penalties[result.stock_code] = -0.22
        elif weight >= 0.35:
            penalties[result.stock_code] = -0.10
        else:
            penalties[result.stock_code] = 0.0
    return penalties


def _risk_contribution_penalties(
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    risk_context: dict,
) -> dict[str, float]:
    contribution_pct = _risk_contribution_pct_by_code(base_weights, eligible_results, risk_context)
    penalties: dict[str, float] = {}
    for code, pct in contribution_pct.items():
        if pct >= 0.45:
            penalties[code] = -0.28
        elif pct >= 0.30:
            penalties[code] = -0.18
        elif pct >= 0.20:
            penalties[code] = -0.10
    return penalties


def _risk_contribution_pct_by_code(
    base_weights: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    risk_context: dict,
) -> dict[str, float]:
    covariance_annual = risk_context.get("covariance_annual")
    if covariance_annual is None or len(eligible_results) == 0:
        return {}
    risky_weights = np.array(base_weights[:-1], dtype=float)
    if risky_weights.size == 0 or float(np.sum(risky_weights)) <= 1e-12:
        return {}
    portfolio_volatility = float(np.sqrt(max(float(risky_weights.T @ covariance_annual @ risky_weights), 0.0)))
    if portfolio_volatility <= 1e-12:
        return {}
    contributions = _risk_contributions_from_covariance(
        eligible_results,
        risky_weights,
        covariance_annual,
        portfolio_volatility,
    )
    return {
        item.stock_code: float(item.risk_contribution_pct)
        for item in contributions
        if item.risk_contribution_pct is not None
    }


def _limit_overlay_change(
    base_weights: np.ndarray,
    adjusted_weights: np.ndarray,
    half_turnover_budget: float,
    single_delta_cap: float,
) -> np.ndarray:
    if base_weights.size != adjusted_weights.size or base_weights.size == 0:
        return adjusted_weights
    cash_weight = float(base_weights[-1])
    risky_budget = max(0.0, 1.0 - cash_weight)
    base_risky = np.array(base_weights[:-1], dtype=float)
    desired_risky = np.array(adjusted_weights[:-1], dtype=float)
    if base_risky.size == 0:
        return np.array([cash_weight], dtype=float)
    max_weight = _risky_max_weight(int(base_risky.size))
    raw_delta = desired_risky - base_risky
    lower = np.maximum(-single_delta_cap, -base_risky)
    upper = np.minimum(single_delta_cap, max_weight - base_risky)
    delta = np.clip(raw_delta, lower, upper)
    delta = _rebalance_overlay_delta(delta)
    half_turnover = 0.5 * float(np.sum(np.abs(delta)))
    if half_turnover > half_turnover_budget and half_turnover > 1e-12:
        delta = delta * float(half_turnover_budget / half_turnover)
        delta = np.clip(delta, lower, upper)
        delta = _rebalance_overlay_delta(delta)
    risky = np.clip(base_risky + delta, 0.0, max_weight)
    total = float(np.sum(risky))
    if total > 1e-12:
        risky = risky / total * risky_budget
    risky = _cap_risky_weights_preserving_cash(risky, cash_weight)
    return np.concatenate([risky, np.array([cash_weight])])


def _rebalance_overlay_delta(delta: np.ndarray) -> np.ndarray:
    balanced = np.array(delta, dtype=float)
    for _ in range(6):
        total = float(np.sum(balanced))
        if abs(total) <= 1e-10:
            break
        if total > 0:
            positive = balanced > 0
            positive_sum = float(np.sum(balanced[positive]))
            if positive_sum <= 1e-12:
                break
            balanced[positive] *= max(0.0, (positive_sum - total) / positive_sum)
        else:
            negative = balanced < 0
            negative_sum = float(np.sum(-balanced[negative]))
            if negative_sum <= 1e-12:
                break
            balanced[negative] *= max(0.0, (negative_sum + total) / negative_sum)
    return balanced


def _cap_risky_weights_preserving_cash(risky_weights: np.ndarray, cash_weight: float) -> np.ndarray:
    if risky_weights.size == 0:
        return risky_weights
    cap = _risky_max_weight(int(risky_weights.size))
    budget = max(0.0, 1.0 - cash_weight)
    weights = np.clip(risky_weights, 0.0, cap)
    for _ in range(8):
        total = float(np.sum(weights))
        if abs(total - budget) <= 1e-9:
            break
        if total <= 1e-12:
            weights = np.full_like(weights, budget / weights.size)
            break
        weights = weights / total * budget
        weights = np.clip(weights, 0.0, cap)
    total = float(np.sum(weights))
    return weights / total * budget if total > 1e-12 else weights


def _overlay_visualizations(signals: list[Feature3OverlaySignal]) -> list[dict]:
    by_type: dict[str, list[Feature3OverlaySignal]] = {}
    for signal in signals:
        by_type.setdefault(signal.overlay_type, []).append(signal)
    visualizations = []
    for overlay_type, items in by_type.items():
        visualizations.append({
            "type": overlay_type,
            "title": _overlay_type_title(overlay_type),
            "chartType": "bar",
            "items": [
                {
                    "stockCode": item.stock_code,
                    "companyName": item.company_name,
                    "label": item.label,
                    "score": round(float(item.score), 4),
                    "severity": item.severity,
                    "evidence": item.evidence,
                }
                for item in items
            ],
        })
    return visualizations


def _overlay_explanations(signals: list[Feature3OverlaySignal]) -> list[dict]:
    explanations = []
    for signal in signals:
        direction = "확대 압력" if signal.score > 0.08 else "축소 압력" if signal.score < -0.08 else "관찰 압력"
        explanations.append({
            "stockCode": signal.stock_code,
            "companyName": signal.company_name,
            "overlayType": signal.overlay_type,
            "title": f"{signal.label}: {direction}",
            "description": signal.evidence or "보조 관측값을 포트폴리오 비중 조정 시나리오에 반영했습니다.",
            "score": round(float(signal.score), 4),
            "severity": signal.severity,
        })
    return explanations


def _overlay_portfolio_description(label: str, signals: list[Feature3OverlaySignal], overlay_types: set[str]) -> str:
    used = [signal for signal in signals if signal.overlay_type in overlay_types]
    warn_count = sum(1 for signal in used if signal.severity == "WARN" or signal.score < -0.15)
    positive_count = sum(1 for signal in used if signal.score > 0.15)
    if "correlation" in overlay_types:
        return (
            f"{label}: core risk 결과를 대체하지 않는 시뮬레이션입니다. "
            "분산 리스크는 단순 상관계수가 아니라 보유 종목의 위험 기여도를 우선 기준으로 압력 방향을 관측합니다."
        )
    return (
        f"{label}: core risk 결과를 대체하지 않는 보조 관측 시나리오입니다. "
        f"보조 관측 {len(used)}개 중 축소/주의 압력 {warn_count}개, 우호 압력 {positive_count}개를 제한적으로 관측했습니다."
    )


def _overlay_type_title(overlay_type: str) -> str:
    return {
        "fundamentals": "종목 건강도",
        "technical": "기술적 지표",
        "industry": "산업 집중도",
        "correlation": "내부 분산 리스크",
        "news": "뉴스 흐름",
    }.get(overlay_type, overlay_type)


def _minimum_variance_weights(covariance_annual: np.ndarray, asset_count: int) -> np.ndarray:
    x0 = np.full(asset_count, 1.0 / asset_count, dtype=float)
    bounds = [(0.0, _risky_max_weight(asset_count)) for _ in range(asset_count)]

    def objective(weights: np.ndarray) -> float:
        return float(weights.T @ covariance_annual @ weights)

    constraints = [{"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)}]
    try:
        from scipy.optimize import minimize

        result = minimize(
            objective,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=constraints,
            options={"maxiter": 300, "ftol": 1e-10},
        )
        if result.success and _risky_weights_satisfy_constraints(result.x):
            weights = np.clip(result.x, 0.0, None)
            return weights / float(np.sum(weights))
    except Exception:
        pass
    return _equal_risky_weights(asset_count)


def _max_sharpe_weights(
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    risk_free_rate: float,
    asset_count: int,
) -> np.ndarray:
    x0 = np.full(asset_count, 1.0 / asset_count, dtype=float)
    bounds = [(0.0, _risky_max_weight(asset_count)) for _ in range(asset_count)]

    def objective(weights: np.ndarray) -> float:
        variance = float(weights.T @ covariance_annual @ weights)
        volatility = float(np.sqrt(max(variance, 0.0)))
        if volatility <= 1e-9:
            return 1e6
        expected_return = float(annual_mu @ weights)
        return -((expected_return - risk_free_rate) / volatility)

    constraints = [{"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)}]
    try:
        from scipy.optimize import minimize

        result = minimize(
            objective,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=constraints,
            options={"maxiter": 300, "ftol": 1e-10},
        )
        if result.success and _risky_weights_satisfy_constraints(result.x):
            weights = np.clip(result.x, 0.0, None)
            return weights / float(np.sum(weights))
    except Exception:
        pass
    return _equal_risky_weights(asset_count)


def _user_risk_allocation_weights(
    risky_weights: np.ndarray,
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    risk_free_rate: float,
    gamma: float,
    max_cash_weight: float,
) -> np.ndarray:
    risky_return = float(annual_mu @ risky_weights)
    risky_variance = float(risky_weights.T @ covariance_annual @ risky_weights)
    if risky_variance <= 1e-10:
        risky_allocation = 1.0 - max_cash_weight
    else:
        risky_allocation = (risky_return - risk_free_rate) / (max(1.0, gamma) * risky_variance)
    risky_allocation = max(1.0 - max_cash_weight, min(1.0, risky_allocation))
    cash_weight = 1.0 - risky_allocation
    return np.concatenate([risky_weights * risky_allocation, np.array([cash_weight])])


def _utility_optimal_weights(
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    risk_free_rate: float,
    gamma: float,
    max_cash_weight: float,
    asset_count: int,
) -> np.ndarray:
    dimension = asset_count + 1
    covariance_with_cash = _with_cash_covariance(covariance_annual)
    mu_with_cash = np.concatenate([annual_mu, np.array([risk_free_rate])])
    x0 = np.full(dimension, 1.0 / dimension, dtype=float)
    x0[-1] = min(max_cash_weight, max(0.0, x0[-1]))
    risky_budget = 1.0 - x0[-1]
    x0[:asset_count] = risky_budget / asset_count
    bounds = [(0.0, _risky_max_weight(asset_count)) for _ in range(asset_count)] + [(0.0, max_cash_weight)]

    def objective(weights: np.ndarray) -> float:
        expected_return = float(mu_with_cash @ weights)
        variance = float(weights.T @ covariance_with_cash @ weights)
        utility = expected_return - 0.5 * max(1.0, gamma) * variance
        return -utility

    constraints = [{"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)}]
    try:
        from scipy.optimize import minimize

        result = minimize(
            objective,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=constraints,
            options={"maxiter": 500, "ftol": 1e-12},
        )
        if result.success and _weights_satisfy_constraints(result.x, asset_count, max_cash_weight):
            weights = np.clip(result.x, 0.0, None)
            return weights / float(np.sum(weights))
    except Exception:
        pass

    return _user_risk_allocation_weights(
        _max_sharpe_weights(annual_mu, covariance_annual, risk_free_rate, asset_count),
        annual_mu,
        covariance_annual,
        risk_free_rate,
        gamma,
        max_cash_weight,
    )


def _unconstrained_risky_allocation(
    risky_weights: np.ndarray,
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    risk_free_rate: float,
    gamma: float,
) -> float:
    risky_return = float(annual_mu @ risky_weights)
    risky_variance = float(risky_weights.T @ covariance_annual @ risky_weights)
    if risky_variance <= 1e-10:
        return 1.0
    return float((risky_return - risk_free_rate) / (max(1.0, gamma) * risky_variance))


def _current_total_market_value(req: PortfolioAnalyzeRequest) -> float:
    risky_values = [
        max(0.0, holding.quantity * (holding.current_price or holding.avg_price))
        for holding in req.holdings
    ]
    cash_values = [cash.amount for cash in req.cash_positions if cash.amount > 0]
    return float(sum(risky_values) + sum(cash_values))


def _theoretical_utility_portfolio(
    req: PortfolioAnalyzeRequest,
    eligible_results: list[Feature3PriceSeriesResult],
    max_sharpe_risky_weights: np.ndarray,
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    mu_with_cash: np.ndarray,
    raw_mu_with_cash: np.ndarray,
    target_volatility: float,
    risk_free_rate: float,
    gamma: float,
) -> Feature3PortfolioResult | None:
    risky_allocation = _unconstrained_risky_allocation(
        max_sharpe_risky_weights,
        annual_mu,
        covariance_annual,
        risk_free_rate,
        gamma,
    )
    min_risky_allocation = max(0.0, 1.0 - _resolved_max_cash_weight(req, gamma))

    if risky_allocation <= 0.000001:
        return None
    if min_risky_allocation - 0.000001 <= risky_allocation <= 1.000001:
        return None

    total_value = _current_total_market_value(req)
    additional_required_cash = max(0.0, total_value * (risky_allocation - 1.0))
    constraint_binding = "RISKY_MAX" if risky_allocation > 1.0 else "CASH_MAX"

    asset_count = len(eligible_results)
    theoretical_weights = np.concatenate([max_sharpe_risky_weights * risky_allocation, np.array([1.0 - risky_allocation])])
    if risky_allocation > 1.0:
        display_weights = np.concatenate([max_sharpe_risky_weights, np.array([0.0])])
    else:
        display_weights = np.concatenate([max_sharpe_risky_weights * risky_allocation, np.array([1.0 - risky_allocation])])
    risky_weights = theoretical_weights[:asset_count]
    volatility = round(float(np.sqrt(max(risky_weights.T @ covariance_annual @ risky_weights, 0.0))), 6)
    expected_return = round(float(mu_with_cash @ theoretical_weights), 6)
    raw_historical_return = round(float(raw_mu_with_cash @ theoretical_weights), 6)
    display_expected_return = _display_expected_return(expected_return)
    weight_items = [
        Feature3Weight(
            stock_code=result.stock_code,
            company_name=result.company_name,
            asset_type="EQUITY",
            weight=round(float(display_weights[idx]), 6),
        )
        for idx, result in enumerate(eligible_results)
        if display_weights[idx] > 0.000001
    ]
    if display_weights[-1] > 0.000001:
        weight_items.append(
            Feature3Weight(
                stock_code="CASH_KRW",
                company_name="KRW 현금",
                asset_type="CASH",
                weight=round(float(display_weights[-1]), 6),
            )
        )
    risk_contributions = _risk_contributions_from_covariance(
        eligible_results,
        risky_weights,
        covariance_annual,
        volatility,
    )
    return Feature3PortfolioResult(
        type="THEORETICAL_UTILITY",
        label="Theoretical Utility",
        expected_return=expected_return,
        raw_historical_return=raw_historical_return,
        display_expected_return=display_expected_return,
        is_display_capped=_is_display_capped(expected_return, display_expected_return),
        additional_required_cash=round(float(additional_required_cash), 2),
        theoretical_risky_allocation=round(float(risky_allocation), 6),
        constraint_binding=constraint_binding,
        sharpe_ratio=_sharpe_ratio(expected_return, volatility, risk_free_rate),
        volatility=volatility,
        target_volatility=target_volatility,
        achieved_volatility=volatility,
        risk_level=_risk_level(volatility, target_volatility),
        optimization_status="NEAREST_FEASIBLE",
        weights=weight_items,
        risk_contributions=risk_contributions,
        user_description=(
            "추가입금이 필요한 이론적 효용접점입니다. 표시 비중은 추가입금 후 위험자산 구성 기준입니다."
            if constraint_binding == "RISKY_MAX"
            else "현금 한도 제약보다 더 높은 현금 비중이 필요한 이론적 효용접점입니다."
        ),
    )


def _efficient_frontier_points(
    annual_mu: np.ndarray,
    covariance_annual: np.ndarray,
    asset_count: int,
    risk_free_rate: float,
    raw_historical_annual_mu: np.ndarray | None = None,
    display_annual_mu: np.ndarray | None = None,
) -> list[dict]:
    frontier: list[dict] = []
    min_mu, max_mu = _feasible_return_range(annual_mu, asset_count)
    targets = np.linspace(min_mu, max_mu, num=FRONTIER_POINT_COUNT)
    x0 = _equal_risky_weights(asset_count)
    bounds = [(0.0, _risky_max_weight(asset_count)) for _ in range(asset_count)]

    for target_return in targets:
        def objective(weights: np.ndarray) -> float:
            return float(weights.T @ covariance_annual @ weights)

        constraints = [
            {"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)},
            {"type": "eq", "fun": lambda weights, target=target_return: float(annual_mu @ weights - target)},
        ]
        try:
            from scipy.optimize import minimize

            result = minimize(
                objective,
                x0,
                method="SLSQP",
                bounds=bounds,
                constraints=constraints,
                options={"maxiter": 250, "ftol": 1e-10},
            )
            if not result.success or not _risky_weights_satisfy_constraints(result.x):
                continue
            weights = np.clip(result.x, 0.0, None)
            weights = weights / float(np.sum(weights))
            volatility = float(np.sqrt(max(weights.T @ covariance_annual @ weights, 0.0)))
            expected_return = float(annual_mu @ weights)
            raw_historical_return = float(raw_historical_annual_mu @ weights) if raw_historical_annual_mu is not None else expected_return
            display_expected_return = _display_expected_return(expected_return)
            frontier.append({
                "volatility": round(volatility, 6),
                "expectedReturn": round(expected_return, 6),
                "rawHistoricalReturn": round(raw_historical_return, 6),
                "displayExpectedReturn": round(display_expected_return, 6),
                "isDisplayCapped": _is_display_capped(expected_return, display_expected_return),
                "sharpeRatio": _sharpe_ratio(expected_return, volatility, risk_free_rate),
            })
        except Exception:
            continue
    return _upper_envelope(frontier)


def _portfolio_from_weight_vector(
    portfolio_type: str,
    label: str,
    eligible_results: list[Feature3PriceSeriesResult],
    weights: np.ndarray,
    covariance_annual: np.ndarray,
    mu_with_cash: np.ndarray,
    raw_mu_with_cash: np.ndarray,
    display_mu_with_cash: np.ndarray,
    target_volatility: float,
    risk_free_rate: float,
) -> Feature3PortfolioResult:
    asset_count = len(eligible_results)
    risky_weights = weights[:asset_count]
    volatility = round(float(np.sqrt(max(weights.T @ _with_cash_covariance(covariance_annual) @ weights, 0.0))), 6)
    weight_items = [
        Feature3Weight(
            stock_code=result.stock_code,
            company_name=result.company_name,
            asset_type="EQUITY",
            weight=round(float(weights[idx]), 6),
        )
        for idx, result in enumerate(eligible_results)
        if weights[idx] > 0.000001
    ]
    if weights[-1] > 0.000001:
        weight_items.append(
            Feature3Weight(
                stock_code="CASH_KRW",
                company_name="KRW 현금",
                asset_type="CASH",
                weight=round(float(weights[-1]), 6),
            )
        )
    risk_contributions = _risk_contributions_from_covariance(
        eligible_results,
        risky_weights,
        covariance_annual,
        volatility,
    )
    if weights[-1] > 0.000001:
        risk_contributions.append(
            Feature3RiskContribution(
                stock_code="CASH_KRW",
                company_name="KRW 현금",
                weight=round(float(weights[-1]), 6),
                volatility=0.0,
                marginal_risk_contribution=0.0,
                risk_contribution=0.0,
                risk_contribution_pct=0.0,
            )
        )
    expected_return = round(float(mu_with_cash @ weights), 6)
    raw_historical_return = round(float(raw_mu_with_cash @ weights), 6)
    display_expected_return = _display_expected_return(expected_return)
    return Feature3PortfolioResult(
        type=portfolio_type,
        label=label,
        expected_return=expected_return,
        raw_historical_return=raw_historical_return,
        display_expected_return=display_expected_return,
        is_display_capped=_is_display_capped(expected_return, display_expected_return),
        sharpe_ratio=_sharpe_ratio(expected_return, volatility, risk_free_rate),
        volatility=volatility,
        target_volatility=target_volatility,
        achieved_volatility=volatility,
        risk_level=_risk_level(volatility, target_volatility),
        optimization_status="SUCCESS",
        weights=weight_items,
        risk_contributions=risk_contributions,
        user_description="Advanced 검증용 기대수익률/무위험수익률 기반 최적화 포트폴리오입니다.",
    )


def _feasible_return_range(annual_mu: np.ndarray, asset_count: int) -> tuple[float, float]:
    if asset_count <= 0:
        return 0.0, 0.0
    return (
        float(annual_mu @ _greedy_return_extreme_weights(annual_mu, asset_count, ascending=True)),
        float(annual_mu @ _greedy_return_extreme_weights(annual_mu, asset_count, ascending=False)),
    )


def _greedy_return_extreme_weights(annual_mu: np.ndarray, asset_count: int, ascending: bool) -> np.ndarray:
    weights = np.zeros(asset_count, dtype=float)
    remaining = 1.0
    risky_max = _risky_max_weight(asset_count)
    order = np.argsort(annual_mu)
    if not ascending:
        order = order[::-1]
    for idx in order:
        add = min(risky_max, remaining)
        weights[idx] = add
        remaining -= add
        if remaining <= 1e-9:
            break
    if remaining > 1e-6:
        weights += remaining / asset_count
    return weights / float(np.sum(weights))


def _upper_envelope(points: list[dict]) -> list[dict]:
    best_by_volatility: dict[float, dict] = {}
    for point in points:
        volatility_key = round(float(point["volatility"]), 4)
        previous = best_by_volatility.get(volatility_key)
        if previous is None or point["expectedReturn"] > previous["expectedReturn"]:
            best_by_volatility[volatility_key] = point

    ordered = sorted(best_by_volatility.values(), key=lambda point: (point["volatility"], -point["expectedReturn"]))
    envelope: list[dict] = []
    best_return = -float("inf")
    for point in ordered:
        expected_return = point["expectedReturn"]
        if expected_return > best_return + 1e-6:
            envelope.append(point)
            best_return = expected_return
    deduped: list[dict] = []
    seen = set()
    for point in envelope:
        key = (point["volatility"], point["expectedReturn"])
        if key not in seen:
            seen.add(key)
            deduped.append(point)
    return deduped


def _equal_risky_weights(asset_count: int) -> np.ndarray:
    weights = np.full(asset_count, 1.0 / max(asset_count, 1), dtype=float)
    risky_max = _risky_max_weight(asset_count)
    weights = np.minimum(weights, risky_max)
    deficit = 1.0 - float(np.sum(weights))
    idx = 0
    while deficit > 1e-9 and idx < asset_count * 2:
        target = idx % asset_count
        room = risky_max - weights[target]
        add = min(room, deficit)
        weights[target] += add
        deficit -= add
        idx += 1
    return weights / float(np.sum(weights))


def _inverse_volatility_weights(covariance_annual: np.ndarray, asset_count: int) -> np.ndarray:
    diagonal = np.sqrt(np.maximum(np.diag(covariance_annual), 0.0))
    inverse = np.divide(
        1.0,
        diagonal,
        out=np.zeros_like(diagonal, dtype=float),
        where=(diagonal > 1e-9) & np.isfinite(diagonal),
    )
    if inverse.size != asset_count or float(np.sum(inverse)) <= 1e-12:
        return _equal_risky_weights(asset_count)
    raw = inverse / float(np.sum(inverse))
    return _cap_and_normalize_risky_weights(raw, asset_count)


def _cap_and_normalize_risky_weights(raw_weights: np.ndarray, asset_count: int) -> np.ndarray:
    cap = _risky_max_weight(asset_count)
    weights = np.clip(np.asarray(raw_weights, dtype=float), 0.0, cap)
    if weights.size != asset_count or not np.isfinite(weights).all() or float(np.sum(weights)) <= 1e-12:
        return _equal_risky_weights(asset_count)
    for _ in range(asset_count * 3):
        deficit = 1.0 - float(np.sum(weights))
        if abs(deficit) <= 1e-10:
            break
        room = np.maximum(cap - weights, 0.0)
        room_sum = float(np.sum(room))
        if deficit <= 0 or room_sum <= 1e-12:
            break
        weights = np.minimum(cap, weights + room / room_sum * deficit)
    if float(np.sum(weights)) <= 1e-12:
        return _equal_risky_weights(asset_count)
    return weights / float(np.sum(weights))


def _risky_max_weight(asset_count: int) -> float:
    if asset_count <= 1:
        return 1.0
    return max(MAX_ASSET_WEIGHT, min(0.75, 2.0 / asset_count))


def _risky_weights_satisfy_constraints(weights: np.ndarray) -> bool:
    if not np.isfinite(weights).all():
        return False
    if abs(float(np.sum(weights)) - 1.0) > 1e-4:
        return False
    if np.any(weights < -1e-6) or np.any(weights > _risky_max_weight(len(weights)) + 1e-6):
        return False
    return True


def _with_cash_covariance(covariance_annual: np.ndarray) -> np.ndarray:
    dimension = covariance_annual.shape[0] + 1
    out = np.zeros((dimension, dimension), dtype=float)
    out[:-1, :-1] = covariance_annual
    return out


def _clamp_float(value: float, minimum: float, maximum: float) -> float:
    return min(maximum, max(minimum, float(value)))


def _feasible_volatility_range_with_cash(
    covariance_annual: np.ndarray,
    asset_count: int,
    max_cash_weight: float,
) -> tuple[float, float]:
    covariance_with_cash = _with_cash_covariance(covariance_annual)
    dimension = asset_count + 1
    risky_prior = _inverse_volatility_weights(covariance_annual, asset_count)
    x0 = np.concatenate([
        risky_prior * (1.0 - max_cash_weight),
        np.array([max_cash_weight]),
    ])
    x0 = x0 / float(np.sum(x0) or 1.0)
    bounds = [(0.0, _risky_max_weight(asset_count)) for _ in range(asset_count)] + [(0.0, max_cash_weight)]
    constraints = [{"type": "eq", "fun": lambda weights: float(np.sum(weights) - 1.0)}]

    def variance(weights: np.ndarray) -> float:
        return float(weights.T @ covariance_with_cash @ weights)

    min_weights = x0
    try:
        from scipy.optimize import minimize

        result = minimize(
            variance,
            x0,
            method="SLSQP",
            bounds=bounds,
            constraints=constraints,
            options={"maxiter": 300, "ftol": 1e-10},
        )
        if result.success and _weights_satisfy_constraints(result.x, asset_count, max_cash_weight):
            min_weights = np.clip(result.x, 0.0, None)
            min_weights = min_weights / float(np.sum(min_weights) or 1.0)
    except Exception:
        pass

    max_weights = _high_volatility_weights_with_cash(covariance_annual, asset_count)
    rng = np.random.default_rng(7)
    for _ in range(1500):
        raw_assets = rng.random(asset_count)
        raw_assets = raw_assets / float(np.sum(raw_assets) or 1.0)
        asset_budget = rng.uniform(max(0.15, 1.0 - max_cash_weight), 1.0)
        asset_weights = raw_assets * asset_budget
        risky_max = _risky_max_weight(asset_count)
        if float(np.max(asset_weights)) > risky_max:
            asset_weights = np.minimum(asset_weights, risky_max)
            asset_weights = asset_weights / float(np.sum(asset_weights) or 1.0) * min(asset_budget, risky_max * asset_count)
        cash_weight = max(0.0, 1.0 - float(np.sum(asset_weights)))
        if cash_weight > max_cash_weight:
            continue
        weights = np.concatenate([asset_weights, np.array([cash_weight])])
        weights = weights / float(np.sum(weights) or 1.0)
        if _weights_satisfy_constraints(weights, asset_count, max_cash_weight) and variance(weights) > variance(max_weights):
            max_weights = weights

    return (
        float(np.sqrt(max(variance(min_weights), 0.0))),
        float(np.sqrt(max(variance(max_weights), 0.0))),
    )


def _high_volatility_weights_with_cash(covariance_annual: np.ndarray, asset_count: int) -> np.ndarray:
    weights = np.zeros(asset_count + 1, dtype=float)
    remaining = 1.0
    cap = _risky_max_weight(asset_count)
    order = np.argsort(np.diag(covariance_annual))[::-1]
    for idx in order:
        add = min(cap, remaining)
        weights[idx] = add
        remaining -= add
        if remaining <= 1e-9:
            break
    if remaining > 1e-9:
        weights[:asset_count] += remaining / asset_count
    return weights / float(np.sum(weights) or 1.0)


def _weights_satisfy_constraints(weights: np.ndarray, asset_count: int, max_cash_weight: float) -> bool:
    if not np.isfinite(weights).all():
        return False
    if abs(float(np.sum(weights)) - 1.0) > 1e-4:
        return False
    if np.any(weights[:asset_count] < -1e-6) or np.any(weights[:asset_count] > _risky_max_weight(asset_count) + 1e-6):
        return False
    if weights[asset_count] < -1e-6 or weights[asset_count] > max_cash_weight + 1e-6:
        return False
    return True


def _grid_candidate_search(
    covariance_with_cash: np.ndarray,
    target_volatility: float,
    asset_count: int,
    max_cash_weight: float,
) -> np.ndarray:
    # scipy 실패 시에도 제약을 만족하는 후보를 보장하기 위한 제한적 random search fallback.
    rng = np.random.default_rng(42)
    covariance_annual = covariance_with_cash[:asset_count, :asset_count]
    risky_prior = _inverse_volatility_weights(covariance_annual, asset_count)
    best_weights = None
    best_score = float("inf")
    for _ in range(2500):
        raw_assets = rng.random(asset_count)
        raw_assets = raw_assets / float(np.sum(raw_assets) or 1.0)
        asset_budget = rng.uniform(max(0.15, 1.0 - max_cash_weight), 1.0)
        asset_weights = raw_assets * asset_budget
        risky_max_weight = _risky_max_weight(asset_count)
        if float(np.max(asset_weights)) > risky_max_weight:
            asset_weights = np.minimum(asset_weights, risky_max_weight)
            asset_weights = asset_weights / float(np.sum(asset_weights) or 1.0) * min(asset_budget, risky_max_weight * asset_count)
        cash_weight = max(0.0, 1.0 - float(np.sum(asset_weights)))
        if cash_weight > max_cash_weight:
            continue
        weights = np.concatenate([asset_weights, np.array([cash_weight])])
        weights = weights / float(np.sum(weights))
        if not _weights_satisfy_constraints(weights, asset_count, max_cash_weight):
            continue
        variance = float(weights.T @ covariance_with_cash @ weights)
        volatility = float(np.sqrt(max(variance, 0.0)))
        score = abs(volatility - target_volatility) + 0.05 * float(np.sum((weights[:asset_count] - risky_prior * float(np.sum(weights[:asset_count]))) ** 2))
        if score < best_score:
            best_score = score
            best_weights = weights
    if best_weights is not None:
        return best_weights

    fallback = np.zeros(asset_count + 1, dtype=float)
    fallback[:asset_count] = risky_prior
    fallback[-1] = max(0.0, 1.0 - float(np.sum(fallback[:asset_count])))
    if fallback[-1] > max_cash_weight:
        fallback[-1] = max_cash_weight
    return fallback / float(np.sum(fallback))


def _nearest_feasible_from_weights(
    portfolio_type: str,
    label: str,
    target_volatility: float,
    weights: list[Feature3Weight],
    max_cash_weight: float,
) -> Feature3PortfolioResult:
    fallback_weights = _fallback_profile_weights(portfolio_type, weights, max_cash_weight)
    return Feature3PortfolioResult(
        type=portfolio_type,
        label=label,
        volatility=target_volatility,
        target_volatility=target_volatility,
        achieved_volatility=target_volatility,
        risk_level="MID",
        optimization_status="NEAREST_FEASIBLE",
        weights=fallback_weights,
        risk_contributions=_dummy_risk_contributions(fallback_weights),
        user_description=f"{label}은 가격 데이터 부족으로 현재 종목 구성과 현금 한도를 기준으로 표시됩니다.",
    )


def _fallback_profile_weights(
    portfolio_type: str,
    weights: list[Feature3Weight],
    max_cash_weight: float,
) -> list[Feature3Weight]:
    risky_items = [weight for weight in weights if weight.asset_type != "CASH"]
    cash_items = [weight for weight in weights if weight.asset_type == "CASH"]
    risky_total = sum(max(0.0, weight.weight) for weight in risky_items)
    if risky_total <= 1e-12:
        return weights

    cash_target_by_type = {
        "STABLE": max_cash_weight,
        "BALANCED": max_cash_weight * 0.5,
        "AGGRESSIVE": 0.0,
        "PSYCHOLOGICAL": max_cash_weight,
    }
    target_cash = round(max(0.0, min(max_cash_weight, cash_target_by_type.get(portfolio_type, 0.0))), 6)
    target_risky = max(0.0, 1.0 - target_cash)
    scaled = [
        Feature3Weight(
            stock_code=weight.stock_code,
            company_name=weight.company_name,
            asset_type=weight.asset_type,
            weight=round(float(weight.weight / risky_total * target_risky), 6),
        )
        for weight in risky_items
        if weight.weight > 0
    ]
    if target_cash > 0:
        cash_code = cash_items[0].stock_code if cash_items else "CASH_KRW"
        cash_name = cash_items[0].company_name if cash_items else "KRW 현금"
        scaled.append(
            Feature3Weight(
                stock_code=cash_code,
                company_name=cash_name,
                asset_type="CASH",
                weight=target_cash,
            )
        )
    return scaled


def _core_warning_if_dummy(current: Feature3PortfolioResult) -> list[Feature3Warning]:
    if current.optimization_status != "NEAREST_FEASIBLE":
        return []
    return [
        Feature3Warning(
            code="CORE_RISK_PARTIAL_FALLBACK",
            message=current.user_description or "Core risk calculation used a partial fallback.",
            user_message="가격 데이터가 부족해 일부 결과는 임시 기준으로 표시됩니다.",
            severity="WARN",
            target="currentPortfolio",
        )
    ]


def _covariance_warnings(req: PortfolioAnalyzeRequest, risk_context: dict) -> list[Feature3Warning]:
    if req.options.covariance_model != "LEDOIT_WOLF" or risk_context["used_model"] != "SAMPLE_COVARIANCE":
        return []
    return [
        Feature3Warning(
            code="SAMPLE_COVARIANCE_FALLBACK",
            message="Ledoit-Wolf covariance failed and sample covariance was used as fallback.",
            user_message="가격 움직임 계산 중 안정화 모델을 적용하지 못해 대체 계산값을 사용했습니다.",
            severity="WARN",
            target="advanced.covarianceDiagnostics",
        )
    ]


def _risk_context_warnings(risk_context: dict) -> list[Feature3Warning]:
    reason = risk_context.get("fallback_reason")
    if not reason:
        return []

    common_missing_rate = float(risk_context.get("common_missing_rate") or 1.0)
    common_price_count = int(risk_context.get("common_price_count") or 0)
    messages = {
        "NO_ELIGIBLE_HOLDINGS": "No holding satisfied per-symbol price quality thresholds.",
        "HIGH_COMMON_MISSING_RATE": (
            f"Common date missing rate {common_missing_rate:.2%} exceeded "
            f"threshold {MAX_COMMON_MISSING_RATE:.2%}."
        ),
        "INSUFFICIENT_COMMON_OBSERVATIONS": (
            f"Only {common_price_count} common price observations were available; "
            f"minimum is {MIN_OBSERVATIONS}."
        ),
        "INSUFFICIENT_COMMON_RETURN_SAMPLE": "Common return sample was too short for covariance calculation.",
        "INVALID_COMMON_RETURN_SAMPLE": "Common return sample contained invalid values.",
    }
    user_messages = {
        "NO_ELIGIBLE_HOLDINGS": "가격 데이터 품질 기준을 충족한 종목이 없어 현재 입력 비중 기반으로 표시됩니다.",
        "HIGH_COMMON_MISSING_RATE": (
            f"보유 종목의 공통 거래일 결측률이 {common_missing_rate:.1%}로 "
            f"허용 기준 {MAX_COMMON_MISSING_RATE:.0%}를 초과해 현재 입력 비중 기반으로 표시됩니다."
        ),
        "INSUFFICIENT_COMMON_OBSERVATIONS": (
            f"보유 종목의 공통 가격 관측치가 {common_price_count}개로 "
            f"최소 기준 {MIN_OBSERVATIONS}개보다 적어 현재 입력 비중 기반으로 표시됩니다."
        ),
        "INSUFFICIENT_COMMON_RETURN_SAMPLE": "공통 수익률 표본이 부족해 현재 입력 비중 기반으로 표시됩니다.",
        "INVALID_COMMON_RETURN_SAMPLE": "공통 수익률 표본에 유효하지 않은 값이 있어 현재 입력 비중 기반으로 표시됩니다.",
    }
    return [
        Feature3Warning(
            code=f"CORE_RISK_FALLBACK_{reason}",
            message=messages.get(reason, str(reason)),
            user_message=user_messages.get(reason, "가격 데이터가 부족해 현재 입력 비중 기반으로 표시됩니다."),
            severity="WARN",
            target="advanced.covarianceDiagnostics",
        )
    ]


def _build_risk_drivers(
    current: Feature3PortfolioResult,
    suitability: str,
) -> list[Feature3RiskDriver]:
    # 현재 구현: LLM 없이 deterministic metric만으로 Basic 설명 재료를 만든다.
    drivers: list[Feature3RiskDriver] = []
    severity = current.risk_level

    if suitability == "AGGRESSIVE_THAN_PROFILE":
        drivers.append(
            Feature3RiskDriver(
                code="ABOVE_TARGET_VOLATILITY",
                severity=severity,
                title="성향 대비 높은 변동 위험",
                description="현재 구성의 가격 변동 폭이 투자 성향 기준보다 큽니다.",
                affected_holdings=[weight.stock_code for weight in current.weights if weight.asset_type != "CASH"],
            )
        )
    elif suitability == "CONSERVATIVE_THAN_PROFILE":
        drivers.append(
            Feature3RiskDriver(
                code="BELOW_TARGET_VOLATILITY",
                severity="LOW",
                title="성향 대비 낮은 변동 위험",
                description="현재 구성은 투자 성향 기준보다 안정적으로 움직이는 편입니다.",
                affected_holdings=[weight.stock_code for weight in current.weights if weight.asset_type != "CASH"],
            )
        )

    risky_contributions = [
        contribution
        for contribution in current.risk_contributions
        if contribution.stock_code != "CASH_KRW" and contribution.risk_contribution_pct is not None
    ]
    if risky_contributions:
        top = max(risky_contributions, key=lambda contribution: contribution.risk_contribution_pct)
        if top.risk_contribution_pct >= 0.35:
            drivers.append(
                Feature3RiskDriver(
                    code="HIGH_RISK_CONTRIBUTION",
                    severity="HIGH" if top.risk_contribution_pct >= 0.50 else "MID",
                    title="특정 종목 위험 기여 집중",
                    description=f"{top.company_name or top.stock_code} 비중이 전체 위험에서 차지하는 몫이 큽니다.",
                    affected_holdings=[top.stock_code],
                )
            )

    if not drivers:
        drivers.append(
            Feature3RiskDriver(
                code="PROFILE_ALIGNED",
                severity="MID",
                title="성향 기준에 가까운 구성",
                description="현재 포트폴리오의 변동 위험이 투자 성향 기준과 크게 벗어나지 않습니다.",
                affected_holdings=[weight.stock_code for weight in current.weights if weight.asset_type != "CASH"],
            )
        )
    return drivers


def _target_volatility_warning(portfolio: Feature3PortfolioResult) -> Feature3Warning:
    return Feature3Warning(
        code="TARGET_VOLATILITY_INFEASIBLE",
        message=f"{portfolio.type} target volatility was not exactly feasible under current constraints.",
        user_message=f"{portfolio.label}은 현재 제약조건에서 목표 위험에 가장 가까운 구성으로 표시됩니다.",
        severity="WARN",
        target=portfolio.type,
    )


def _common_sample_size(price_results: list[Feature3PriceSeriesResult]) -> int:
    eligible = [
        result
        for result in price_results
        if result.available_price_count >= MIN_OBSERVATIONS
        and result.missing_rate <= MAX_MISSING_RATE
        and len(result.points) >= MIN_OBSERVATIONS
    ]
    if not eligible:
        return 0
    return max(0, len(_common_dates(eligible)) - 1)


def _excluded_holdings(price_results: list[Feature3PriceSeriesResult]) -> list[Feature3ExcludedHolding]:
    excluded: list[Feature3ExcludedHolding] = []
    for result in price_results:
        reason = None
        if any(point.close <= 0 or not np.isfinite(point.close) for point in result.points):
            reason = "INVALID_PRICE_SERIES"
        elif result.available_price_count < MIN_OBSERVATIONS:
            reason = "INSUFFICIENT_OBSERVATIONS"
        elif result.missing_rate > MAX_MISSING_RATE:
            reason = "HIGH_MISSING_RATE"

        if reason is None:
            continue

        excluded.append(
            Feature3ExcludedHolding(
                stock_code=result.stock_code,
                company_name=result.company_name,
                reason=reason,
                available_price_count=result.available_price_count,
                expected_trading_day_count=result.expected_trading_day_count,
                missing_rate=result.missing_rate,
            )
        )
    return excluded


def _excluded_holding_warnings(excluded_holdings: list[Feature3ExcludedHolding]) -> list[Feature3Warning]:
    return [
        Feature3Warning(
            code=f"HOLDING_EXCLUDED_{holding.reason}",
            message=f"{holding.stock_code} was excluded from covariance calculation: {holding.reason}",
            user_message="일부 종목은 가격 데이터 품질 기준을 충족하지 못해 핵심 리스크 계산에서 제외됐습니다.",
            severity="WARN",
            target=holding.stock_code,
        )
        for holding in excluded_holdings
    ]


def _warning_codes(warnings: list[Feature3Warning]) -> list[str]:
    return list(dict.fromkeys(warning.code for warning in warnings if warning.code))


def _portfolio_price_basis(
    requested_price_basis: str,
    eligible_results: list[Feature3PriceSeriesResult],
    all_results: list[Feature3PriceSeriesResult],
) -> str:
    if requested_price_basis != "ADJUSTED_CLOSE":
        return "RAW_CLOSE"

    basis_values = {
        result.used_price_basis
        for result in (eligible_results or all_results)
        if result.used_price_basis in {"YAHOO_ADJ_CLOSE", "RAW_CLOSE"}
    }
    if basis_values == {"YAHOO_ADJ_CLOSE"}:
        return "YAHOO_ADJ_CLOSE"
    if basis_values == {"YAHOO_ADJ_CLOSE", "RAW_CLOSE"}:
        return "YAHOO_ADJ_CLOSE_WITH_KIS_FALLBACK"
    return "RAW_CLOSE"


def _dedupe_warnings(warnings: list[Feature3Warning]) -> list[Feature3Warning]:
    seen: set[tuple[str, str | None]] = set()
    deduped: list[Feature3Warning] = []
    for warning in warnings:
        key = (warning.code, warning.target)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(warning)
    return deduped
