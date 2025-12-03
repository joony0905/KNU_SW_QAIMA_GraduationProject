# app/models/feature1.py
from datetime import date, datetime
from typing import List, Optional, Literal, Dict, Any

from pydantic import BaseModel


# ---- 공통 DTO들 (Java StockDto / PriceOhlcvDto / IndicatorValueDto / FinancialSummaryDto 대응) ----

class StockDto(BaseModel):
    stockId: Optional[int] = None
    stockCode: str
    isin: Optional[str] = None
    companyName: str

    exchangeId: Optional[int] = None
    exchangeCode: Optional[str] = None

    assetType: Optional[str] = None
    currency: Optional[str] = None

    industryId: Optional[int] = None

    # 추후 확장 Optional
    price: Optional[float] = None
    changeRate: Optional[float] = None

    listedAt: Optional[date] = None
    delistedAt: Optional[date] = None


class PriceOhlcvDto(BaseModel):
    ts: datetime
    freq: Literal["ONE_MIN", "FIVE_MIN", "FIFTEEN_MIN", "ONE_D", "ONE_W", "ONE_M", "ONE_H"]
    open: float
    high: float
    low: float
    close: float
    volume: float


class IndicatorValueDto(BaseModel):
    ts: datetime
    freq: Literal["ONE_MIN", "FIVE_MIN", "FIFTEEN_MIN", "ONE_D", "ONE_W", "ONE_M", "ONE_H"]

    key: str
    valueNum: Optional[float] = None
    valueJson: Optional[Dict[str, Any]] = None


class FinancialSummaryDto(BaseModel):
    fiscalYear: int
    fiscalQuarter: Optional[int] = None

    reportDate: date

    revenue: Optional[float] = None
    operatingIncome: Optional[float] = None
    netIncome: Optional[float] = None

    assets: Optional[float] = None
    equity: Optional[float] = None
    liabilities: Optional[float] = None

    roe: Optional[float] = None
    per: Optional[float] = None
    pbr: Optional[float] = None


class RequestOptions(BaseModel):
    outputLanguage: Literal["ko", "en"] = "ko"
    promptVersion: Optional[str] = None
    # 필요하면 나중에 옵션 더 추가


# ---- Spring → FastAPI 요청 DTO ----

class FeatOneRequestDto(BaseModel):
    stock: StockDto
    candles: List[PriceOhlcvDto]
    indicators: List[IndicatorValueDto] = []
    financials: List[FinancialSummaryDto] = []

    options: Optional[RequestOptions] = None


# ---- FastAPI → Spring 응답 DTO (LLM 텍스트 섹션) ----

class FeatOneResponseTextDto(BaseModel):
    stockId: Optional[int] = None
    stockCode: Optional[str] = None

    summary: Optional[str] = None
    business: Optional[str] = None
    financial: Optional[str] = None
    valuation: Optional[str] = None
    risk: Optional[str] = None
    outlook: Optional[str] = None

    rawPrompt: Optional[str] = None

    # 전체 보고서 텍스트 (summary~outlook 합친 버전)
    analysisText: Optional[str] = None
