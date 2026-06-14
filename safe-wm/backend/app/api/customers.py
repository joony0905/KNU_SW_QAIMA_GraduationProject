from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.services.customer_repository import CustomerNotFoundError, get_customer, list_customers
from app.services.dashboard_service import build_customer_dashboard_summary

router = APIRouter(prefix="/api/wm/customers", tags=["customers"])


@router.get("")
def customers() -> dict:
    return {"customers": [_customer_summary(customer) for customer in list_customers()]}


@router.get("/{customer_id}")
def customer_detail(customer_id: str) -> dict:
    try:
        customer = get_customer(customer_id)
    except CustomerNotFoundError as exc:
        raise HTTPException(status_code=404, detail={"code": "CUSTOMER_NOT_FOUND", "message": str(exc)}) from exc
    return customer.dict()


def _customer_summary(customer) -> dict:
    return build_customer_dashboard_summary(customer)
