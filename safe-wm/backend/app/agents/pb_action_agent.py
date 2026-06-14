from __future__ import annotations

from app.agents.state import add_agent_trace
from app.models.customer import Customer


def run_pb_action_agent(state: dict, customer: Customer, risk_result: dict) -> list[dict]:
    priority = risk_result["priority"]
    factors = risk_result.get("riskFactors", [])
    actions = [
        {
            "priority": priority,
            "title": "상담 우선순위 확인",
            "description": f"{customer.name} 고객은 {priority} 관리 대상으로 분류됩니다.",
        }
    ]
    for factor in factors[:3]:
        actions.append(
            {
                "priority": priority,
                "title": factor["title"],
                "description": _action_for(factor["code"]),
            }
        )
    add_agent_trace(state, "PBActionAgent", "SUCCESS", "상담 우선순위와 후속 액션 생성 완료")
    return actions


def _action_for(code: str) -> str:
    return {
        "REAL_ESTATE_CONCENTRATION": "실거주 여부, 처분 가능성, 담보대출 연결 여부를 확인하십시오.",
        "LIQUIDITY_SHORTAGE": "현금성 자산으로 생활비를 몇 개월 감당할 수 있는지 확인하십시오.",
        "CASHFLOW_DEFICIT": "월 지출, 의료비, 가족 부양비 등 고정 지출을 재점검하십시오.",
        "DEBT_INTEREST_RATE": "변동금리 대출의 금리 상승 부담과 상환 계획을 확인하십시오.",
        "MARKET_VOLATILITY": "금융자산 변동성 노출이 고객 생활비 계획에 미치는 영향을 설명하십시오.",
        "PENSION_SHORTFALL": "국민연금, 퇴직연금, 개인연금 예상 수령액을 확인하십시오.",
    }.get(code, "상담을 통해 세부 내용을 확인하십시오.")

