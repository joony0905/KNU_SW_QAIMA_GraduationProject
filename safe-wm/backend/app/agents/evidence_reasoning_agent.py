from __future__ import annotations

from app.agents.state import add_agent_trace


def build_evidence(state: dict, assessments: dict[str, dict], severity_map: dict[str, str]) -> tuple[dict, list[dict], dict]:
    evidence_map: dict[str, list[dict]] = {}
    analysis_evidence: list[dict] = []
    for category, item in assessments.items():
        evidence = {
            "riskCategory": category,
            "label": item["label"],
            "value": round(item["value"], 4) if isinstance(item["value"], float) else item["value"],
            "unit": item["unit"],
            "reason": f"{item['label']} 지표가 {severity_map[category]} 수준으로 분류되었습니다.",
            "source": "DERIVED",
            "confidence": "HIGH",
        }
        evidence_map[category] = [evidence]
        analysis_evidence.append(evidence)

    high_codes = [code for code, severity in severity_map.items() if severity == "HIGH"]
    risk_alert = {
        "level": "HIGH" if high_codes else "MID" if "MID" in severity_map.values() else "LOW",
        "title": "우선 상담 필요" if high_codes else "정기 점검 필요",
        "message": _alert_message(high_codes),
        "triggeredBy": high_codes,
        "dataSource": "DERIVED",
        "confidence": "HIGH",
    }
    add_agent_trace(state, "EvidenceReasoningAgent", "SUCCESS", "Risk Alert와 Analysis Evidence 생성 완료")
    return evidence_map, analysis_evidence, risk_alert


def _alert_message(high_codes: list[str]) -> str:
    labels = {
        "REAL_ESTATE_CONCENTRATION": "부동산 편중",
        "LIQUIDITY_SHORTAGE": "유동성 부족",
        "CASHFLOW_DEFICIT": "현금흐름 부족",
        "DEBT_INTEREST_RATE": "대출/금리 부담",
        "MARKET_VOLATILITY": "시장 변동성",
        "PENSION_SHORTFALL": "연금 안정성",
    }
    if not high_codes:
        return "중대한 고위험 신호는 제한적이며 정기 점검이 필요합니다."
    names = ", ".join(labels.get(code, code) for code in high_codes[:3])
    return f"{names} 위험이 높게 관찰됩니다."

