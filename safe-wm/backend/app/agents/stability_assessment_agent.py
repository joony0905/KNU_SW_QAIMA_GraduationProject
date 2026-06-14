from __future__ import annotations

from app.agents.state import add_agent_trace


def assess_stability(state: dict, risk_context: dict) -> dict[str, dict]:
    assessments = {
        "REAL_ESTATE_CONCENTRATION": {
            "value": risk_context["realEstateRatio"],
            "thresholds": {"LOW": 0.35, "MID": 0.45, "HIGH": 0.60},
            "label": "부동산 비중",
            "unit": "ratio",
        },
        "LIQUIDITY_SHORTAGE": {
            "value": risk_context["cashCoverageMonths"],
            "thresholds": {"HIGH_BELOW": 12, "MID_BELOW": 18, "LOW_BELOW": 24},
            "label": "현금 커버 개월 수",
            "unit": "months",
        },
        "CASHFLOW_DEFICIT": {
            "value": risk_context["monthlySurplusAfterDebt"],
            "thresholds": {"HIGH_BELOW": 0, "MID_BELOW": 500000, "LOW_BELOW": 1000000},
            "label": "월 순현금흐름",
            "unit": "KRW",
        },
        "DEBT_INTEREST_RATE": {
            "value": max(risk_context["variableLoanRatio"], risk_context["debtPaymentRatio"]),
            "thresholds": {"LOW": 0.15, "MID": 0.25, "HIGH": 0.45},
            "label": "변동금리/상환부담",
            "unit": "ratio",
        },
        "MARKET_VOLATILITY": {
            "value": risk_context["riskyAssetRatio"],
            "thresholds": {"LOW": 0.15, "MID": 0.25, "HIGH": 0.40},
            "label": "위험 금융자산 비중",
            "unit": "ratio",
        },
        "PENSION_SHORTFALL": {
            "value": risk_context["monthlyIncomeTotal"] / risk_context.get("monthlyDebtPayment", 1)
            if risk_context.get("monthlyDebtPayment", 0) > 0
            else risk_context["monthlyIncomeTotal"],
            "thresholds": {"HIGH_BELOW": 2.0, "MID_BELOW": 3.5, "LOW_BELOW": 5.0},
            "label": "연금/소득 안정성",
            "unit": "score",
        },
    }
    add_agent_trace(state, "StabilityAssessmentAgent", "SUCCESS", "위험 항목별 안정성 평가 완료")
    return assessments
