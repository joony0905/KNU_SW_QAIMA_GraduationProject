from __future__ import annotations

from fastapi import APIRouter

from app.services.dashboard_service import customer_alerts, high_risk_customers, risk_summary

router = APIRouter(prefix="/api/wm/dashboard", tags=["dashboard"])


@router.get("/risk-summary")
def dashboard_risk_summary() -> dict:
    return risk_summary()


@router.get("/high-risk-customers")
def dashboard_high_risk_customers() -> dict:
    return {"customers": high_risk_customers()}


@router.get("/customer-alerts")
def dashboard_customer_alerts() -> dict:
    return {"alerts": customer_alerts()}
