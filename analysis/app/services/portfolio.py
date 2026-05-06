from __future__ import annotations

import math

from app.models.feature3 import PortfolioAnalyzeRequest, PortfolioAnalyzeResponse


def analyze_portfolio(req: PortfolioAnalyzeRequest) -> PortfolioAnalyzeResponse:
    total_value = sum(item.quantity * item.avg_price for item in req.holdings)
    weights = [
        (item.quantity * item.avg_price) / total_value
        for item in req.holdings
    ]

    concentration = max(weights)
    hhi = sum(weight * weight for weight in weights)
    diversification = _diversification_label(concentration, len(weights))
    covariance_score = round(min(1.0, hhi), 2)

    option_count = len(set(req.options or []))
    risk_gamma = 0.5 if req.risk_gamma is None else req.risk_gamma
    volatility = _estimate_volatility(concentration, len(weights), option_count)
    gamma = round(min(1.0, max(0.0, risk_gamma)), 3)
    risk_level = _risk_level(volatility, gamma)
    efficiency = "Efficient" if covariance_score <= 0.55 and volatility <= 24 else "Inefficient"

    return PortfolioAnalyzeResponse(
        risk_level=risk_level,
        volatility=volatility,
        diversification=diversification,
        covariance_score=covariance_score,
        efficiency=efficiency,
        gamma=gamma,
    )


def _estimate_volatility(concentration: float, holding_count: int, option_count: int) -> float:
    diversification_discount = min(8.0, math.log2(max(holding_count, 1)) * 2.2)
    option_adjustment = min(3.0, option_count * 0.35)
    value = 14.0 + concentration * 18.0 - diversification_discount + option_adjustment
    return round(max(5.0, min(45.0, value)), 1)


def _risk_level(volatility: float, gamma: float) -> str:
    adjusted = volatility * (1.15 - gamma * 0.3)
    if adjusted < 16:
        return "Low"
    if adjusted < 27:
        return "Mid"
    return "High"


def _diversification_label(concentration: float, holding_count: int) -> str:
    if holding_count >= 5 and concentration <= 0.35:
        return "분산됨"
    if concentration >= 0.6 or holding_count <= 2:
        return "집중됨"
    return "보통"
