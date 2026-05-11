from __future__ import annotations

import json
import os

import httpx

from app.models.feature3 import Feature3ExplainResult, Feature3Warning, PortfolioAnalyzeResponse


async def generate_feature3_explain(
    response: PortfolioAnalyzeResponse,
    vendor: str | None,
) -> Feature3ExplainResult:
    # 현재 구현: LLM은 계산하지 않고, FastAPI가 만든 정제 summary/riskDrivers 숫자만 문장화한다.
    # 진행 예정: prompt/schema를 더 엄격히 분리해 Basic/Advanced 설명 톤을 별도로 관리한다.
    prompt = _prompt(response)
    normalized_vendor = (vendor or os.getenv("LLM_VENDOR", "gemini")).strip().lower()
    if normalized_vendor == "openai":
        return await _openai(prompt)
    return await _gemini(prompt)


def deterministic_feature3_explain(response: PortfolioAnalyzeResponse) -> Feature3ExplainResult:
    summary = response.summary
    text = (
        f"현재 포트폴리오의 연 변동성은 {summary.annualized_volatility:.1%}이고 "
        f"투자 성향 기준은 {summary.target_volatility:.1%}입니다. "
        f"판정은 {summary.suitability}이며, 주요 위험 요인은 "
        f"{', '.join(summary.main_risk_drivers) if summary.main_risk_drivers else '크게 감지되지 않음'}입니다."
    )
    return Feature3ExplainResult(provider="DETERMINISTIC", text=text, warnings=[])


async def _openai(prompt: str) -> Feature3ExplainResult:
    api_key = os.getenv("OPENAI_API_KEY")
    model = os.getenv("OPENAI_MODEL", "gpt-5-mini")
    if not api_key:
        return _fallback("OPENAI", model, "LLM_API_KEY_MISSING")
    payload = {
        "model": model,
        "instructions": "제공된 Feature3 요약 데이터만 사용해 한국어로 설명하세요. 새 숫자나 근거를 만들지 마세요.",
        "input": prompt,
        "max_output_tokens": 900,
    }
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            resp = await client.post(
                "https://api.openai.com/v1/responses",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload,
            )
            if resp.status_code >= 400:
                return _fallback("OPENAI", model, f"LLM_EXPLAIN_HTTP_{resp.status_code}")
            data = resp.json()
            text = _extract_openai_text(data)
            if not text:
                return _fallback("OPENAI", model, "LLM_EXPLAIN_EMPTY")
            return Feature3ExplainResult(provider="OPENAI", model=model, text=text, warnings=[])
    except Exception as exc:
        return _fallback("OPENAI", model, f"LLM_EXPLAIN_EXCEPTION:{exc.__class__.__name__}")


async def _gemini(prompt: str) -> Feature3ExplainResult:
    api_key = os.getenv("GEMINI_API_KEY")
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    if not api_key:
        return _fallback("GEMINI", model, "LLM_API_KEY_MISSING")
    payload = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.1, "maxOutputTokens": 900},
    }
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}",
                json=payload,
            )
            if resp.status_code >= 400:
                return _fallback("GEMINI", model, f"LLM_EXPLAIN_HTTP_{resp.status_code}")
            data = resp.json()
            text = (((data.get("candidates") or [{}])[0].get("content") or {}).get("parts") or [{}])[0].get("text")
            if not text:
                return _fallback("GEMINI", model, "LLM_EXPLAIN_EMPTY")
            return Feature3ExplainResult(provider="GEMINI", model=model, text=text.strip(), warnings=[])
    except Exception as exc:
        return _fallback("GEMINI", model, f"LLM_EXPLAIN_EXCEPTION:{exc.__class__.__name__}")


def _prompt(response: PortfolioAnalyzeResponse) -> str:
    payload = {
        "summary": response.summary.model_dump(mode="json"),
        "currentPortfolio": response.current_portfolio.model_dump(mode="json"),
        "basicPortfolios": [portfolio.model_dump(mode="json") for portfolio in response.basic_portfolios],
        "riskDrivers": [driver.model_dump(mode="json") for driver in response.risk_drivers],
        "warnings": [warning.model_dump(mode="json") for warning in response.warnings],
    }
    return (
        "Feature3 포트폴리오 리스크 분석 결과를 일반 사용자에게 설명한다.\n"
        "금지: 공분산, Ledoit-Wolf, 감마, 효용함수 같은 내부 수학 용어를 먼저 쓰지 않는다.\n"
        "반드시 제공된 JSON의 숫자와 경고만 사용한다.\n"
        f"JSON:\n{json.dumps(payload, ensure_ascii=False)}"
    )


def _extract_openai_text(data: dict) -> str | None:
    if data.get("output_text"):
        return str(data["output_text"]).strip()
    for item in data.get("output") or []:
        for content in item.get("content") or []:
            if content.get("type") in {"output_text", "text"} and content.get("text"):
                return str(content["text"]).strip()
    return None


def _fallback(provider: str, model: str | None, code: str) -> Feature3ExplainResult:
    return Feature3ExplainResult(
        provider=provider,
        model=model,
        text=None,
        warnings=[
            Feature3Warning(
                code=code,
                message=f"Feature3 LLM explain failed: {code}",
                user_message="AI 설명 생성이 실패해 계산 결과와 기본 설명을 우선 표시합니다.",
                severity="WARN",
                target="explain",
            )
        ],
    )
