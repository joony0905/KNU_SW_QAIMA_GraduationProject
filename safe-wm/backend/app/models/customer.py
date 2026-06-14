from __future__ import annotations

from pydantic import BaseModel, Field


class FinancialAsset(BaseModel):
    assetId: str
    assetType: str
    stockCode: str | None = None
    name: str
    quantity: float = 0
    avgPrice: float = 0
    currentPrice: float = 0
    currency: str = "KRW"
    marketValue: float | None = None
    volatilityLevel: str | None = None


class RealEstateAsset(BaseModel):
    propertyId: str
    type: str
    region: str
    estimatedValue: float
    ownershipRatio: float = 1.0
    isPrimaryResidence: bool = False
    monthlyRentIncome: float = 0
    linkedLoanId: str | None = None
    liquidityLevel: str = "MID"
    priceChange1y: float = 0


class Loan(BaseModel):
    loanId: str
    type: str
    balance: float
    interestRate: float
    interestType: str
    monthlyPayment: float
    maturityDate: str | None = None
    collateralType: str | None = None
    linkedPropertyId: str | None = None


class ConfirmationItem(BaseModel):
    code: str
    label: str
    description: str
    required: bool = True


class Customer(BaseModel):
    customerId: str
    name: str
    age: int
    region: str
    retirementStatus: str = "RETIRED"
    householdSize: int = 1
    monthlyIncome: float = 0
    monthlyExpense: float
    pensionIncome: float = 0
    otherIncome: float = 0
    cashAssets: float = 0
    depositAssets: float = 0
    financialAssets: list[FinancialAsset] = Field(default_factory=list)
    realEstateAssets: list[RealEstateAsset] = Field(default_factory=list)
    loans: list[Loan] = Field(default_factory=list)
    pensionAssets: float = 0
    insuranceAssets: float = 0
    confirmationItems: list[ConfirmationItem] = Field(default_factory=list)

