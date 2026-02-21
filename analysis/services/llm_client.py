# app/services/llm_client.py
from __future__ import annotations

import os
from datetime import datetime, timezone
from typing import List, Optional, Tuple

import httpx

from models.feature1 import (
    Feature1Explain,
    Feature1Meta,
    Feature1Metrics,
    Feature1Request,
    Feature1Response,
    FinancialSummary,
    IndicatorSlots,
    OhlcvSummary,
)


OPENAI_URL = "https://api.openai.com/v1/chat/completions"
DEFAULT_MODEL = "gpt-4o-mini"
DEFAULT_TIMEOUT = 6.0


async def analyze_feature1(req: Feature1Request) -> Feature1Response:
    metrics = _build_metrics(req)
    warnings: List[str] = []
    explain: Optional[Feature1Explain] = None

    if req.include_explain:
        text, warning = await _generate_explain_text(req, metrics)
        if text:
            explain = Feature1Explain(text=text)
        if warning:
            warnings.append(warning)
    else:
        warnings.append("LLM_EXPLAIN_SKIPPED")

    meta = Feature1Meta(warnings=warnings) if warnings else None
    return Feature1Response(metrics=metrics, explain=explain, meta=meta)


def _build_metrics(req: Feature1Request) -> Feature1Metrics:
    ohlcv_summary = _build_ohlcv_summary(req)
    financial_summary = _build_financial_summary(req)

    return Feature1Metrics(
        stock_code=req.stock_code,
        as_of=datetime.now(timezone.utc),
        ohlcv_summary=ohlcv_summary,
        financial_summary=financial_summary,
        indicators=IndicatorSlots(),
        schema_version="0.1",
    )


def _build_ohlcv_summary(req: Feature1Request) -> OhlcvSummary:
    if not req.ohlcv:
        return OhlcvSummary(count=0)

    sorted_ohlcv = sorted(req.ohlcv, key=lambda item: item.t)
    first = sorted_ohlcv[0]
    last = sorted_ohlcv[-1]

    return OhlcvSummary(
        count=len(req.ohlcv),
        from_=first.t,
        to=last.t,
        last_close=last.c,
    )


def _build_financial_summary(req: Feature1Request) -> FinancialSummary:
    revenue = {}
    operating_income = {}
    net_income = {}
    years: List[int] = []

    for item in req.financials:
        if item.fiscal_year is None:
            continue
        year = int(item.fiscal_year)
        if year not in years:
            years.append(year)
        revenue[year] = item.revenue
        operating_income[year] = item.operating_income
        net_income[year] = item.net_income

    years.sort()
    return FinancialSummary(
        years=years,
        revenue=revenue,
        operating_income=operating_income,
        net_income=net_income,
    )


async def _generate_explain_text(
    req: Feature1Request, metrics: Feature1Metrics
) -> Tuple[Optional[str], Optional[str]]:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        return None, "LLM_EXPLAIN_SKIPPED"

    prompt = _build_prompt(req, metrics)
    payload = {
        "model": os.getenv("OPENAI_MODEL", DEFAULT_MODEL),
        "messages": [
            {
                "role": "system",
                "content": "당신은 금융 분석 요약을 작성하는 어시스턴트입니다.",
            },
            {
                "role": "user",
                "content": prompt,
            },
        ],
        "temperature": 0.3,
        "max_tokens": 300,
    }

    timeout = float(os.getenv("OPENAI_TIMEOUT", DEFAULT_TIMEOUT))

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                OPENAI_URL,
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
            content = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
                .strip()
            )
            if not content:
                return None, "LLM_EXPLAIN_FAILED"
            return content, None
    except (httpx.HTTPError, ValueError):
        return None, "LLM_EXPLAIN_FAILED"


def _build_prompt(req: Feature1Request, metrics: Feature1Metrics) -> str:
    ohlcv = metrics.ohlcv_summary
    financial = metrics.financial_summary

    return (
        "다음 정보를 바탕으로 간단한 설명/요약 텍스트를 작성해주세요. "
        "수치 자체를 변경하거나 단정적인 투자 권고를 하지 마세요.\n\n"
        f"종목 코드: {req.stock_code}\n"
        f"OHLCV 요약: count={ohlcv.count}, from={ohlcv.from_}, to={ohlcv.to}, "
        f"last_close={ohlcv.last_close}\n"
        f"재무 요약(연도): {financial.years}\n"
        f"매출: {financial.revenue}\n"
        f"영업이익: {financial.operating_income}\n"
        f"순이익: {financial.net_income}\n"
    )
