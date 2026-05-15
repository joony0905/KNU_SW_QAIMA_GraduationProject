from __future__ import annotations

import json
import logging
import os

import httpx

from app.models.feature3 import Feature3ExplainResult, Feature3Warning, PortfolioAnalyzeResponse

log = logging.getLogger(__name__)


def _normalize_vendor(vendor_label: str | None) -> str:
    vendor_key = (vendor_label or os.getenv("LLM_VENDOR", "gemini")).strip().lower()
    if vendor_key in {
        "openai",
        "gpt-5.4",
        "gpt-5.2",
        "gpt-5 mini",
        "gpt-5-mini",
        "gpt-4.1",
        "gpt-4o",
        "gpt4o",
    }:
        return "openai"
    if vendor_key in {
        "gemini",
        "gemini 3.1 pro",
        "gemini 3 pro",
        "gemini 3 flash",
        "gemini 3.1 flash lite",
        "gemini 2.5 flash",
        "gemini 2.5 pro",
    }:
        return "gemini"
    log.warning("[feature3][llm] unsupported vendor label=%s, falling back to gemini", vendor_label)
    return "gemini"


def _resolve_openai_model(vendor_label: str | None) -> str:
    default_model = os.getenv("OPENAI_MODEL", "gpt-5-mini")
    if not vendor_label:
        return default_model
    mapped = {
        "gpt-5.4": "gpt-5.4",
        "gpt-5.2": "gpt-5.2",
        "gpt-5 mini": "gpt-5-mini",
        "gpt-5-mini": "gpt-5-mini",
        "gpt-4.1": "gpt-4.1",
        "gpt-4o": "gpt-4o",
        "gpt4o": "gpt-4o",
    }
    return mapped.get(vendor_label.strip().lower(), default_model)


def _resolve_gemini_model(vendor_label: str | None) -> str:
    default_model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    if not vendor_label:
        return default_model
    mapped = {
        "gemini 3.1 pro": "gemini-3.1-pro",
        "gemini 3 pro": "gemini-3-pro",
        "gemini 3 flash": "gemini-3-flash",
        "gemini 3.1 flash lite": "gemini-3.1-flash-lite",
        "gemini 2.5 flash": "gemini-2.5-flash",
        "gemini 2.5 pro": "gemini-2.5-pro",
    }
    return mapped.get(vendor_label.strip().lower(), default_model)


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


async def generate_feature3_explain(
    response: PortfolioAnalyzeResponse,
    vendor: str | None,
) -> Feature3ExplainResult:
    prompt = _prompt(response)
    normalized_vendor = _normalize_vendor(vendor)
    log.info(
        "[feature3][llm] start requested_vendor=%s normalized_vendor=%s prompt_chars=%s overlays=%s adjusted_portfolios=%s",
        vendor,
        normalized_vendor,
        len(prompt),
        len(response.overlays.overlay_signals),
        len(response.overlays.adjusted_portfolios),
    )
    if normalized_vendor == "openai":
        return await _openai(prompt, vendor)
    return await _gemini(prompt, vendor)


def deterministic_feature3_explain(response: PortfolioAnalyzeResponse) -> Feature3ExplainResult:
    summary = response.summary
    text = (
        f"현재 포트폴리오의 연 변동성은 {summary.annualized_volatility:.1%}이고 "
        f"투자 성향 기준은 {summary.target_volatility:.1%}입니다. "
        f"판정은 {summary.suitability}이며, 주요 위험 요인은 "
        f"{', '.join(summary.main_risk_drivers) if summary.main_risk_drivers else '크게 감지되지 않음'}입니다."
    )
    return Feature3ExplainResult(
        provider="DETERMINISTIC",
        text=text,
        sections={
            "core_risk": {
                "title": "핵심 리스크",
                "summary": text,
                "bullets": summary.main_risk_drivers[:3],
            },
            "overlay_observations": {
                "title": "보조 관측",
                "summary": "선택한 보조 관측은 계산 결과와 분리해 참고 신호로 해석합니다.",
                "bullets": [],
            },
            "portfolio_comparison": {
                "title": "포트폴리오 비교",
                "summary": "기본 포트폴리오와 보조 관측 시나리오를 함께 비교해 볼 수 있습니다.",
                "bullets": [],
            },
            "volatility_analysis": {
                "title": "변동성 기반 분석",
                "summary": text,
                "bullets": [],
            },
            "efficiency_analysis": {
                "title": "효율성 기반 분석",
                "summary": "위험 대비 수익 효율은 변동성과 기대수익률을 함께 비교해 참고합니다.",
                "bullets": [],
            },
            "final_judgement": {
                "title": "종합 판단",
                "summary": text,
                "bullets": [],
            },
        },
        overall={
            "summary": text,
            "bullets": summary.main_risk_drivers[:3],
            "risks": summary.main_risk_drivers[:3],
            "conclusion": text,
        },
        warnings=[],
    )


