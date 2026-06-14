from __future__ import annotations

from app.agents.state import add_agent_trace
from app.models.customer import Customer
from app.services.rag_retriever import retrieve_guides


def run_rag_advisor_agent(
    state: dict,
    customer: Customer,
    risk_result: dict,
    *,
    force_unsafe: bool = False,
    retry: bool = False,
) -> tuple[dict, dict, list[dict]]:
    risk_codes = [factor["code"] for factor in risk_result.get("riskFactors", [])]
    guides = retrieve_guides(risk_codes)
    risk_titles = ", ".join(factor["title"] for factor in risk_result.get("riskFactors", [])[:3]) or "주요 위험 제한적"

    if force_unsafe and not retry:
        customer_text = "이 상품을 추천합니다. 반드시 상승합니다."
        pb_text = "고객에게 즉시 투자해야 합니다라고 안내하세요."
    else:
        customer_text = (
            f"현재 자산 안정성 점수는 {risk_result['assetStabilityScore']}점이며 "
            f"{risk_result['riskGrade']} 등급입니다. 주요 확인 항목은 {risk_titles}입니다. "
            "투자 판단보다 생활비 안정성, 긴급 유동성, 대출 부담을 PB와 함께 점검하는 것이 필요합니다."
        )
        pb_text = (
            f"{customer.name} 고객은 {risk_result['priority']} 우선순위입니다. "
            f"상담 시 {risk_titles}를 먼저 확인하고, 실거주 부동산 처분 가능성 및 월 현금흐름을 점검하십시오."
        )

    add_agent_trace(
        state,
        "RAGAdvisorAgent",
        "SUCCESS" if not retry else "RETRY",
        "RAG 근거를 바탕으로 고객용/PB용 설명 초안 생성",
    )
    return (
        {"text": customer_text, "ragEvidence": guides},
        {"text": pb_text, "ragEvidence": guides},
        guides,
    )

