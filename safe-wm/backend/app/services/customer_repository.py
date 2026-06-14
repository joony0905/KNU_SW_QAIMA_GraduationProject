from __future__ import annotations

import json
from functools import lru_cache

from app.core.config import DATA_DIR
from app.models.customer import Customer


class CustomerNotFoundError(ValueError):
    pass


@lru_cache(maxsize=1)
def _load_customers() -> tuple[Customer, ...]:
    path = DATA_DIR / "mock_customers.json"
    rows = json.loads(path.read_text(encoding="utf-8"))
    return tuple(Customer(**row) for row in rows)


def list_customers() -> list[Customer]:
    return list(_load_customers())


def get_customer(customer_id: str) -> Customer:
    normalized = customer_id.strip().upper()
    for customer in _load_customers():
        if customer.customerId.upper() == normalized:
            return customer
    raise CustomerNotFoundError(f"Customer not found: {customer_id}")