async def _openai(prompt: str, vendor_label: str | None) -> Feature3ExplainResult:
    api_key = os.getenv("OPENAI_API_KEY")
    model = _resolve_openai_model(vendor_label)
    timeout = _float_env("FEATURE3_OPENAI_TIMEOUT", _float_env("OPENAI_TIMEOUT", 45.0))
    if not api_key:
        log.warning("[feature3][llm] OpenAI API key missing model=%s", model)
        return _fallback("OPENAI", model, "LLM_API_KEY_MISSING")
    payload = {
        "model": model,
        "instructions": "제공된 Feature3 요약 데이터만 사용해 한국어로 설명하세요. 새 숫자나 근거를 만들지 마세요.",
        "input": prompt,
        "max_output_tokens": _int_env("FEATURE3_OPENAI_MAX_OUTPUT_TOKENS", 4200),
        "reasoning": {"effort": os.getenv("FEATURE3_OPENAI_REASONING_EFFORT", "low")},
        "text": {
            "format": {
                "type": "json_schema",
                "name": "feature3_explain",
                "strict": True,
                "schema": _openai_response_schema(),
            },
            "verbosity": "low",
        },
    }
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            log.info("[feature3][llm] OpenAI request model=%s input_chars=%s timeout=%s", model, len(prompt), timeout)
            resp = await client.post(
                "https://api.openai.com/v1/responses",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload,
            )
            if resp.status_code >= 400:
                log.warning("[feature3][llm] OpenAI HTTP %s model=%s body=%s", resp.status_code, model, resp.text[:1200])
                return _fallback("OPENAI", model, f"LLM_EXPLAIN_HTTP_{resp.status_code}")
            data = resp.json()
            usage = data.get("usage") or {}
            if isinstance(usage, dict):
                reasoning_tokens = ((usage.get("output_tokens_details") or {}).get("reasoning_tokens"))
                log.info(
                    "[feature3][llm] OpenAI usage model=%s status=%s input_tokens=%s output_tokens=%s reasoning_tokens=%s",
                    model,
                    data.get("status"),
                    usage.get("input_tokens"),
                    usage.get("output_tokens"),
                    reasoning_tokens,
                )
            if data.get("status") == "incomplete":
                reason = (((data.get("incomplete_details") or {}).get("reason")) or "unknown")
                log.warning(
                    "[feature3][llm] OpenAI incomplete model=%s reason=%s raw=%s",
                    model,
                    reason,
                    json.dumps(data, ensure_ascii=False)[:1600],
                )
                if reason == "max_output_tokens":
                    return _fallback("OPENAI", model, "LLM_EXPLAIN_MAX_OUTPUT_TOKENS")
                if reason == "content_filter":
                    return _fallback("OPENAI", model, "LLM_EXPLAIN_CONTENT_FILTER")
                return _fallback("OPENAI", model, f"LLM_EXPLAIN_INCOMPLETE:{reason}")
            text = _extract_openai_text(data)
            if not text:
                log.warning("[feature3][llm] OpenAI empty text model=%s raw=%s", model, json.dumps(data, ensure_ascii=False)[:1200])
                return _fallback("OPENAI", model, "LLM_EXPLAIN_EMPTY")
            log.info("[feature3][llm] OpenAI response model=%s text_chars=%s raw_prefix=%s", model, len(text), text[:500])
            parsed = _parse_explain_json(text)
            if parsed is None:
                log.warning("[feature3][llm] OpenAI parse failed model=%s raw=%s", model, text[:1500])
                return _fallback("OPENAI", model, "LLM_EXPLAIN_PARSE_FAILED")
            parsed.provider = "OPENAI"
            parsed.model = model
            parsed.text = text.strip()
            return parsed
    except httpx.TimeoutException:
        log.warning("[feature3][llm] OpenAI timeout model=%s timeout=%s", model, timeout)
        return _fallback("OPENAI", model, "LLM_EXPLAIN_TIMEOUT")
    except Exception as exc:
        log.exception("[feature3][llm] OpenAI exception model=%s", model)
        return _fallback("OPENAI", model, f"LLM_EXPLAIN_EXCEPTION:{exc.__class__.__name__}")


async def _gemini(prompt: str, vendor_label: str | None) -> Feature3ExplainResult:
    api_key = os.getenv("GEMINI_API_KEY")
    model = _resolve_gemini_model(vendor_label)
    timeout = _float_env("FEATURE3_GEMINI_TIMEOUT", 20.0)
    if not api_key:
        log.warning("[feature3][llm] Gemini API key missing model=%s", model)
        return _fallback("GEMINI", model, "LLM_API_KEY_MISSING")
    payload = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.1, "maxOutputTokens": 1800},
    }
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            log.info("[feature3][llm] Gemini request model=%s input_chars=%s timeout=%s", model, len(prompt), timeout)
            resp = await client.post(
                f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}",
                json=payload,
            )
            if resp.status_code >= 400:
                log.warning("[feature3][llm] Gemini HTTP %s model=%s body=%s", resp.status_code, model, resp.text[:1200])
                return _fallback("GEMINI", model, f"LLM_EXPLAIN_HTTP_{resp.status_code}")
            data = resp.json()
            text = (((data.get("candidates") or [{}])[0].get("content") or {}).get("parts") or [{}])[0].get("text")
            if not text:
                log.warning("[feature3][llm] Gemini empty text model=%s raw=%s", model, json.dumps(data, ensure_ascii=False)[:1200])
                return _fallback("GEMINI", model, "LLM_EXPLAIN_EMPTY")
            log.info("[feature3][llm] Gemini response model=%s text_chars=%s raw_prefix=%s", model, len(text), text[:500])
            parsed = _parse_explain_json(text)
            if parsed is None:
                log.warning("[feature3][llm] Gemini parse failed model=%s raw=%s", model, text[:1500])
                return _fallback("GEMINI", model, "LLM_EXPLAIN_PARSE_FAILED")
            parsed.provider = "GEMINI"
            parsed.model = model
            parsed.text = text.strip()
            return parsed
    except httpx.TimeoutException:
        log.warning("[feature3][llm] Gemini timeout model=%s timeout=%s", model, timeout)
        return _fallback("GEMINI", model, "LLM_EXPLAIN_TIMEOUT")
    except Exception as exc:
        log.exception("[feature3][llm] Gemini exception model=%s", model)
        return _fallback("GEMINI", model, f"LLM_EXPLAIN_EXCEPTION:{exc.__class__.__name__}")


