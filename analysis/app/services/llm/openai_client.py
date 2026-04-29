from __future__ import annotations

import asyncio
import json
import logging
import os
from datetime import date, datetime
from typing import Optional, Tuple

import httpx

from app.models.feature1 import Feature1Metrics, Feature1Request, FinancialPointItem
from app.models.feature2 import Feature2ExplainRequest
from app.services.llm.base import LLMClient
from app.services.llm.feature2_prompt import build_feature2_prompt

OPENAI_BASE_URL = "https://api.openai.com/v1/responses"
DEFAULT_MODEL = "gpt-5-mini"
DEFAULT_TIMEOUT = 30.0
RATE_LIMIT_RETRY_DELAYS = (0.6, 1.2)
SERVER_ERROR_RETRY_DELAYS = (1.0, 2.0)
DEFAULT_MAX_OUTPUT_TOKENS = 10000
log = logging.getLogger(__name__)


class OpenAIClient(LLMClient):
    def __init__(self) -> None:
        self.api_key: Optional[str] = os.getenv("OPENAI_API_KEY")
        self.default_model: str = os.getenv("OPENAI_MODEL", DEFAULT_MODEL)
        try:
            self.timeout: float = float(os.getenv("OPENAI_TIMEOUT", DEFAULT_TIMEOUT))
        except ValueError:
            self.timeout = DEFAULT_TIMEOUT
        try:
            self.max_output_tokens: int = int(os.getenv("OPENAI_MAX_OUTPUT_TOKENS", DEFAULT_MAX_OUTPUT_TOKENS))
        except ValueError:
            self.max_output_tokens = DEFAULT_MAX_OUTPUT_TOKENS

    async def generate_explain(
        self,
        req: Feature1Request,
        metrics: Feature1Metrics,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.api_key:
            return None, "LLM_API_KEY_MISSING"

        model = self._resolve_model(req.llm_vendor)
        prompt = self._build_prompt(req, metrics)
        schema = self._build_response_schema(compact=compact)

        payload = {
            "model": model,
            "instructions": (
                    "당신은 데이터 기반 투자 분석가이다.\n"
                    "[작성 원칙]\n"
                    "모든 분석은 제공된 데이터만 사용\n"
                    "외부 지식 사용 금지\n"
                    "일방적인 데이터 나열 금지, 데이터 기반 설명 사용\n"
                    "데이터 기반 내용은 확정적 표현 사용"
                    "데이터 근거로 간접적인 전망 제시 필수\n" 
                    "직접적인 투자 권유/매수·매도 추천 금지\n"
                    "overall.summary는 모든 데이터를 종합한 의견\n"
                    "[분석 절차]\n"
                    "각 섹션마다 다음 순서를 반드시 따른다:\n"
                    "1. 섹션별 제공된 데이터들의 핵심 파악\n"
                    "2. 수치 수준 또는 변화 방향 판단\n"
                    "3. 의미 해석\n"
                    "4. 1~2문장으로 요약\n"
                    "[해석 기준 예시]\n"
                    "성장률 > 0 → 성장\n"
                    "ROE 높음 → 수익성 우수\n"
                    "부채비율 높음 → 재무 리스크 존재\n"
                    "현금흐름 양수 → 재무 안정성 긍정적\n"
                    "[출력 규칙]\n"
                    "모든 문장은 귀엽게 존댓말 “~에요 / ~이에요” 체로 작성\n"
                    "숫자는 반드시 사람이 읽기 쉬운 한국식 단위(억, 만)로 변환하여 출력\n"
                    "각 섹션 summary는 1~2문장\n"
                    "overall.summary는 정확히 6문장\n"
                    "overall.conclusion은 최대 2문장\n"
                    "반드시 JSON만 반환\n"
            ),
            "input": prompt,
            "max_output_tokens": self.max_output_tokens,
            "reasoning": {
                "effort": "low",
            },
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": "feature1_explain",
                    "strict": True,
                    "schema": schema,
                },
            },
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = None
                max_attempts = max(len(RATE_LIMIT_RETRY_DELAYS), len(SERVER_ERROR_RETRY_DELAYS)) + 1
                for attempt in range(max_attempts):
                    resp = await client.post(OPENAI_BASE_URL, headers=headers, json=payload)

                    if resp.status_code == 429:
                        if attempt < len(RATE_LIMIT_RETRY_DELAYS):
                            await asyncio.sleep(RATE_LIMIT_RETRY_DELAYS[attempt])
                            continue
                        break

                    if resp.status_code >= 500:
                        body = (resp.text or "").strip().replace("\n", " ")
                        request_id = _extract_request_id(body)
                        log.warning(
                            "[feature1][llm] OpenAI server error retry model=%s compact=%s status=%s attempt=%s request_id=%s body=%s",
                            model,
                            compact,
                            resp.status_code,
                            attempt + 1,
                            request_id,
                            body[:300],
                        )
                        if attempt < len(SERVER_ERROR_RETRY_DELAYS):
                            await asyncio.sleep(SERVER_ERROR_RETRY_DELAYS[attempt])
                            continue
                        break

                    break

                if resp is not None and resp.status_code == 429:
                    return None, "LLM_EXPLAIN_RATE_LIMITED"

                if resp is None:
                    return None, "LLM_EXPLAIN_EMPTY"

                if resp.status_code >= 400:
                    body = (resp.text or "").strip().replace("\n", " ")
                    body = body[:300]
                    request_id = _extract_request_id(body)
                    log.warning("[feature1][llm] OpenAI HTTP %s model=%s compact=%s body=%s",
                                resp.status_code, model, compact, body)
                    if request_id:
                        log.warning("[feature1][llm] OpenAI request id model=%s compact=%s request_id=%s",
                                    model, compact, request_id)
                    return None, f"LLM_EXPLAIN_HTTP_{resp.status_code}:{body}"

                data = resp.json()
                usage = data.get("usage") or {}
                if isinstance(usage, dict):
                    input_tokens = usage.get("input_tokens")
                    output_tokens = usage.get("output_tokens")
                    total_tokens = usage.get("total_tokens")
                    reasoning_tokens = ((usage.get("output_tokens_details") or {}).get("reasoning_tokens"))
                    log.warning(
                        "[feature1][llm] OpenAI usage model=%s compact=%s input_tokens=%s output_tokens=%s total_tokens=%s reasoning_tokens=%s",
                        model,
                        compact,
                        input_tokens,
                        output_tokens,
                        total_tokens,
                        reasoning_tokens,
                    )
                else:
                    log.warning("[feature1][llm] OpenAI usage missing model=%s compact=%s", model, compact)
                status = data.get("status")
                if status == "incomplete":
                    reason = (((data.get("incomplete_details") or {}).get("reason")) or "unknown")
                    log.warning("[feature1][llm] OpenAI incomplete model=%s compact=%s reason=%s raw=%s",
                                model, compact, reason, json.dumps(data)[:2000])
                    if reason == "max_output_tokens":
                        return None, "LLM_EXPLAIN_MAX_OUTPUT_TOKENS"
                    if reason == "content_filter":
                        return None, "LLM_EXPLAIN_CONTENT_FILTER"
                    return None, f"LLM_EXPLAIN_INCOMPLETE:{reason}"

                refusal = self._extract_refusal_text(data)
                if refusal:
                    log.warning("[feature1][llm] OpenAI refusal model=%s compact=%s refusal=%s",
                                model, compact, refusal[:500])
                    return None, "LLM_EXPLAIN_REFUSAL"

                text = self._extract_content_text(data)
                if not text:
                    log.warning("[feature1][llm] OpenAI empty content model=%s compact=%s raw=%s",
                                model, compact, json.dumps(data)[:2000])
                    return None, "LLM_EXPLAIN_EMPTY"

                log.info("[feature1][llm] OpenAI raw explain model=%s compact=%s %s", model, compact, text[:2000])
                return text, None

        except httpx.TimeoutException:
            log.warning("[feature1][llm] OpenAI timeout model=%s compact=%s timeout=%s", model, compact, self.timeout)
            return None, "LLM_EXPLAIN_TIMEOUT"
        except Exception as exc:
            return None, f"LLM_EXPLAIN_EXCEPTION:{type(exc).__name__}:{str(exc)[:120]}"

    async def generate_feature2_explain(
        self,
        req: Feature2ExplainRequest,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.api_key:
            return None, "LLM_API_KEY_MISSING"

        model = self._resolve_model(req.llm_vendor)
        payload = {
            "model": model,
            "instructions": (
                "당신은 데이터 기반 투자 분석가입니다. "
                "제공된 Feature2 외부요인 데이터만 사용해 설명하세요."
            ),
            "input": build_feature2_prompt(req, compact=compact),
            "max_output_tokens": min(self.max_output_tokens, 1200 if compact else 2200),
            "reasoning": {"effort": "low"},
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = None
                max_attempts = max(len(RATE_LIMIT_RETRY_DELAYS), len(SERVER_ERROR_RETRY_DELAYS)) + 1
                for attempt in range(max_attempts):
                    resp = await client.post(OPENAI_BASE_URL, headers=headers, json=payload)
                    if resp.status_code == 429 and attempt < len(RATE_LIMIT_RETRY_DELAYS):
                        await asyncio.sleep(RATE_LIMIT_RETRY_DELAYS[attempt])
                        continue
                    if resp.status_code >= 500 and attempt < len(SERVER_ERROR_RETRY_DELAYS):
                        await asyncio.sleep(SERVER_ERROR_RETRY_DELAYS[attempt])
                        continue
                    break

                if resp is not None and resp.status_code == 429:
                    return None, "LLM_EXPLAIN_RATE_LIMITED"
                if resp is None:
                    return None, "LLM_EXPLAIN_EMPTY"
                if resp.status_code >= 400:
                    body = (resp.text or "").strip().replace("\n", " ")[:300]
                    log.warning("[feature2][llm] OpenAI HTTP %s model=%s compact=%s body=%s",
                                resp.status_code, model, compact, body)
                    return None, f"LLM_EXPLAIN_HTTP_{resp.status_code}:{body}"

                data = resp.json()
                status = data.get("status")
                if status == "incomplete":
                    reason = (((data.get("incomplete_details") or {}).get("reason")) or "unknown")
                    if reason == "max_output_tokens":
                        return None, "LLM_EXPLAIN_MAX_OUTPUT_TOKENS"
                    if reason == "content_filter":
                        return None, "LLM_EXPLAIN_CONTENT_FILTER"
                    return None, f"LLM_EXPLAIN_INCOMPLETE:{reason}"

                refusal = self._extract_refusal_text(data)
                if refusal:
                    log.warning("[feature2][llm] OpenAI refusal model=%s compact=%s refusal=%s",
                                model, compact, refusal[:500])
                    return None, "LLM_EXPLAIN_REFUSAL"

                text = self._extract_content_text(data)
                if not text:
                    return None, "LLM_EXPLAIN_EMPTY"
                log.info("[feature2][llm] OpenAI raw explain model=%s compact=%s %s", model, compact, text[:2000])
                return text, None

        except httpx.TimeoutException:
            return None, "LLM_EXPLAIN_TIMEOUT"
        except Exception as exc:
            return None, f"LLM_EXPLAIN_EXCEPTION:{type(exc).__name__}:{str(exc)[:120]}"

    def _resolve_model(self, vendor_label: Optional[str]) -> str:
        if not vendor_label:
            return self.default_model

        key = vendor_label.strip().lower()
        mapped = {
            "gpt-5.4": "gpt-5.4",
            "gpt-5.2": "gpt-5.2",
            "gpt-5 mini": "gpt-5-mini",
            "gpt-4.1": "gpt-4.1",
            "gpt-4o": "gpt-4o",
        }
        return mapped.get(key, self.default_model)

    @staticmethod
    def _extract_content_text(data: dict) -> Optional[str]:
        output = data.get("output") or []
        if not isinstance(output, list):
            return None

        chunks: list[str] = []
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict):
                    continue
                if part.get("type") == "output_text" and isinstance(part.get("text"), str):
                    chunks.append(part["text"])

        if chunks:
            return "".join(chunks).strip()

        output_text = data.get("output_text")
        if isinstance(output_text, str) and output_text.strip():
            return output_text.strip()

        return None

    @staticmethod
    def _extract_refusal_text(data: dict) -> Optional[str]:
        output = data.get("output") or []
        if not isinstance(output, list):
            return None

        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict):
                    continue
                if part.get("type") == "refusal":
                    refusal = part.get("refusal")
                    if isinstance(refusal, str) and refusal.strip():
                        return refusal.strip()
        return None

    def _build_prompt(self, req: Feature1Request, metrics: Feature1Metrics) -> str:
        o = metrics.ohlcv_summary
        market_snapshot = metrics.market_snapshot
        indicator_summary = metrics.indicator_summary or "indicator summary unavailable"
        financial_summary = self._build_financial_summary(req)

        return (
            "Write sectioned explanations in Korean.\n"
            "Each section must only use its own data.\n"
            "Keep wording concise and factual.\n\n"
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
            f"{indicator_summary}\n\n"
            "[FINANCIAL_SUMMARY]\n"
            f"{financial_summary}\n"
        )

    def _build_financial_summary(self, req: Feature1Request) -> str:
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

    @staticmethod
    def _build_response_schema(compact: bool) -> dict:
        def section_schema() -> dict:
            return {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "summary": {"type": "string"},
                },
                "required": ["summary"],
            }

        return {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "sections": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "price_flow": section_schema(),
                        "market_snapshot": section_schema(),
                        "indicators": section_schema(),
                        "financial_timeline": section_schema(),
                    },
                    "required": [
                        "price_flow",
                        "market_snapshot",
                        "indicators",
                        "financial_timeline",
                    ],
                },
                "overall": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "summary": {"type": "string"},
                        "conclusion": {"type": "string"},
                    },
                    "required": ["summary", "conclusion"],
                },
            },
            "required": ["sections", "overall"],
        }


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


def _extract_request_id(body: str) -> Optional[str]:
    marker = "req_"
    start = body.find(marker)
    if start == -1:
        return None
    end = start + len(marker)
    while end < len(body) and (body[end].isalnum() or body[end] in {"_", "-"}):
        end += 1
    return body[start:end]
