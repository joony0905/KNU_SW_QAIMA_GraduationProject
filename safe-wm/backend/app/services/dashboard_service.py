from __future__ import annotations

from app.agents.evidence_reasoning_agent import build_evidence
from app.agents.preparation_harness import prepare_customer
from app.agents.risk_agent import run_risk_analysis_agent
from app.agents.severity_classification_agent import classify_severity
from app.agents.stability_assessment_agent import assess_stability
from app.models.customer import Customer
from app.services.customer_repository import list_customers


GRADE_ORDER = {"HIGH": 0, "MID": 1, "LOW": 2}
PRIORITY_ORDER = {"URGENT": 0, "WATCH": 1, "NORMAL": 2}


def build_customer_dashboard_summary(customer: Customer) -> dict:
    state = {"agentTrace": [], "debugSteps": []}
    prepared, context, warnings = prepare_customer(state, customer)
    assessments = assess_stability(state, context)
    severity_map = classify_severity(state, assessments)
    evidence_map, analysis_evidence, risk_alert = build_evidence(state, assessments, severity_map)
    risk_result = run_risk_analysis_agent(state, severity_map, evidence_map)
    main_factors = risk_result["riskFactors"][:3]
    return {
        "customerId": prepared.customerId,
        "name": prepared.name,
        "age": prepared.age,
        "region": prepared.region,
        "retirementStatus": prepared.retirementStatus,
        "summary": {
            "totalAssets": context["totalAssets"],
            "liquidAssets": context["liquidAssets"],
            "realEstateRatio": context["realEstateRatio"],
            "cashCoverageMonths": context["cashCoverageMonths"],
            "monthlySurplusAfterDebt": context["monthlySurplusAfterDebt"],
        },
        "lastAnalysis": {
            "assetStabilityScore": risk_result["assetStabilityScore"],
            "riskGrade": risk_result["riskGrade"],
            "priority": risk_result["priority"],
            "mainRiskFactors": [factor["code"] for factor in main_factors],
            "mainRiskTitles": [factor["title"] for factor in main_factors],
        },
        "riskAlert": risk_alert,
        "analysisEvidence": analysis_evidence,
        "warnings": [{"code": code, "message": code, "severity": "WARN"} for code in warnings],
    }


def list_dashboard_customers() -> list[dict]:
    rows = [build_customer_dashboard_summary(customer) for customer in list_customers()]
    return sorted(
        rows,
        key=lambda row: (
            PRIORITY_ORDER[row["lastAnalysis"]["priority"]],
            GRADE_ORDER[row["lastAnalysis"]["riskGrade"]],
            row["lastAnalysis"]["assetStabilityScore"],
            row["customerId"],
        ),
    )


def risk_summary() -> dict:
    rows = list_dashboard_customers()
    grade_counts = {"HIGH": 0, "MID": 0, "LOW": 0}
    priority_counts = {"URGENT": 0, "WATCH": 0, "NORMAL": 0}
    total_score = 0
    for row in rows:
        grade_counts[row["lastAnalysis"]["riskGrade"]] += 1
        priority_counts[row["lastAnalysis"]["priority"]] += 1
        total_score += row["lastAnalysis"]["assetStabilityScore"]
    average_score = round(total_score / len(rows), 1) if rows else 0
    return {
        "totalCustomers": len(rows),
        "averageAssetStabilityScore": average_score,
        "riskGradeCounts": grade_counts,
        "priorityCounts": priority_counts,
        "highRiskCustomerIds": [
            row["customerId"] for row in rows if row["lastAnalysis"]["riskGrade"] == "HIGH"
        ],
        "urgentCustomerIds": [
            row["customerId"] for row in rows if row["lastAnalysis"]["priority"] == "URGENT"
        ],
    }


def high_risk_customers() -> list[dict]:
    return [
        row
        for row in list_dashboard_customers()
        if row["lastAnalysis"]["riskGrade"] == "HIGH" or row["lastAnalysis"]["priority"] == "URGENT"
    ]


def customer_alerts() -> list[dict]:
    alerts: list[dict] = []
    for row in list_dashboard_customers():
        alert = row["riskAlert"]
        alerts.append(
            {
                "customerId": row["customerId"],
                "name": row["name"],
                "riskGrade": row["lastAnalysis"]["riskGrade"],
                "priority": row["lastAnalysis"]["priority"],
                "level": alert["level"],
                "title": alert["title"],
                "message": alert["message"],
                "triggeredBy": alert["triggeredBy"],
                "mainRiskTitles": row["lastAnalysis"]["mainRiskTitles"],
            }
        )
    return alerts
