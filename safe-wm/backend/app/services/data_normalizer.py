from __future__ import annotations

from app.models.customer import Customer, FinancialAsset


def normalize_customer(customer: Customer) -> Customer:
    assets: list[FinancialAsset] = []
    for asset in customer.financialAssets:
        market_value = asset.marketValue
        if market_value is None:
            market_value = round((asset.quantity or 0) * (asset.currentPrice or 0), 2)
        assets.append(asset.model_copy(update={"marketValue": market_value}))
    return customer.model_copy(update={"financialAssets": assets})
