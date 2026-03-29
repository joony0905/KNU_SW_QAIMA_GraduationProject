# analysis/app/services/market_data_spring.py
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Dict, List, Optional, Literal

import httpx

from app.services.market_data import (
    MarketDataProvider,
    PriceSeries,
    LiquiditySeries,
    StockMeta,
)

Freq = Literal["ONE_D", "ONE_W"]


@dataclass(frozen=True)
class SpringClientConfig:
    base_url: str
    timeout_sec: float = 8.0


class SpringMarketDataProvider(MarketDataProvider):
    """
    Calls Spring once:
    POST {base_url}/api/v1/feature2/peercluster/data
    """

    def __init__(self, cfg: SpringClientConfig):
        self.cfg = cfg
        self._last_payload: Optional[dict] = None

    def _post_peercluster_data(
        self,
        industry_id: int,
        anchor_stock_code: str,
        freq: Freq,
        window: int,
    ) -> dict:
        url = f"{self.cfg.base_url}/api/v1/feature2/peercluster/data"

        # Spring DTO = snake_case contract
        payload = {
            "industry_id": industry_id,
            "anchor_stock_code": anchor_stock_code,
            "freq": freq,
            "window": window,
            "peer_count": 0,
            "max_lag": 0,
        }
        self._last_payload = payload
        print(f"[DEBUG][spring-pack] payload={payload}")

        with httpx.Client(timeout=self.cfg.timeout_sec) as client:
            r = client.post(url, json=payload)
            r.raise_for_status()
            data = r.json()
            print(f"[DEBUG][spring-pack] response warnings={data.get('warnings', [])}")
            return data

    def get_industry_members(self, industry_id: int) -> List[str]:
        raise RuntimeError("Use bulk methods in one call via get_*_bulk (see compute path).")

    def get_stock_meta_bulk(self, stock_codes: List[str]) -> Dict[str, StockMeta]:
        raise RuntimeError("Not used directly. Use get_peercluster_pack(...) pattern.")

    def get_price_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, PriceSeries]:
        raise RuntimeError("Not used directly. Use get_peercluster_pack(...) pattern.")

    def get_liquidity_series_bulk(self, stock_codes: List[str], freq: Freq, window: int) -> Dict[str, LiquiditySeries]:
        raise RuntimeError("Not used directly. Use get_peercluster_pack(...) pattern.")

    def get_peercluster_pack(
        self,
        industry_id: int,
        anchor_stock_code: str,
        freq: Freq,
        window: int,
    ) -> tuple[
        List[str],
        Dict[str, StockMeta],
        Dict[str, PriceSeries],
        Dict[str, LiquiditySeries],
        List[str],
    ]:
        data = self._post_peercluster_data(industry_id, anchor_stock_code, freq, window)

        warnings = data.get("warnings", []) or []
        members = data.get("members", []) or []

        metas: Dict[str, StockMeta] = {}
        for m in (data.get("metas", []) or []):
            metas[m["stock_code"]] = StockMeta(
                stock_code=m["stock_code"],
                company_name=m.get("company_name"),
                market_cap=m.get("market_cap"),
            )

        prices: Dict[str, PriceSeries] = {}
        for s in (data.get("prices", []) or []):
            dates = [datetime.fromisoformat(x.replace("Z", "+00:00")) for x in s["dates"]]
            prices[s["stock_code"]] = PriceSeries(
                stock_code=s["stock_code"],
                dates=dates,
                close=s["close"],
            )

        liquidity: Dict[str, LiquiditySeries] = {}
        for s in (data.get("liquidity", []) or []):
            dates = [datetime.fromisoformat(x.replace("Z", "+00:00")) for x in s["dates"]]
            liquidity[s["stock_code"]] = LiquiditySeries(
                stock_code=s["stock_code"],
                dates=dates,
                turnover=s["turnover"],
                volume=s["volume"],
            )

        return members, metas, prices, liquidity, warnings