def _openai_response_schema() -> dict:
    section_schema = {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "title": {"type": "string"},
            "summary": {"type": "string"},
            "bullets": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["title", "summary", "bullets"],
    }
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "sections": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "core_risk": section_schema,
                    "overlay_observations": section_schema,
                    "portfolio_comparison": section_schema,
                    "volatility_analysis": section_schema,
                    "efficiency_analysis": section_schema,
                    "final_judgement": section_schema,
                },
                "required": [
                    "core_risk",
                    "overlay_observations",
                    "portfolio_comparison",
                    "volatility_analysis",
                    "efficiency_analysis",
                    "final_judgement",
                ],
            },
            "overall": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "summary": {"type": "string"},
                    "bullets": {"type": "array", "items": {"type": "string"}},
                    "risks": {"type": "array", "items": {"type": "string"}},
                    "conclusion": {"type": ["string", "null"]},
                },
                "required": ["summary", "bullets", "risks", "conclusion"],
            },
        },
        "required": ["sections", "overall"],
    }


def _prompt(response: PortfolioAnalyzeResponse) -> str:
    payload = {
        "sectionData": _explain_context(response),
    }
    return (
        "Feature3 포트폴리오 리스크 분석 결과를 일반 사용자에게 설명한다.\n"
        "금지: 공분산, Ledoit-Wolf, 감마, 효용함수 같은 내부 수학 용어를 먼저 쓰지 않는다.\n"
        "반드시 제공된 JSON의 숫자와 경고만 사용한다. 새 숫자, 새 종목, 새 원인은 만들지 않는다.\n"
        "sectionData만 근거로 사용한다.\n"
        "데이터가 부족한 섹션은 단정하지 말고 '확인 가능한 범위에서는'처럼 제한적으로 설명한다.\n"
        "insightContext는 단순 요약이 아니라 압력의 의미를 해석하기 위한 우선 근거로 사용한다.\n"
        "insightContext의 interpretationCue는 그대로 복사하지 말고, 섹션별 문맥에 맞춰 자연스럽게 풀어서 설명한다.\n"
        "가능하면 각 섹션 summary에는 관련 종목명 또는 포트폴리오명을 최소 1개 포함해 사용자가 화면 카드와 연결해 읽을 수 있게 한다.\n"
        "core_risk는 currentPortfolio, topRiskContributors, riskDrivers를 근거로 쓴다.\n"
        "volatility_analysis는 volatilityAnalysis.portfolios와 summary의 목표 변동성 차이를 근거로 쓴다.\n"
        "efficiency_analysis는 efficiencyAnalysis.portfolios의 Sharpe, expectedReturn, volatility를 근거로 위험 대비 효율을 설명한다.\n"
        "overlay_observations는 overlaySignals, overlayExplanations, overlayVisualizations를 근거로 체크된 보조 관측의 압력 방향을 설명한다.\n"
        "portfolio_comparison은 adjustedPortfolioComparisons의 weightChanges와 volatility를 근거로 하되, 행동 지시가 아니라 압력 반영 예시로 설명한다.\n"
        "final_judgement는 위 세 섹션을 종합하되 매수/매도/리밸런싱 추천처럼 쓰지 않는다.\n"
        "단순 데이터 나열을 피하고, 어떤 리스크 압력/품질 압력/뉴스 압력/분산 압력이 관측되는지와 그 의미를 설명한다.\n"
        "'늘리세요', '줄이세요', '매수', '매도', '추천 비중' 같은 행동 권고 표현은 금지한다.\n"
        "대신 '확대 압력', '축소 압력', '분산 압력', '품질 경고', '관찰 필요' 같은 표현을 사용한다.\n"
        "각 sections.core_risk.summary, sections.overlay_observations.summary, sections.portfolio_comparison.summary, sections.volatility_analysis.summary, sections.efficiency_analysis.summary는 정확히 한국어 4문장으로 작성한다.\n"
        "sections.final_judgement.summary는 정확히 한국어 8문장으로 작성한다.\n"
        "문장 수는 마침표 기준으로 세며, 각 summary 안에서 줄바꿈이나 bullet 형식은 사용하지 않는다.\n"
        "bullets 배열은 빈 배열로 둔다. 설명은 summary에만 작성한다.\n"
        "overall.summary는 final_judgement.summary와 동일하게 둔다. overall.bullets와 overall.risks는 빈 배열로 둔다.\n"
        "adjustedPortfolios는 추천/최적 포트폴리오가 아니라 Overlay-adjusted Simulation이다.\n"
        "보조 관측 시나리오는 core risk 최적화 결과를 대체하지 않고, 선택한 보조 관측이 비중에 주는 압력을 제한적으로 보여준다.\n"
        "halfTurnoverBudget과 singleNameDeltaCap을 넘는 강한 결론을 쓰지 않는다.\n"
        "반드시 아래 JSON 구조만 출력한다. 마크다운 코드펜스와 추가 문장은 금지한다.\n"
        "{\n"
        '  "sections": {\n'
        '    "core_risk": {"title": "핵심 리스크", "summary": "정확히 4문장.", "bullets": []},\n'
        '    "overlay_observations": {"title": "보조 관측", "summary": "정확히 4문장.", "bullets": []},\n'
        '    "portfolio_comparison": {"title": "포트폴리오 비교", "summary": "정확히 4문장.", "bullets": []},\n'
        '    "volatility_analysis": {"title": "변동성 기반 분석", "summary": "정확히 4문장.", "bullets": []},\n'
        '    "efficiency_analysis": {"title": "효율성 기반 분석", "summary": "정확히 4문장.", "bullets": []},\n'
        '    "final_judgement": {"title": "종합 판단", "summary": "정확히 8문장.", "bullets": []}\n'
        "  },\n"
        '  "overall": {"summary": "final_judgement.summary와 동일", "bullets": [], "risks": [], "conclusion": null}\n'
        "}\n"
        f"JSON:\n{json.dumps(payload, ensure_ascii=False, separators=(',', ':'))}"
    )


