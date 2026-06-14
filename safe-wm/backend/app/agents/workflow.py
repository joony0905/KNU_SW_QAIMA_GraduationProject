from __future__ import annotations

import uuid

from app.agents.compliance_guard_agent import run_compliance_guard
from app.agents.evidence_reasoning_agent import build_evidence
from app.agents.pb_action_agent import run_pb_action_agent
from app.agents.preparation_harness import prepare_customer
from app.agents.profile_agent import run_profile_agent
from app.agents.rag_advisor_agent import run_rag_advisor_agent
from app.agents.risk_agent import run_risk_analysis_agent
from app.agents.severity_classification_agent import classify_severity
from app.agents.stability_assessment_agent import assess_stability
from app.models.customer import Customer
from app.services.audit_logger import write_audit_log
from app.services.qaima_client import enrich_context_with_qaima


async def run_analysis(
    customer: Customer,
    *,
    include_debug: bool = False,
    include_qaima: bool = False,
    force_unsafe_explanation: bool = False,
) -> dict:
    trace_id = f"trace-{uuid.uuid4().hex[:12]}"
    analysis_id = f"wm-{customer.customerId}-{uuid.uuid4().hex[:8]}"
    state: dict = {"traceId": trace_id, "analysisId": analysis_id, "debugSteps": [], "agentTrace": []}

    customer, risk_context, validation_warnings = prepare_customer(state, customer)
    enriched_context, qaima_warnings = await enrich_context_with_qaima(risk_context, include_qaima=include_qaima)
    if qaima_warnings:
        state["debugSteps"].append(
            {
                "step": "qaima_context_enrichment",
                "status": "FALLBACK",
                "durationMs": 0,
                "message": "QAIMA context enrichment fallback used",
                "warnings": [warning["code"] for warning in qaima_warnings],
            }
        )

    profile = run_profile_agent(state, customer, enriched_context)
    assessments = assess_stability(state, enriched_context)
    severity_map = classify_severity(state, assessments)
    evidence_map, analysis_evidence, risk_alert = build_evidence(state, assessments, severity_map)
    risk_result = run_risk_analysis_agent(state, severity_map, evidence_map)

    customer_explanation, pb_explanation, rag_evidence = run_rag_advisor_agent(
        state,
        customer,
        risk_result,
        force_unsafe=force_unsafe_explanation,
    )
    compliance = run_compliance_guard(state, customer_explanation, pb_explanation)
    if compliance["status"] == "FAIL":
        customer_explanation, pb_explanation, rag_evidence = run_rag_advisor_agent(
            state,
            customer,
            risk_result,
            force_unsafe=False,
            retry=True,
        )
        compliance = run_compliance_guard(state, customer_explanation, pb_explanation)
        compliance["regenerated"] = True
    else:
        compliance["regenerated"] = False

    pb_actions = run_pb_action_agent(state, customer, risk_result)
    warnings = [{"code": code, "message": code, "severity": "WARN", "source": "VALIDATION"} for code in validation_warnings]
    warnings.extend(qaima_warnings)

    response = {
        "customerId": customer.customerId,
        "analysisId": analysis_id,
        "traceId": trace_id,
        "profile": profile,
        "assetStabilityScore": risk_result["assetStabilityScore"],
        "riskGrade": risk_result["riskGrade"],
        "priority": risk_result["priority"],
        "riskAlert": risk_alert,
        "scoreBreakdown": risk_result["scoreBreakdown"],
        "riskFactors": risk_result["riskFactors"],
        "analysisEvidence": analysis_evidence,
        "cashflowAnalysis": _cashflow(enriched_context),
        "liquidityAnalysis": _liquidity(enriched_context),
        "realEstateRisk": _real_estate(enriched_context, severity_map),
        "debtRateRisk": _debt(enriched_context, severity_map),
        "marketRisk": _market(enriched_context, severity_map),
        "customerExplanation": customer_explanation,
        "pbExplanation": pb_explanation,
        "pbActions": pb_actions,
        "compliance": compliance,
        "ragEvidence": rag_evidence,
        "warnings": warnings,
        "agentTrace": state["agentTrace"],
    }
    if include_debug:
        response["debug"] = {"traceId": trace_id, "steps": state["debugSteps"]}

    write_audit_log(
        {
            "traceId": trace_id,
            "customerId": customer.customerId,
            "analysisId": analysis_id,
            "assetStabilityScore": response["assetStabilityScore"],
            "riskGrade": response["riskGrade"],
            "riskAlertLevel": risk_alert["level"],
            "analysisEvidenceCount": len(analysis_evidence),
            "agentTraceSummary": [f"{item['agent']}:{item['status']}" for item in state["agentTrace"]],
            "complianceStatus": compliance["status"],
            "generatedOutputSummary": "고객용 설명 및 PB 상담 액션 생성 완료",
            "warnings": [warning["code"] for warning in warnings],
        }
    )
    return response


def _cashflow(ctx: dict) -> dict:
    return {
        "monthlyIncomeTotal": ctx["monthlyIncomeTotal"],
        "monthlyDebtPayment": ctx["monthlyDebtPayment"],
        "monthlySurplusAfterDebt": ctx["monthlySurplusAfterDebt"],
    }


def _liquidity(ctx: dict) -> dict:
    return {"liquidAssets": ctx["liquidAssets"], "cashCoverageMonths": ctx["cashCoverageMonths"]}


def _real_estate(ctx: dict, severity_map: dict[str, str]) -> dict:
    return {
        "realEstateValue": ctx["realEstateValue"],
        "realEstateRatio": ctx["realEstateRatio"],
        "severity": severity_map["REAL_ESTATE_CONCENTRATION"],
    }


def _debt(ctx: dict, severity_map: dict[str, str]) -> dict:
    return {
        "loanBalance": ctx["loanBalance"],
        "variableLoanRatio": ctx["variableLoanRatio"],
        "debtPaymentRatio": ctx["debtPaymentRatio"],
        "severity": severity_map["DEBT_INTEREST_RATE"],
    }


def _market(ctx: dict, severity_map: dict[str, str]) -> dict:
    return {
        "riskyAssetRatio": ctx["riskyAssetRatio"],
        "severity": severity_map["MARKET_VOLATILITY"],
        "qaima": ctx.get("qaima", {"enabled": False, "source": "FALLBACK"}),
    }

