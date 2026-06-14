from __future__ import annotations

from app.agents.state import add_agent_trace
from app.models.customer import Customer


def run_profile_agent(state: dict, customer: Customer, risk_context: dict) -> dict:
    profile = {
        "customerId": customer.customerId,
        "age": customer.age,
        "region": customer.region,
        "retirementStatus": customer.retirementStatus,
        "totalAssets": risk_context["totalAssets"],
        "monthlySurplusAfterDebt": risk_context["monthlySurplusAfterDebt"],
        "confirmationItemCount": len(customer.confirmationItems),
    }
    add_agent_trace(state, "ProfileAgent", "SUCCESS", "고객 자산/부채/현금흐름 프로필 정리 완료")
    return profile