def _explain_context(response: PortfolioAnalyzeResponse) -> dict:
    return {
        "insightContext": _insight_context(response),
        "coreRisk": {
            "summary": response.summary.model_dump(mode="json"),
            "currentPortfolio": _portfolio_brief(response.current_portfolio),
            "topRiskContributors": _top_risk_contributors(response.current_portfolio),
            "riskDrivers": _brief_risk_drivers(response),
            "dataQuality": {
                "includedHoldingCount": response.policy.data_quality.included_holding_count,
                "excludedHoldingCount": response.policy.data_quality.excluded_holding_count,
                "commonReturnSampleSize": response.policy.data_quality.common_return_sample_size,
                "commonMissingRate": response.policy.data_quality.common_missing_rate,
            },
        },
        "volatilityAnalysis": {
            "targetVolatility": response.summary.target_volatility,
            "currentVolatility": response.summary.annualized_volatility,
            "volatilityGap": response.summary.volatility_gap,
            "suitability": response.summary.suitability,
            "portfolios": _volatility_portfolios(response),
        },
        "efficiencyAnalysis": {
            "riskFreeRate": response.policy.risk_free_policy.rate,
            "riskFreeSource": response.policy.risk_free_policy.source,
            "portfolios": _efficiency_portfolios(response),
        },
        "overlayObservations": {
            "selectedOverlayTypes": sorted({signal.overlay_type for signal in response.overlays.overlay_signals}),
            "signalsByType": _signals_by_type(response),
            "overlayExplanations": _brief_overlay_explanations(response),
            "overlayVisualizations": _brief_visualizations(response),
        },
        "portfolioComparison": {
            "basicPortfolios": [_portfolio_brief(portfolio) for portfolio in response.basic_portfolios],
            "adjustedPortfolioComparisons": _adjusted_portfolio_comparisons(response),
            "overlayScenarioPolicy": {
                "name": "Overlay Scenario Policy v1",
                "role": "simulation_not_recommendation",
                "halfTurnoverBudget": 0.09,
                "singleNameDeltaCap": 0.035,
                "cashPolicy": "preserve_core_cash_weight",
            },
        },
        "finalJudgementInputs": {
            "warnings": _brief_warnings(response),
            "freshness": response.freshness.model_dump(mode="json"),
            "candidateTakeaways": _candidate_takeaways(response),
        },
    }


def _insight_context(response: PortfolioAnalyzeResponse) -> dict:
    return {
        "portfolioShape": _portfolio_shape_insight(response),
        "volatilityPressure": _volatility_pressure_insight(response),
        "efficiencyPressure": _efficiency_pressure_insight(response),
        "overlayPressure": _overlay_pressure_insight(response),
        "scenarioImpact": _scenario_impact_insight(response),
        "sectionInsightHints": _section_insight_hints(response),
    }


def _portfolio_shape_insight(response: PortfolioAnalyzeResponse) -> dict:
    current = response.current_portfolio
    weights = [weight for weight in current.weights if weight.asset_type != "CASH"]
    top_weights = sorted(weights, key=lambda item: item.weight, reverse=True)
    top_risk = sorted(current.risk_contributions, key=lambda item: item.risk_contribution_pct, reverse=True)
    largest_weight = top_weights[0] if top_weights else None
    largest_risk = top_risk[0] if top_risk else None
    top3_weight = sum(weight.weight for weight in top_weights[:3])
    concentration_tone = "balanced"
    if largest_weight and largest_weight.weight >= 0.45:
        concentration_tone = "single_name_dominant"
    elif top3_weight >= 0.75:
        concentration_tone = "top3_concentrated"
    return {
        "riskLevel": response.summary.risk_level,
        "suitability": response.summary.suitability,
        "currentVolatility": response.summary.annualized_volatility,
        "targetVolatility": response.summary.target_volatility,
        "volatilityGap": response.summary.volatility_gap,
        "cashWeight": _cash_weight(current),
        "top3Weight": top3_weight,
        "concentrationTone": concentration_tone,
        "largestWeight": _weight_item(largest_weight),
        "largestRiskContributor": _risk_item(largest_risk),
        "interpretationCue": _portfolio_shape_cue(response, concentration_tone, largest_risk),
    }


def _portfolio_shape_cue(response: PortfolioAnalyzeResponse, concentration_tone: str, largest_risk) -> str:
    cues = []
    if response.summary.volatility_gap > 0:
        cues.append("현재 변동성이 성향 기준보다 높아 방어 압력이 우선 관찰됩니다.")
    elif response.summary.volatility_gap < 0:
        cues.append("현재 변동성이 성향 기준보다 낮아 위험 여력이 남아 있는 구조입니다.")
    else:
        cues.append("현재 변동성은 성향 기준과 거의 맞닿아 있습니다.")
    if concentration_tone == "single_name_dominant":
        cues.append("단일 종목 비중이 커서 분산보다 특정 종목 노출 해석이 중요합니다.")
    elif concentration_tone == "top3_concentrated":
        cues.append("상위 종목군에 비중이 모여 있어 그룹 단위 리스크 해석이 필요합니다.")
    if largest_risk and largest_risk.risk_contribution_pct >= 0.4:
        name = largest_risk.company_name or largest_risk.stock_code
        cues.append(f"{name}의 위험 기여도가 커서 보유 비중보다 리스크 영향이 크게 보입니다.")
    return " ".join(cues)


def _volatility_pressure_insight(response: PortfolioAnalyzeResponse) -> dict:
    portfolios = _volatility_portfolios(response)
    target = response.summary.target_volatility
    closest = min(portfolios, key=lambda item: abs((item.get("volatility") or 0.0) - target), default=None)
    lowest = min(portfolios, key=lambda item: item.get("volatility") or 0.0, default=None)
    highest = max(portfolios, key=lambda item: item.get("volatility") or 0.0, default=None)
    if response.summary.volatility_gap > 0.015:
        pressure = "defensive_pressure"
    elif response.summary.volatility_gap < -0.015:
        pressure = "risk_capacity_remaining"
    else:
        pressure = "near_target"
    return {
        "pressure": pressure,
        "currentVsTargetGap": response.summary.volatility_gap,
        "closestToTarget": closest,
        "lowestVolatilityPortfolio": lowest,
        "highestVolatilityPortfolio": highest,
        "interpretationCue": _volatility_cue(response, pressure, closest),
    }


