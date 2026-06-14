from __future__ import annotations


PENALTY = {"NONE": 0, "LOW": 5, "MID": 10, "HIGH": 20}
RISK_ORDER = {"HIGH": 0, "MID": 1, "LOW": 2, "NONE": 3}


def calculate_score(severity_map: dict[str, str], evidence_map: dict[str, list[dict]]) -> dict:
    score = 100
    breakdown: list[dict] = []
    for category, severity in severity_map.items():
        penalty = PENALTY.get(severity, 0)
        next_score = max(0, score - penalty)
        breakdown.append(
            {
                "category": category,
                "severity": severity,
                "baseScore": score,
                "penalty": penalty,
                "resultScore": next_score,
                "reason": _reason(category, severity),
                "evidence": evidence_map.get(category, []),
            }
        )
        score = next_score
    risk_grade = "LOW" if score >= 80 else "MID" if score >= 60 else "HIGH"
    priority = _priority(risk_grade, severity_map)
    return {
        "assetStabilityScore": score,
        "riskGrade": risk_grade,
        "priority": priority,
        "scoreBreakdown": breakdown,
    }


def _priority(risk_grade: str, severity_map: dict[str, str]) -> str:
    if risk_grade == "HIGH":
        return "URGENT"
    if severity_map.get("CASHFLOW_DEFICIT") == "HIGH" and severity_map.get("LIQUIDITY_SHORTAGE") in {"HIGH", "MID"}:
        return "URGENT"
    if severity_map.get("DEBT_INTEREST_RATE") == "HIGH":
        return "URGENT"
    if risk_grade == "MID" or "HIGH" in severity_map.values():
        return "WATCH"
    return "NORMAL"


def _reason(category: str, severity: str) -> str:
    label = {
        "REAL_ESTATE_CONCENTRATION": "부동산 편중",
        "LIQUIDITY_SHORTAGE": "긴급 유동성",
        "CASHFLOW_DEFICIT": "현금흐름",
        "DEBT_INTEREST_RATE": "대출/금리",
        "MARKET_VOLATILITY": "시장 변동성",
        "PENSION_SHORTFALL": "연금 안정성",
    }.get(category, category)
    return f"{label} 위험이 {severity}로 분류되어 penalty가 반영되었습니다."
