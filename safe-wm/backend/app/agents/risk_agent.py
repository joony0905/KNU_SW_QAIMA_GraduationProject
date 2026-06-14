from __future__ import annotations

from app.agents.state import add_agent_trace
from app.services.asset_stability_engine import calculate_score


def run_risk_analysis_agent(
    state: dict,
    severity_map: dict[str, str],
    evidence_map: dict[str, list[dict]],
) -> dict:
    result = calculate_score(severity_map, evidence_map)
    high_or_mid = [
        (code, severity)
        for code, severity in severity_map.items()
        if severity in {"HIGH", "MID"}
    ]
    result["riskFactors"] = [
        _risk_factor(code, severity, evidence_map.get(code, []))
        for code, severity in sorted(high_or_mid, key=lambda item: {"HIGH": 0, "MID": 1}[item[1]])[:4]
    ]
    add_agent_trace(
        state,
        "RiskAnalysisAgent",
        "SUCCESS",
        f"자산 안정성 점수 {result['assetStabilityScore']}점, {result['riskGrade']} 등급 산출",
    )
    return result


def _risk_factor(code: str, severity: str, evidence: list[dict]) -> dict:
    titles = {
        "REAL_ESTATE_CONCENTRATION": "부동산 편중",
        "LIQUIDITY_SHORTAGE": "긴급 유동성 부족",
        "CASHFLOW_DEFICIT": "현금흐름 부족",
        "DEBT_INTEREST_RATE": "대출/금리 부담",
        "MARKET_VOLATILITY": "시장 변동성",
        "PENSION_SHORTFALL": "연금 안정성 부족",
    }
    descriptions = {
        "REAL_ESTATE_CONCENTRATION": "총자산 중 부동산 비중이 높아 긴급 자금이 필요할 때 유동성 부담이 커질 수 있습니다.",
        "LIQUIDITY_SHORTAGE": "현금성 자산이 생활비 대비 충분하지 않아 예상치 못한 지출에 취약할 수 있습니다.",
        "CASHFLOW_DEFICIT": "월 소득과 연금 대비 지출 및 상환 부담이 큽니다.",
        "DEBT_INTEREST_RATE": "변동금리 또는 월 상환 부담으로 금리 상승 시 현금흐름 압박이 커질 수 있습니다.",
        "MARKET_VOLATILITY": "위험 금융자산 비중이 높아 시장 변동성에 영향을 받을 수 있습니다.",
        "PENSION_SHORTFALL": "연금 및 안정 소득 기반이 생활비를 충분히 받쳐주는지 확인이 필요합니다.",
    }
    return {
        "code": code,
        "severity": severity,
        "title": titles.get(code, code),
        "description": descriptions.get(code, "자산 안정성 관점에서 확인이 필요한 항목입니다."),
        "affectedAssets": [],
        "evidence": evidence,
        "dataSource": "DERIVED",
        "confidence": "HIGH",
        "needsConfirmation": code in {"REAL_ESTATE_CONCENTRATION", "PENSION_SHORTFALL"},
    }
