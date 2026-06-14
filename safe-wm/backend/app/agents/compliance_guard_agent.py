from __future__ import annotations

from app.agents.state import add_agent_trace


FORBIDDEN_PHRASES = [
    "매수하세요",
    "매도하세요",
    "반드시 상승",
    "반드시 하락",
    "수익을 보장",
    "원금이 보장",
    "추천합니다",
    "투자해야 합니다",
]


def run_compliance_guard(state: dict, customer_explanation: dict, pb_explanation: dict) -> dict:
    text = f"{customer_explanation.get('text', '')}\n{pb_explanation.get('text', '')}"
    violations = [phrase for phrase in FORBIDDEN_PHRASES if phrase in text]
    status = "FAIL" if violations else "PASS"
    result = {
        "status": status,
        "violations": violations,
        "checkedItems": [
            "investment_recommendation_expression",
            "definitive_expression",
            "guaranteed_return_expression",
            "personal_information_exposure",
        ],
        "message": "금지 표현이 감지되어 재생성이 필요합니다." if violations else "투자권유 및 확정 표현 없음",
    }
    add_agent_trace(
        state,
        "ComplianceGuardAgent",
        "RETRY" if violations else "PASS",
        result["message"],
        violations,
    )
    return result