def _volatility_cue(response: PortfolioAnalyzeResponse, pressure: str, closest: dict | None) -> str:
    closest_label = closest.get("label") if closest else None
    if pressure == "defensive_pressure":
        return f"성향 기준보다 변동성이 높으므로 {closest_label or '목표 변동성에 가까운 안'}을 비교 기준으로 삼아 방어 압력을 설명합니다."
    if pressure == "risk_capacity_remaining":
        return f"성향 기준보다 변동성이 낮으므로 {closest_label or '목표 변동성에 가까운 안'}과 비교해 남아 있는 위험 여력을 설명합니다."
    return f"현재 변동성이 목표와 가까우므로 {closest_label or '인접한 대안'}의 차이는 방향성보다 안정성 확인 관점에서 설명합니다."


def _efficiency_pressure_insight(response: PortfolioAnalyzeResponse) -> dict:
    portfolios = _efficiency_portfolios(response)
    with_sharpe = [item for item in portfolios if item.get("sharpeRatio") is not None]
    best_sharpe = max(with_sharpe, key=lambda item: item.get("sharpeRatio") or -999.0, default=None)
    current = next((item for item in portfolios if item.get("type") == "CURRENT"), None)
    utility = next((item for item in portfolios if item.get("type") == "UTILITY_OPTIMAL"), None)
    theoretical = next((item for item in portfolios if item.get("type") == "THEORETICAL_UTILITY"), None)
    min_vol = next((item for item in portfolios if item.get("type") == "MIN_VOL"), None)
    return {
        "bestSharpePortfolio": best_sharpe,
        "currentPortfolio": current,
        "minVolPortfolio": min_vol,
        "utilityPortfolio": utility,
        "theoreticalPortfolio": theoretical,
        "constraintCue": _constraint_cue(theoretical),
        "interpretationCue": _efficiency_cue(current, best_sharpe, utility, theoretical),
    }


def _constraint_cue(portfolio: dict | None) -> str | None:
    if not portfolio:
        return None
    binding = portfolio.get("constraintBinding")
    if binding == "CASH_MAX":
        return "목표 효율 구성이 현금 한도에 걸려 있어 현재 설정만으로는 같은 구조를 그대로 만들기 어렵습니다."
    if binding == "RISKY_MAX":
        return "목표 효율 구성이 위험자산 한도에 가까워 공격적 해석보다 제약 확인이 필요합니다."
    if portfolio.get("additionalRequiredCash"):
        return "이론적 목표안은 추가 현금 투입 조건을 포함하므로 현재 보유 구조와 직접 비교하면 안 됩니다."
    return None


def _efficiency_cue(current: dict | None, best: dict | None, utility: dict | None, theoretical: dict | None) -> str:
    cues = []
    if current and best and current.get("type") != best.get("type"):
        cues.append(f"현재 포트폴리오와 {best.get('label') or best.get('type')}의 Sharpe 차이를 위험 대비 효율 압력으로 설명합니다.")
    elif best:
        cues.append("현재 포트폴리오가 효율성 비교에서 크게 밀리지 않는지 확인하는 관점으로 설명합니다.")
    if utility:
        cues.append(f"{utility.get('label') or '현재 조건 최적안'}은 제약 안에서의 효율 대안으로만 해석합니다.")
    if theoretical:
        cues.append("이론적 목표안은 현재 제약 밖의 참고점일 수 있으므로 행동 지시가 아니라 거리감 설명에 사용합니다.")
    return " ".join(cues)


def _overlay_pressure_insight(response: PortfolioAnalyzeResponse) -> dict:
    signals = response.overlays.overlay_signals
    by_type = {}
    by_holding = {}
    for signal in signals:
        by_type.setdefault(signal.overlay_type, []).append(signal)
        key = signal.stock_code
        bucket = by_holding.setdefault(key, {
            "stockCode": signal.stock_code,
            "companyName": signal.company_name,
            "netScore": 0.0,
            "positiveTypes": [],
            "negativeTypes": [],
            "warnTypes": [],
            "evidenceHighlights": [],
        })
        bucket["netScore"] += signal.score
        if signal.score > 0.05:
            bucket["positiveTypes"].append(signal.overlay_type)
        elif signal.score < -0.05:
            bucket["negativeTypes"].append(signal.overlay_type)
        if signal.severity == "WARN":
            bucket["warnTypes"].append(signal.overlay_type)
        if signal.evidence:
            bucket["evidenceHighlights"].append(_short_text(signal.evidence, 140))
    holding_items = sorted(
        by_holding.values(),
        key=lambda item: abs(float(item["netScore"])),
        reverse=True,
    )[:8]
    return {
        "selectedOverlayTypes": sorted(by_type.keys()),
        "typeSummaries": [_overlay_type_summary(overlay_type, items) for overlay_type, items in sorted(by_type.items())],
        "holdingPressure": holding_items,
        "crossSignalConflicts": _cross_signal_conflicts(holding_items),
        "interpretationCue": _overlay_cue(by_type, holding_items),
    }


