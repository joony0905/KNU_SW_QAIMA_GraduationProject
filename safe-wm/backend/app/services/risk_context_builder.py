from __future__ import annotations

from app.models.customer import Customer


def build_risk_context(customer: Customer) -> dict:
    financial_value = sum(asset.marketValue or 0 for asset in customer.financialAssets)
    real_estate_value = sum(
        estate.estimatedValue * estate.ownershipRatio for estate in customer.realEstateAssets
    )
    loan_balance = sum(loan.balance for loan in customer.loans)
    monthly_debt_payment = sum(loan.monthlyPayment for loan in customer.loans)
    liquid_assets = customer.cashAssets + customer.depositAssets
    total_assets = (
        liquid_assets
        + financial_value
        + real_estate_value
        + customer.pensionAssets
        + customer.insuranceAssets
    )
    monthly_income_total = customer.monthlyIncome + customer.pensionIncome + customer.otherIncome
    monthly_surplus_before_debt = monthly_income_total - customer.monthlyExpense
    monthly_surplus_after_debt = monthly_income_total - customer.monthlyExpense - monthly_debt_payment
    cash_coverage_months = liquid_assets / customer.monthlyExpense if customer.monthlyExpense > 0 else 0
    real_estate_ratio = real_estate_value / total_assets if total_assets > 0 else 0
    risky_financial_value = sum(
        asset.marketValue or 0
        for asset in customer.financialAssets
        if (asset.assetType or "").upper() in {"STOCK", "ETF", "FUND"}
    )
    risky_asset_ratio = risky_financial_value / total_assets if total_assets > 0 else 0
    variable_loan_balance = sum(
        loan.balance for loan in customer.loans if loan.interestType.upper() == "VARIABLE"
    )
    variable_loan_ratio = variable_loan_balance / loan_balance if loan_balance > 0 else 0
    debt_to_asset_ratio = loan_balance / total_assets if total_assets > 0 else 0
    debt_payment_ratio = monthly_debt_payment / monthly_income_total if monthly_income_total > 0 else 0

    return {
        "customerId": customer.customerId,
        "totalAssets": round(total_assets, 2),
        "liquidAssets": round(liquid_assets, 2),
        "financialAssetValue": round(financial_value, 2),
        "realEstateValue": round(real_estate_value, 2),
        "loanBalance": round(loan_balance, 2),
        "monthlyDebtPayment": round(monthly_debt_payment, 2),
        "monthlyIncomeTotal": round(monthly_income_total, 2),
        "monthlySurplusBeforeDebt": round(monthly_surplus_before_debt, 2),
        "monthlySurplusAfterDebt": round(monthly_surplus_after_debt, 2),
        "cashCoverageMonths": round(cash_coverage_months, 2),
        "realEstateRatio": round(real_estate_ratio, 4),
        "riskyAssetRatio": round(risky_asset_ratio, 4),
        "variableLoanRatio": round(variable_loan_ratio, 4),
        "debtToAssetRatio": round(debt_to_asset_ratio, 4),
        "debtPaymentRatio": round(debt_payment_ratio, 4),
    }

