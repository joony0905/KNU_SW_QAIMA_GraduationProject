from __future__ import annotations

from app.api.dashboard import dashboard_customer_alerts, dashboard_high_risk_customers, dashboard_risk_summary


def test_dashboard_risk_summary_counts_customer_risk() -> None:
    summary = dashboard_risk_summary()

    assert summary["totalCustomers"] == 4
    assert summary["riskGradeCounts"] == {"HIGH": 2, "MID": 1, "LOW": 1}
    assert summary["highRiskCustomerIds"] == ["C001", "C002"]
    assert set(summary["urgentCustomerIds"]) == {"C001", "C002"}


def test_dashboard_high_risk_customers_are_sorted_for_pb_action() -> None:
    rows = dashboard_high_risk_customers()["customers"]

    assert [row["customerId"] for row in rows] == ["C001", "C002"]
    assert all(row["lastAnalysis"]["priority"] == "URGENT" for row in rows)
    assert all(row["riskAlert"]["level"] == "HIGH" for row in rows)


def test_dashboard_customer_alerts_expose_pb_queue_data() -> None:
    alerts = dashboard_customer_alerts()["alerts"]

    assert len(alerts) == 4
    assert alerts[0]["customerId"] == "C001"
    assert alerts[0]["priority"] == "URGENT"
    assert alerts[-1]["riskGrade"] == "LOW"