def _overlay_type_summary(overlay_type: str, items: list) -> dict:
    scores = [signal.score for signal in items]
    avg_score = sum(scores) / len(scores) if scores else 0.0
    positive = [signal for signal in items if signal.score > 0.05]
    negative = [signal for signal in items if signal.score < -0.05]
    warns = [signal for signal in items if signal.severity == "WARN"]
    strongest = sorted(items, key=lambda signal: abs(signal.score), reverse=True)[:3]
    return {
        "overlayType": overlay_type,
        "count": len(items),
        "averageScore": avg_score,
        "pressureDirection": _pressure_direction(avg_score),
        "positiveCount": len(positive),
        "negativeCount": len(negative),
        "warnCount": len(warns),
        "strongestSignals": [
            {
                "stockCode": signal.stock_code,
                "companyName": signal.company_name,
                "score": signal.score,
                "severity": signal.severity,
                "label": signal.label,
                "evidence": _short_text(signal.evidence, 180),
            }
            for signal in strongest
        ],
        "interpretationCue": _overlay_type_cue(overlay_type, avg_score, warns),
    }


def _pressure_direction(score: float) -> str:
    if score >= 0.08:
        return "expansion_pressure"
    if score <= -0.08:
        return "reduction_pressure"
    return "observation_pressure"


def _overlay_type_cue(overlay_type: str, avg_score: float, warns: list) -> str:
    direction = _pressure_direction(avg_score)
    if overlay_type == "correlation":
        return "Peer corr은 설명 근거이며, 실제 분산 압력은 위험 기여도와 포트폴리오 내부 구조를 우선해서 해석합니다."
    if overlay_type == "industry":
        return "산업 신호는 업종 강세 예측이 아니라 같은 산업에 노출이 몰리는지 보는 집중도 참고 정보입니다."
    if overlay_type == "fundamentals":
        return "종목 체력 신호는 재무와 밸류에이션을 통해 품질 압력이 완화되는지 또는 경고되는지 설명합니다."
    if overlay_type == "technical":
        return "기술 신호는 단기 흐름 참고 정보라 강한 결론보다 과열·침체·추세 정렬 여부 중심으로 설명합니다."
    if overlay_type == "news":
        return "뉴스 신호는 기사 수와 감성 점수의 신뢰도를 함께 보며, 단기 경계 압력으로 제한해 설명합니다."
    if warns:
        return f"{direction}가 관찰되지만 WARN 신호가 있어 확인 필요 문맥으로 설명합니다."
    return f"{direction}가 관찰되는 보조 신호로 설명합니다."


def _cross_signal_conflicts(holding_items: list[dict]) -> list[dict]:
    conflicts = []
    for item in holding_items:
        if item["positiveTypes"] and item["negativeTypes"]:
            conflicts.append({
                "stockCode": item["stockCode"],
                "companyName": item["companyName"],
                "positiveTypes": sorted(set(item["positiveTypes"])),
                "negativeTypes": sorted(set(item["negativeTypes"])),
                "cue": "품질/흐름 신호와 리스크/경계 신호가 동시에 있어 단일 방향으로 단정하지 않습니다.",
            })
    return conflicts[:5]


def _overlay_cue(by_type: dict, holding_items: list[dict]) -> str:
    if not by_type:
        return "선택된 보조 관측이 없어 core risk 중심으로 설명합니다."
    types = ", ".join(sorted(by_type.keys()))
    high_pressure = [item for item in holding_items if abs(float(item["netScore"])) >= 0.25]
    if high_pressure:
        names = ", ".join((item["companyName"] or item["stockCode"]) for item in high_pressure[:3])
        return f"선택된 보조 관측은 {types}이며, {names}에서 상대적으로 강한 압력이 관찰됩니다."
    return f"선택된 보조 관측은 {types}이며, 강한 단일 결론보다 여러 신호의 균형을 설명합니다."


def _scenario_impact_insight(response: PortfolioAnalyzeResponse) -> dict:
    current_weights = {weight.stock_code: weight.weight for weight in response.current_portfolio.weights}
    scenarios = []
    for portfolio in response.overlays.adjusted_portfolios:
        changes = []
        for weight in portfolio.weights:
            before = current_weights.get(weight.stock_code, 0.0)
            delta = weight.weight - before
            if abs(delta) < 0.0005:
                continue
            changes.append({
                "stockCode": weight.stock_code,
                "companyName": weight.company_name,
                "assetType": weight.asset_type,
                "beforeWeight": before,
                "afterWeight": weight.weight,
                "deltaWeight": delta,
                "pressureDirection": "expansion_pressure" if delta > 0 else "reduction_pressure",
            })
        top_changes = sorted(changes, key=lambda item: abs(item["deltaWeight"]), reverse=True)
        scenarios.append({
            "type": portfolio.type,
            "label": portfolio.label,
            "volatility": portfolio.volatility,
            "halfTurnover": 0.5 * sum(abs(item["deltaWeight"]) for item in changes),
            "topExpansion": [item for item in top_changes if item["deltaWeight"] > 0][:3],
            "topReduction": [item for item in top_changes if item["deltaWeight"] < 0][:3],
            "interpretationCue": _scenario_cue(portfolio, top_changes),
        })
    return {
        "policy": {
            "role": "risk_stress_visualization",
            "notRecommendation": True,
            "halfTurnoverBudget": 0.09,
            "singleNameDeltaCap": 0.035,
            "cashPolicy": "preserve_core_cash_weight",
        },
        "scenarios": scenarios,
    }


def _scenario_cue(portfolio, changes: list[dict]) -> str:
    if not changes:
        return "보조 관측을 적용해도 의미 있는 비중 압력 변화가 제한적입니다."
    top = changes[0]
    name = top.get("companyName") or top.get("stockCode")
    direction = "확대 압력" if top.get("deltaWeight", 0.0) > 0 else "축소 압력"
    return f"{portfolio.label}에서는 {name}의 {direction}이 가장 크게 나타나지만, 이는 추천 비중이 아니라 제한된 시나리오 변화입니다."


