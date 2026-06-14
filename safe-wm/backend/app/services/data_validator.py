from __future__ import annotations

from app.models.customer import ConfirmationItem, Customer


def validate_customer(customer: Customer) -> tuple[Customer, list[str]]:
    warnings: list[str] = []
    confirmation_items = list(customer.confirmationItems)

    if customer.monthlyExpense <= 0:
        warnings.append("CUSTOMER_MONTHLY_EXPENSE_INVALID")
    if not customer.realEstateAssets:
        confirmation_items.append(
            ConfirmationItem(
                code="REAL_ESTATE_OWNERSHIP_CONFIRMATION",
                label="부동산 보유 여부 확인",
                description="부동산 보유 여부와 평가금액 확인이 필요합니다.",
            )
        )
    for estate in customer.realEstateAssets:
        if estate.isPrimaryResidence:
            confirmation_items.append(
                ConfirmationItem(
                    code=f"{estate.propertyId}_PRIMARY_RESIDENCE_SELLABILITY",
                    label="실거주 부동산 처분 가능성",
                    description="실거주 부동산은 실제 유동화 가능 여부 확인이 필요합니다.",
                )
            )
    return customer.model_copy(update={"confirmationItems": confirmation_items}), warnings
