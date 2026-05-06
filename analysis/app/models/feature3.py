from __future__ import annotations

from pydantic import BaseModel, Field


class PortfolioHoldingRequest(BaseModel):
    stock_code: str = Field(min_length=1)
    quantity: float = Field(gt=0)
    avg_price: float = Field(gt=0)


class PortfolioAnalyzeRequest(BaseModel):
    holdings: list[PortfolioHoldingRequest] = Field(min_length=1)
    options: list[str] = Field(default_factory=list)
    risk_gamma: float | None = Field(default=None, ge=0, le=1)


class PortfolioAnalyzeResponse(BaseModel):
    risk_level: str
    volatility: float
    diversification: str
    covariance_score: float
    efficiency: str
    gamma: float