def _section_insight_hints(response: PortfolioAnalyzeResponse) -> dict:
    return {
        "coreRisk": [
            "현재 위험 수준을 성향 기준과 비교해 먼저 설명합니다.",
            "보유 비중과 위험 기여도가 다른 종목이 있으면 그 차이를 짚습니다.",
        ],
        "volatilityAnalysis": [
            "변동성 기반 대안은 목표 변동성에 가까워지는 정도와 현금 비중 변화를 중심으로 설명합니다.",
            "LOW/MID/HIGH를 절대 등급처럼 말하지 말고 카드 간 상대 위험 수준으로 설명합니다.",
        ],
        "efficiencyAnalysis": [
            "Sharpe가 높은 안을 단순 우월안으로 말하지 말고, 변동성 증가와 기대수익 보상의 균형으로 설명합니다.",
            "이론적 목표안은 제약 밖 참고점일 수 있으므로 현재 조건 최적안과 구분합니다.",
        ],
        "overlayObservations": [
            "보조 관측은 수익 예측이 아니라 확대·축소·분산·품질 압력의 근거로 설명합니다.",
            "상충 신호가 있으면 단일 결론 대신 관찰 필요로 표현합니다.",
        ],
        "portfolioComparison": [
            "보조 관측 시나리오의 변화 폭은 half-turnover budget과 단일 종목 delta cap 안에서 제한적으로 설명합니다.",
            "시나리오 결과를 리밸런싱 지시처럼 쓰지 않습니다.",
        ],
        "finalJudgement": [
            "core risk를 우선하고 보조 관측은 해석 레이어로 정리합니다.",
            "사용자가 다음에 확인해야 할 압력의 우선순위를 말하되 행동 권고는 피합니다.",
        ],
    }


def _portfolio_brief(portfolio) -> dict:
    return {
        "type": portfolio.type,
        "label": portfolio.label,
        "riskLevel": portfolio.risk_level,
        "volatility": portfolio.volatility,
        "expectedReturn": portfolio.expected_return,
        "displayExpectedReturn": portfolio.display_expected_return,
        "sharpeRatio": portfolio.sharpe_ratio,
        "constraintBinding": portfolio.constraint_binding,
        "userDescription": portfolio.user_description,
        "topWeights": [
            {
                "stockCode": weight.stock_code,
                "companyName": weight.company_name,
                "assetType": weight.asset_type,
                "weight": weight.weight,
            }
            for weight in sorted(portfolio.weights, key=lambda item: item.weight, reverse=True)[:6]
        ],
    }


def _top_risk_contributors(portfolio) -> list[dict]:
    return [
        {
            "stockCode": item.stock_code,
            "companyName": item.company_name,
            "weight": item.weight,
            "riskContributionPct": item.risk_contribution_pct,
            "volatility": item.volatility,
        }
        for item in sorted(portfolio.risk_contributions, key=lambda row: row.risk_contribution_pct, reverse=True)[:6]
    ]


def _weight_item(weight) -> dict | None:
    if weight is None:
        return None
    return {
        "stockCode": weight.stock_code,
        "companyName": weight.company_name,
        "assetType": weight.asset_type,
        "weight": weight.weight,
    }


def _risk_item(item) -> dict | None:
    if item is None:
        return None
    return {
        "stockCode": item.stock_code,
        "companyName": item.company_name,
        "weight": item.weight,
        "riskContributionPct": item.risk_contribution_pct,
        "volatility": item.volatility,
    }


def _volatility_portfolios(response: PortfolioAnalyzeResponse) -> list[dict]:
    candidates = [
        response.current_portfolio,
        *[
            portfolio
            for portfolio in response.basic_portfolios
            if portfolio.type in {"STABLE", "BALANCED", "AGGRESSIVE", "PSYCHOLOGICAL"}
        ],
    ]
    return [
        {
            "type": portfolio.type,
            "label": portfolio.label,
            "riskLevel": portfolio.risk_level,
            "volatility": portfolio.volatility,
            "targetVolatility": portfolio.target_volatility,
            "achievedVolatility": portfolio.achieved_volatility,
            "cashWeight": _cash_weight(portfolio),
            "topWeights": _top_weight_brief(portfolio, 4),
        }
        for portfolio in candidates[:6]
    ]


def _efficiency_portfolios(response: PortfolioAnalyzeResponse) -> list[dict]:
    advanced = response.advanced.candidate_portfolios if response.advanced else []
    candidates = [
        response.current_portfolio,
        *[portfolio for portfolio in advanced if portfolio.type in {"MIN_VOL", "MAX_SHARPE", "UTILITY_OPTIMAL", "THEORETICAL_UTILITY"}],
    ]
    return [
        {
            "type": portfolio.type,
            "label": portfolio.label,
            "volatility": portfolio.volatility,
            "expectedReturn": portfolio.expected_return,
            "displayExpectedReturn": portfolio.display_expected_return,
            "sharpeRatio": portfolio.sharpe_ratio,
            "constraintBinding": portfolio.constraint_binding,
            "additionalRequiredCash": portfolio.additional_required_cash,
            "theoreticalRiskyAllocation": portfolio.theoretical_risky_allocation,
            "cashWeight": _cash_weight(portfolio),
            "topWeights": _top_weight_brief(portfolio, 4),
        }
        for portfolio in candidates[:6]
    ]


def _cash_weight(portfolio) -> float:
    return sum(weight.weight for weight in portfolio.weights if weight.asset_type == "CASH")


def _top_weight_brief(portfolio, limit: int) -> list[dict]:
    return [
        {
            "stockCode": weight.stock_code,
            "companyName": weight.company_name,
            "assetType": weight.asset_type,
            "weight": weight.weight,
        }
        for weight in sorted(portfolio.weights, key=lambda item: item.weight, reverse=True)[:limit]
    ]


