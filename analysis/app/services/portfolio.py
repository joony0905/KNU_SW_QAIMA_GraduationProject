from __future__ import annotations

from datetime import datetime, timezone
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
from app.services.feature3_price_data import Feature3PriceSeriesResult, fetch_feature3_price_series

MIN_OBSERVATIONS = 120
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


def analyze_portfolio(req: PortfolioAnalyzeRequest) -> PortfolioAnalyzeResponse:
    score = req.risk_profile.risk_tolerance_score
    gamma = round(req.risk_profile.risk_aversion_gamma or (10 - 9 * score), 4)
    profile_type = req.risk_profile.profile_type or _profile_type(score)
    target_volatility = round(req.risk_profile.target_volatility or (0.08 + 0.12 * score), 4)
    risk_free_rate = _risk_free_rate(req)
    cash_limit = _resolved_max_cash_weight(req, gamma)
    low_target_volatility = round(max(target_volatility * 0.70, 0.06), 4)
    high_target_volatility = round(min(target_volatility * 1.35, 0.25), 4)

    price_results = [
        fetch_feature3_price_series(
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

    # 현재 구현: 가격 시계열이 충분하면 실제 Ledoit-Wolf 기반 CURRENT 리스크를 계산한다.
    # 진행 예정: Phase 9에서 Feature1/2 overlay를 이 core 계산과 분리된 설명 레이어로 붙인다.
    risk_context = _build_risk_context(req, price_results, risk_free_rate)
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
            + _excluded_holding_warnings(excluded_holdings)
            + candidate_warnings
            + advanced_warnings
        )
        if warning is not None
    ]
    risk_drivers = _build_risk_drivers(current, suitability)

    now = datetime.now(timezone.utc).isoformat()
    policy = Feature3PolicyEcho(
        price_policy=Feature3PricePolicy(
            requested=req.options.price_basis,
            used="CLOSE" if any(result.used_price_basis == "CLOSE" for result in price_results) else req.options.price_basis,
            warnings=_warning_codes(price_warnings),
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


def _build_risk_context(
    req: PortfolioAnalyzeRequest,
    price_results: list[Feature3PriceSeriesResult],
    risk_free_rate: float,
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
        "display_annual_mu": display_annual_mu,
        "is_display_capped_by_asset": is_display_capped_by_asset,
        "expected_return_estimation": expected_return_estimation["policy"],
    }


def _common_dates(price_results: list[Feature3PriceSeriesResult]) -> list:
    common = None
    for result in price_results:
        dates = {point.ts.date() for point in result.points}
        common = dates if common is None else common & dates
    return sorted(common or [])


def _common_missing_rate(expected_trading_day_count: int, common_price_count: int) -> float:
    expected = max(1, int(expected_trading_day_count))
    return round(max(0.0, 1.0 - (common_price_count / expected)), 6)


def _log_return_matrix(price_results: list[Feature3PriceSeriesResult], common_dates: list) -> np.ndarray:
    close_by_result = [
        {point.ts.date(): point.close for point in result.points}
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


def _estimate_expected_returns(
    returns_matrix: np.ndarray,
    eligible_results: list[Feature3PriceSeriesResult],
    covariance_annual: np.ndarray,
    annualization_factor: int,
    risk_free_rate: float,
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
    annual_volatility = np.sqrt(np.maximum(np.diag(covariance_annual), 0.0))
    sample_size = int(returns_matrix.shape[0])
    base_confidence = _clamp_float(
        sample_size / HISTORICAL_CONFIDENCE_SAMPLE_DAYS,
        HISTORICAL_CONFIDENCE_MIN,
        HISTORICAL_CONFIDENCE_MAX,
    )
    vol_penalty = np.array([
        _clamp_float(VOLATILITY_CONFIDENCE_REFERENCE / vol, VOLATILITY_PENALTY_MIN, 1.0)
        if vol > 1e-9 and np.isfinite(vol) else VOLATILITY_PENALTY_MIN
        for vol in annual_volatility
    ], dtype=float)
    missing_penalty = np.array([
        _clamp_float(1.0 - result.missing_rate, MISSING_PENALTY_MIN, 1.0)
        for result in eligible_results
    ], dtype=float)
    historical_confidence = np.clip(base_confidence * vol_penalty * missing_penalty, 0.0, HISTORICAL_CONFIDENCE_MAX)
    prior_return = risk_free_rate + EQUITY_RISK_PREMIUM
    annual_mu = historical_confidence * winsorized_annual_mu + (1.0 - historical_confidence) * prior_return
    display_annual_mu = np.clip(annual_mu, DISPLAY_EXPECTED_RETURN_CAP_LOWER, DISPLAY_EXPECTED_RETURN_CAP_UPPER)
    is_display_capped = np.abs(display_annual_mu - annual_mu) > 1e-12

    return {
        "annual_mu": annual_mu,
        "raw_historical_annual_mu": raw_historical_annual_mu,
        "display_annual_mu": display_annual_mu,
        "is_display_capped_by_asset": is_display_capped,
        "policy": {
            "estimator": "SHRINKED_WINSORIZED_MEAN",
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
            "displayCapLower": DISPLAY_EXPECTED_RETURN_CAP_LOWER,
            "displayCapUpper": DISPLAY_EXPECTED_RETURN_CAP_UPPER,
            "hasDisplayCappedAssets": bool(np.any(is_display_capped)),
            "warning": "EXPECTED_RETURN_ESTIMATION_UNSTABLE",
        },
    }


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
    # Advanced 검증용으로 shrinked winsorized mean 기반 frontier/utility point를 계산한다.
    # 진행 예정: mu estimator가 추가되면 expected_return_policy에 estimator와 warning을 더 상세히 남긴다.
    eligible_results = risk_context["eligible_results"]
    covariance_annual = risk_context["covariance_annual"]
    annual_mu = risk_context.get("annual_mu")
    if not eligible_results or covariance_annual is None or annual_mu is None:
        return [], [], _expected_return_policy("UNAVAILABLE"), []

    warnings = [
        Feature3Warning(
            code="EXPECTED_RETURN_ESTIMATION_UNSTABLE",
            message="Advanced mean-variance outputs use historical mean returns, which are unstable.",
            user_message="고급 검증의 기대수익률은 과거 수익률을 장기 기대수익률 쪽으로 수축한 추정값이라 실제 수익 예측으로 해석하면 안 됩니다.",
            severity="WARN",
            target="advanced.expectedReturnPolicy",
        )
    ]
    if not req.options.include_frontier and req.options.view_mode != "ADVANCED":
        return [], [], _expected_return_policy("HISTORICAL_MEAN"), []

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
    utility_weights = _user_risk_allocation_weights(
        max_sharpe_risky_weights,
        annual_mu,
        covariance_annual,
        risk_free_rate,
        gamma,
        max_cash_weight,
    )
    utility = _portfolio_from_weight_vector(
        "UTILITY_OPTIMAL",
        "User Risk Allocation",
        eligible_results,
        utility_weights,
        covariance_annual,
        mu_with_cash,
        raw_mu_with_cash,
        display_mu_with_cash,
        target_volatility,
        risk_free_rate,
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
        utility,
        *([theoretical_utility] if theoretical_utility is not None else []),
    ], frontier, _expected_return_policy("SHRINKED_WINSORIZED_MEAN", risk_free_rate, req, risk_context), warnings


def _expected_return_policy(
    status: str,
    risk_free_rate: float | None = None,
    req: PortfolioAnalyzeRequest | None = None,
    risk_context: dict | None = None,
) -> dict:
    if status == "SHRINKED_WINSORIZED_MEAN" and risk_context is not None:
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
        variants.append(("OVERLAY_BALANCED", "보조 관측 균형 시나리오", selected_types, 0.18))
    if "fundamentals" in selected_types:
        variants.append(("QUALITY_TILT", "종목 건강도 시나리오", {"fundamentals"}, 0.18))
    if "technical" in selected_types:
        variants.append(("MOMENTUM_AWARE", "기술 흐름 시나리오", {"technical"}, 0.10))
    if "news" in selected_types:
        variants.append(("NEWS_GUARDED", "뉴스 리스크 시나리오", {"news"}, 0.12))
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
            half_turnover_budget=0.09,
            single_delta_cap=0.035,
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
        return 0.22
    if overlay_types == {"correlation"}:
        return 0.16
    return 0.18


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
    ordered = sorted(points, key=lambda point: (point["volatility"], point["expectedReturn"]))
    envelope: list[dict] = []
    best_return = -float("inf")
    for point in ordered:
        expected_return = point["expectedReturn"]
        if expected_return >= best_return - 1e-6:
            envelope.append(point)
            best_return = max(best_return, expected_return)
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
