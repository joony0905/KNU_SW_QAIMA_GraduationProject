# analysis/app/services/market_data.py
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Dict, List, Optional, Protocol, Literal

"""
기능2에 필요한 시총 / 거래대금 / 거래량 / 수익률 
기반 데이터 셋
"""
Freq = Literal["ONE_D", "ONE_W"]


@dataclass(frozen=True)
class PriceSeries:
    """Price series for a stock. Dates must be ascending."""
    stock_code: str
    dates: List[datetime]
    close: List[float]


@dataclass(frozen=True)
class LiquiditySeries:
    """
    Liquidity series aligned to dates (same length).
    turnover: 거래대금 (금액)
    volume: 거래량 (수량)
    """
    stock_code: str
    dates: List[datetime]
    turnover: List[float]
    volume: List[float]


@dataclass(frozen=True)
class StockMeta:
    stock_code: str
    company_name: Optional[str] = None
    market_cap: Optional[float] = None  # 시총(가능하면 최신 or as-of)


class MarketDataProvider(Protocol):
    """
    PeerCluster 계산에 필요한 데이터 접근 레이어.
    실제 구현은:
    - Spring API 호출 (추천)
    - 또는 DB/외부 API 직접 호출
    로 바꿔 끼우면 됨.
    """

    def get_industry_members(self, industry_id: int) -> List[str]:
        """산업 내 종목코드 목록."""
        ...

    def get_stock_meta_bulk(self, stock_codes: List[str]) -> Dict[str, StockMeta]:
        """종목 메타(회사명/시총) bulk."""
        ...

    def get_price_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, PriceSeries]:
        """종가 시계열 bulk."""
        ...

    def get_liquidity_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, LiquiditySeries]:
        """거래대금/거래량 시계열 bulk."""
        ...


# --------------------------
# Default stub (TODO)
# --------------------------
class NotConfiguredMarketDataProvider:
    def get_industry_members(self, industry_id: int) -> List[str]:
        raise RuntimeError("MarketDataProvider not configured")

    def get_stock_meta_bulk(self, stock_codes: List[str]) -> Dict[str, StockMeta]:
        raise RuntimeError("MarketDataProvider not configured")

    def get_price_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, PriceSeries]:
        raise RuntimeError("MarketDataProvider not configured")

    def get_liquidity_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, LiquiditySeries]:
        raise RuntimeError("MarketDataProvider not configured")