def _signals_by_type(response: PortfolioAnalyzeResponse) -> dict:
    grouped: dict[str, list[dict]] = {}
    for signal in response.overlays.overlay_signals:
        grouped.setdefault(signal.overlay_type, []).append({
            "stockCode": signal.stock_code,
            "companyName": signal.company_name,
            "label": signal.label,
            "score": signal.score,
            "severity": signal.severity,
            "source": signal.source,
            "evidence": _short_text(signal.evidence, 180),
        })
    return {
        overlay_type: sorted(items, key=lambda item: abs(float(item["score"] or 0.0)), reverse=True)[:6]
        for overlay_type, items in grouped.items()
    }


def _adjusted_portfolio_comparisons(response: PortfolioAnalyzeResponse) -> list[dict]:
    current_weights = {weight.stock_code: weight.weight for weight in response.current_portfolio.weights}
    comparisons = []
    for portfolio in response.overlays.adjusted_portfolios:
        changes = []
        for weight in portfolio.weights:
            before = current_weights.get(weight.stock_code, 0.0)
            delta = weight.weight - before
            if abs(delta) < 0.0005:
                continue
            changes.append({
                "stockCode": weight.stock_code,
                "companyName": weight.company_name,
                "assetType": weight.asset_type,
                "beforeWeight": before,
                "afterWeight": weight.weight,
                "deltaWeight": delta,
            })
        comparisons.append({
            **_portfolio_brief(portfolio),
            "weightChanges": sorted(changes, key=lambda item: abs(item["deltaWeight"]), reverse=True)[:8],
        })
    return comparisons


def _candidate_takeaways(response: PortfolioAnalyzeResponse) -> list[str]:
    takeaways = []
    for portfolio in response.overlays.adjusted_portfolios[:3]:
        changes = _adjusted_portfolio_comparisons(response)
        matching = next((item for item in changes if item["type"] == portfolio.type), None)
        if not matching:
            continue
        top_change = matching["weightChanges"][0] if matching["weightChanges"] else None
        if top_change:
            direction = "확대 압력" if top_change["deltaWeight"] > 0 else "축소 압력"
            name = top_change["companyName"] or top_change["stockCode"]
            takeaways.append(f"{portfolio.label}: {name} {direction} 관측이 가장 큼")
    return takeaways


def _brief_risk_drivers(response: PortfolioAnalyzeResponse) -> list[dict]:
    return [
        {
            "code": driver.code,
            "source": driver.source,
            "severity": driver.severity,
            "title": driver.title,
            "description": _short_text(driver.description, 220),
            "affectedHoldings": driver.affected_holdings[:5],
        }
        for driver in response.risk_drivers[:8]
    ]


def _brief_overlay_explanations(response: PortfolioAnalyzeResponse) -> list[dict]:
    explanations = []
    for item in response.overlays.explanations[:10]:
        explanations.append({
            "title": item.get("title"),
            "severity": item.get("severity"),
            "score": item.get("score"),
            "description": _short_text(item.get("description"), 220),
        })
    return explanations


def _brief_visualizations(response: PortfolioAnalyzeResponse) -> list[dict]:
    visualizations = []
    for item in response.overlays.visualizations[:6]:
        entries = []
        for entry in (item.get("items") or item.get("entries") or [])[:6]:
            if isinstance(entry, dict):
                entries.append({
                    "stockCode": entry.get("stockCode") or entry.get("stock_code"),
                    "companyName": entry.get("companyName") or entry.get("company_name"),
                    "label": entry.get("label"),
                    "value": entry.get("value"),
                    "score": entry.get("score"),
                    "severity": entry.get("severity"),
                    "evidence": _short_text(entry.get("evidence") or entry.get("description"), 160),
                })
        visualizations.append({
            "type": item.get("type"),
            "title": item.get("title"),
            "description": _short_text(item.get("description"), 180),
            "items": entries,
        })
    return visualizations


def _brief_warnings(response: PortfolioAnalyzeResponse) -> list[dict]:
    return [
        {
            "code": warning.code,
            "severity": warning.severity,
            "target": warning.target,
            "userMessage": _short_text(warning.user_message or warning.message, 180),
        }
        for warning in response.warnings[:8]
    ]


def _short_text(value, limit: int) -> str | None:
    if value is None:
        return None
    text = str(value).replace("\n", " ").strip()
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip() + "…"


def _parse_explain_json(text: str) -> Feature3ExplainResult | None:
    payload = _extract_json_object(text)
    if not payload:
        log.warning("[feature3][llm] parse failed: no JSON object raw=%s", text[:800])
        return None
    try:
        data = json.loads(payload)
    except Exception as exc:
        log.warning("[feature3][llm] parse failed: json decode error=%s payload=%s", exc, payload[:1200])
        return None
    sections = data.get("sections")
    overall = data.get("overall")
    if not isinstance(sections, dict) or not isinstance(overall, dict):
        log.warning(
            "[feature3][llm] parse failed: missing sections/overall keys=%s payload=%s",
            list(data.keys()) if isinstance(data, dict) else type(data).__name__,
            payload[:1200],
        )
        return None
    missing_sections = [
        key for key in (
            "core_risk",
            "overlay_observations",
            "portfolio_comparison",
            "volatility_analysis",
            "efficiency_analysis",
            "final_judgement",
        )
        if not isinstance(sections.get(key), dict)
    ]
    if missing_sections:
        log.warning("[feature3][llm] parse warning: missing section keys=%s payload=%s", missing_sections, payload[:1200])
    return Feature3ExplainResult(
        text=payload,
        sections=sections,
        overall=overall,
        warnings=[],
    )


def _extract_json_object(text: str) -> str | None:
    trimmed = text.strip()
    if trimmed.startswith("```"):
        trimmed = trimmed.removeprefix("```json").removeprefix("```JSON").removeprefix("```").strip()
        if trimmed.endswith("```"):
            trimmed = trimmed[:-3].strip()
    start = trimmed.find("{")
    end = trimmed.rfind("}")
    if start < 0 or end <= start:
        return None
    return trimmed[start:end + 1]


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
