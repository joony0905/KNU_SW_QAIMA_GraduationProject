from __future__ import annotations

from app.agents.state import add_agent_trace


def classify_severity(state: dict, assessments: dict[str, dict]) -> dict[str, str]:
    result: dict[str, str] = {}
    for category, item in assessments.items():
        thresholds = item["thresholds"]
        value = item["value"]
        if "HIGH_BELOW" in thresholds:
            if value < thresholds["HIGH_BELOW"]:
                severity = "HIGH"
            elif value < thresholds["MID_BELOW"]:
                severity = "MID"
            elif value < thresholds.get("LOW_BELOW", float("inf")):
                severity = "LOW"
            else:
                severity = "NONE"
        else:
            if value > thresholds["HIGH"]:
                severity = "HIGH"
            elif value > thresholds["MID"]:
                severity = "MID"
            elif value > thresholds.get("LOW", float("-inf")):
                severity = "LOW"
            else:
                severity = "NONE"
        result[category] = severity
    add_agent_trace(state, "SeverityClassificationAgent", "SUCCESS", "위험 항목 NONE/LOW/MID/HIGH 분류 완료")
    return result
