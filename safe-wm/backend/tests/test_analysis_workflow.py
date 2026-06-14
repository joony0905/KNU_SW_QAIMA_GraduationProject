from __future__ import annotations

import asyncio

from app.api.analysis import analyze_customer
from app.models.analysis import AnalysisRequest


def run(coro):
    return asyncio.run(coro)


def test_mock_customer_grades_are_fixed() -> None:
    expected = {"C001": "HIGH", "C002": "HIGH", "C003": "MID", "C004": "LOW"}
    for customer_id, risk_grade in expected.items():
        result = run(analyze_customer(customer_id, AnalysisRequest(includeDebug=False)))
        assert result["riskGrade"] == risk_grade


def test_debug_steps_are_hidden_by_default_and_visible_when_requested() -> None:
    without_debug = run(analyze_customer("C001", AnalysisRequest(includeDebug=False)))
    with_debug = run(analyze_customer("C001", AnalysisRequest(includeDebug=True)))

    assert "debug" not in without_debug
    assert with_debug["debug"]["steps"]


def test_compliance_regenerates_for_forbidden_expression() -> None:
    result = run(
        analyze_customer(
            "C001",
            AnalysisRequest(includeDebug=True, forceUnsafeExplanation=True),
        )
    )

    assert result["compliance"]["status"] == "PASS"
    assert result["compliance"]["regenerated"] is True
    assert "반드시 상승" not in result["customerExplanation"]["text"]
    assert any(item["status"] == "RETRY" for item in result["agentTrace"])
