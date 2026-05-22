from __future__ import annotations

import os
import asyncio
import json
import logging
from datetime import date
from typing import Optional, Tuple
import httpx

from app.services.llm.base import LLMClient
from app.models.feature1 import Feature1Request, Feature1Metrics, FinancialPointItem
from app.models.feature2 import Feature2ExplainRequest
from app.services.llm.feature2_prompt import build_feature2_prompt
from app.services.llm.invest_level import invest_level_prompt


# v1 사용
GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_TIMEOUT = 10.0
RATE_LIMIT_RETRY_DELAYS = (0.6, 1.2)
log = logging.getLogger(__name__)


class GeminiClient(LLMClient):
    def __init__(self) -> None:
        self.api_key: Optional[str] = os.getenv("GEMINI_API_KEY")
        self.model: str = os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
        try:
            self.timeout: float = float(os.getenv("GEMINI_TIMEOUT", DEFAULT_TIMEOUT))
        except ValueError:
            self.timeout = DEFAULT_TIMEOUT

    async def generate_explain(
        self,
        req: Feature1Request,
        metrics: Feature1Metrics,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.api_key:
            return None, "LLM_API_KEY_MISSING"

        prompt = self._build_prompt(req, metrics, compact=compact)
        url = f"{GEMINI_BASE_URL}/{self.model}:generateContent?key={self.api_key}"

        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.1,
                "maxOutputTokens": 1400,
                "responseMimeType": "application/json",
            },
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = None
                for attempt in range(len(RATE_LIMIT_RETRY_DELAYS) + 1):
                    resp = await client.post(url, json=payload)

                    if resp.status_code != 429:
                        break

                    if attempt < len(RATE_LIMIT_RETRY_DELAYS):
                        await asyncio.sleep(RATE_LIMIT_RETRY_DELAYS[attempt])

                # 왜 실패했는지 warnings에 찍기
                if resp is not None and resp.status_code == 429:
                    return None, "LLM_EXPLAIN_RATE_LIMITED"

                if resp is None:
                    return None, "LLM_EXPLAIN_EMPTY"

                if resp.status_code >= 400:
                    body = (resp.text or "").strip().replace("\n", " ")
                    body = body[:250]
                    log.warning("[feature1][llm] Gemini HTTP %s compact=%s body=%s", resp.status_code, compact, body)
                    return None, f"LLM_EXPLAIN_HTTP_{resp.status_code}:{body}"

                data = resp.json()
                
                text = (
                    data.get("candidates", [{}])[0]
                    .get("content", {})
                    .get("parts", [{}])[0]
                    .get("text", "")
                    .strip()
                )

                if not text:
                    return None, "LLM_EXPLAIN_EMPTY"

                log.info("[feature1][llm] Gemini raw explain compact=%s %s", compact, text[:2000])

                return text, None

        except httpx.TimeoutException:
            return None, "LLM_EXPLAIN_TIMEOUT"
        except Exception as e:
            # 예외 종류도 같이 남겨서 원인 확정
            return None, f"LLM_EXPLAIN_EXCEPTION:{type(e).__name__}:{str(e)[:120]}"

    async def generate_feature2_explain(
        self,
        req: Feature2ExplainRequest,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.api_key:
            return None, "LLM_API_KEY_MISSING"

        url = f"{GEMINI_BASE_URL}/{self.model}:generateContent?key={self.api_key}"
        payload = {
            "contents": [{"role": "user", "parts": [{"text": build_feature2_prompt(req, compact=compact)}]}],
            "generationConfig": {
                "temperature": 0.1,
                "maxOutputTokens": 900 if compact else 1600,
            },
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = None
                for attempt in range(len(RATE_LIMIT_RETRY_DELAYS) + 1):
                    resp = await client.post(url, json=payload)
                    if resp.status_code != 429:
                        break
                    if attempt < len(RATE_LIMIT_RETRY_DELAYS):
                        await asyncio.sleep(RATE_LIMIT_RETRY_DELAYS[attempt])

                if resp is not None and resp.status_code == 429:
                    return None, "LLM_EXPLAIN_RATE_LIMITED"
                if resp is None:
                    return None, "LLM_EXPLAIN_EMPTY"
                if resp.status_code >= 400:
                    body = (resp.text or "").strip().replace("\n", " ")[:250]
                    log.warning("[feature2][llm] Gemini HTTP %s compact=%s body=%s", resp.status_code, compact, body)
                    return None, f"LLM_EXPLAIN_HTTP_{resp.status_code}:{body}"

                data = resp.json()
                text = (
                    data.get("candidates", [{}])[0]
                    .get("content", {})
                    .get("parts", [{}])[0]
                    .get("text", "")
                    .strip()
                )
                if not text:
                    return None, "LLM_EXPLAIN_EMPTY"
                log.info("[feature2][llm] Gemini raw explain compact=%s %s", compact, text[:2000])
                return text, None

        except httpx.TimeoutException:
            return None, "LLM_EXPLAIN_TIMEOUT"
        except Exception as e:
            return None, f"LLM_EXPLAIN_EXCEPTION:{type(e).__name__}:{str(e)[:120]}"

    def _build_prompt(self, req: Feature1Request, metrics: Feature1Metrics, compact: bool = False) -> str:
        o = metrics.ohlcv_summary
        market_snapshot = metrics.market_snapshot
        indicator_summary = metrics.indicator_summary or "indicator summary unavailable"
        financial_summary = _build_financial_summary(req)
        return (
            "아래 규칙을 반드시 지키고 JSON 객체만 출력하세요.\n"
            "마크다운/코드블록(```) 사용 금지.\n"
            "출력은 { 로 시작해서 } 로 끝나야 합니다.\n\n"

            "규칙:\n"
            "1) 각 섹션 설명은 해당 섹션 데이터만 근거로 작성\n"
            "2) 섹션별 summary는 정확히 3문장으로 작성\n"
            "3) overall.summary는 정확히 5문장, overall.conclusion은 정확히 1문장으로 작성\n"
            "4) 투자 권유/매수·매도 추천 금지\n"
            "5) 데이터가 비거나 약하면 그 한계를 보수적으로 표현\n\n"
            "6) 키 이름은 반드시 sections.price_flow / sections.market_snapshot / sections.indicators / sections.financial_timeline / overall 을 정확히 유지\n"
            "7) 키를 번역하거나 camelCase로 바꾸지 말 것\n\n"
            f"{invest_level_prompt(req.invest_level)}\n"

            "출력 JSON 스키마:\n"
            "{"
            "\"sections\":{"
              "\"price_flow\":{\"summary\":\"string\"},"
              "\"market_snapshot\":{\"summary\":\"string\"},"
              "\"indicators\":{\"summary\":\"string\"},"
              "\"financial_timeline\":{\"summary\":\"string\"}"
            "},"
            "\"overall\":{"
              "\"summary\":\"string\","
              "\"conclusion\":\"string\""
            "}"
            "}\n\n"

            "입력:\n"
            f"stock_code={metrics.stock_code}\n"
            "\n[PRICE_FLOW]\n"
            f"ohlcv_count={o.count}\n"
            f"period_from={o.from_}\n"
            f"period_to={o.to}\n"
            f"last_close={o.last_close}\n\n"
            "[MARKET_SNAPSHOT]\n"
            f"per={market_snapshot.valuation.per}\n"
            f"pbr={market_snapshot.valuation.pbr}\n"
            f"psr={market_snapshot.valuation.psr}\n"
            f"market_cap={market_snapshot.valuation.market_cap}\n"
            f"roe={market_snapshot.profitability.roe}\n"
            f"roa={market_snapshot.profitability.roa}\n"
            f"operating_margin={market_snapshot.profitability.operating_margin}\n"
            f"net_margin={market_snapshot.profitability.net_margin}\n"
            f"debt_ratio={market_snapshot.stability.debt_ratio}\n"
            f"current_ratio={market_snapshot.stability.current_ratio}\n"
            f"quick_ratio={market_snapshot.stability.quick_ratio}\n"
            f"interest_coverage_ratio={market_snapshot.stability.interest_coverage_ratio}\n"
            f"revenue_growth={market_snapshot.growth.revenue_growth}\n"
            f"eps_growth={market_snapshot.growth.eps_growth}\n"
            f"free_cash_flow={market_snapshot.growth.free_cash_flow}\n"
            f"eps={market_snapshot.per_share.eps}\n"
            f"bps={market_snapshot.per_share.bps}\n\n"
            "[INDICATORS]\n"
            f"{indicator_summary}\n"
            "\n[FINANCIAL_SUMMARY]\n"
            f"{financial_summary}\n"
        )


def _build_financial_summary(req: Feature1Request) -> str:
    points = _select_financial_points(req)
    if not points:
        return (
            "period_labels=[]\n"
            "latest_period=null\n"
            "revenue_trend=unknown\n"
            "operating_income_trend=unknown\n"
            "net_income_trend=unknown\n"
            "latest_revenue=null\n"
            "latest_operating_income=null\n"
            "latest_net_income=null\n"
            "latest_operating_margin=null"
        )

    labels = [_financial_label(point) for point in points]
    latest = points[-1]
    earliest = points[0]
    return (
        f"period_labels={json.dumps(labels, ensure_ascii=False)}\n"
        f"latest_period={json.dumps(labels[-1], ensure_ascii=False)}\n"
        f"revenue_trend={_trend_label(earliest.revenue, latest.revenue)}\n"
        f"operating_income_trend={_trend_label(earliest.operating_income, latest.operating_income)}\n"
        f"net_income_trend={_trend_label(earliest.net_income, latest.net_income)}\n"
        f"latest_revenue={latest.revenue}\n"
        f"latest_operating_income={latest.operating_income}\n"
        f"latest_net_income={latest.net_income}\n"
        f"latest_operating_margin={_ratio_percent(latest.operating_income, latest.revenue)}"
    )


def _select_financial_points(req: Feature1Request) -> list[FinancialPointItem]:
    points = list(req.financials or [])
    if not points:
        return []

    ordered = sorted(
        points,
        key=lambda p: (
            _point_date(p) or date.min,
            p.fiscal_year or 0,
            _period_rank(p.period_type),
            p.period_no or 0,
        ),
    )

    from_date = req.from_ if req.from_ else None
    to_date = req.to if req.to else None
    if from_date or to_date:
        in_range = [
            point for point in ordered
            if _is_in_range(_point_date(point), from_date, to_date)
        ]
        if in_range:
            return in_range[-6:]

    return ordered[-6:]


def _is_in_range(value: Optional[date], from_date: Optional[date], to_date: Optional[date]) -> bool:
    if value is None:
        return False
    if from_date and value < from_date:
        return False
    if to_date and value > to_date:
        return False
    return True


def _point_date(point: FinancialPointItem) -> Optional[date]:
    return point.report_date


def _period_rank(period_type: Optional[str]) -> int:
    return {"Q": 1, "H": 2, "A": 3}.get((period_type or "").upper(), 9)


def _financial_label(point: FinancialPointItem) -> str:
    fiscal_year = point.fiscal_year
    period_type = (point.period_type or "").upper()
    period_no = point.period_no
    if fiscal_year is not None and period_type == "Q" and period_no is not None:
        return f"{fiscal_year} Q{period_no}"
    if fiscal_year is not None and period_type == "H" and period_no is not None:
        return f"{fiscal_year} H{period_no}"
    if fiscal_year is not None and period_type == "A":
        return str(fiscal_year)
    if point.report_date is not None:
        return point.report_date.isoformat()
    return "unknown"


def _trend_label(start: Optional[float], end: Optional[float]) -> str:
    if start is None or end is None:
        return "unknown"
    if start == end:
        return "flat"
    return "up" if end > start else "down"


def _safe_div(numerator: Optional[float], denominator: Optional[float]) -> Optional[float]:
    if numerator is None or denominator is None or denominator == 0:
        return None
    return numerator / denominator


def _ratio_percent(numerator: Optional[float], denominator: Optional[float]) -> Optional[float]:
    value = _safe_div(numerator, denominator)
    return None if value is None else value * 100
