from __future__ import annotations

from app.agents.state import add_agent_trace
from app.models.customer import Customer
from app.services.data_normalizer import normalize_customer
from app.services.data_validator import validate_customer
from app.services.risk_context_builder import build_risk_context


def prepare_customer(state: dict, customer: Customer) -> tuple[Customer, dict, list[str]]:
    normalized = normalize_customer(customer)
    validated, warnings = validate_customer(normalized)
    risk_context = build_risk_context(validated)
    add_agent_trace(
        state,
        "PreparationHarness",
        "SUCCESS",
        "Data Normalizer, Data Validator, Risk Context Builder 실행 완료",
        warnings,
    )
    return validated, risk_context, warnings

