# app/services/llm_client.py
from __future__ import annotations

import json
import logging
import re
from typing import List, Optional

from app.services.llm.factory import get_llm_client
from app.services.llm.invest_level import normalize_language_code

from app.models.feature1 import (
    Feature1Explain,
    Feature1ExplainOverall,
    Feature1ExplainSection,
    Feature1ExplainSections,
    Feature1Metrics,
    Feature1Request,
    Feature1Response,
)
from app.models.feature2 import Feature2ExplainRequest, Feature2ExplainResponse
from app.models.common import ExplainOverall, ExplainResult, ExplainSection

log = logging.getLogger(__name__)

# =========================================================
# Public API
# =========================================================

async def analyze_feature1(req: Feature1Request, metrics: Feature1Metrics) -> Feature1Response:
    warnings: List[str] = []
    explain: Optional[Feature1Explain] = None
    compact_retry_warnings = {
        "LLM_EXPLAIN_TIMEOUT",
        "LLM_EXPLAIN_MAX_OUTPUT_TOKENS",
        "LLM_EXPLAIN_RATE_LIMITED",
    }

    if req.include_explain:
        llm = get_llm_client(req.llm_vendor)
        text, warning = await llm.generate_explain(req, metrics)

        if text and text.strip():
            explain = _parse_explain_json(text.strip())
            if explain is not None:
                explain.text = text.strip()
            elif not warning:
                log.warning("[feature1][llm] explain parse failed, retrying compact raw=%s", text[:2000])
                compact_text, compact_warning = await llm.generate_explain(req, metrics, compact=True)
                if compact_text and compact_text.strip():
                    explain = _parse_explain_json(compact_text.strip())
                    if explain is not None:
                        explain.text = compact_text.strip()
                    else:
                        log.warning("[feature1][llm] compact explain parse failed raw=%s", compact_text[:2000])
                if explain is None:
                    warnings.append("LLM_EXPLAIN_PARSE_FAILED")
                if compact_warning:
                    warnings.append(compact_warning)
        elif warning in compact_retry_warnings:
            log.warning("[feature1][llm] explain generation warning=%s, retrying compact", warning)
            compact_text, compact_warning = await llm.generate_explain(req, metrics, compact=True)
            if compact_text and compact_text.strip():
                explain = _parse_explain_json(compact_text.strip())
                if explain is not None:
                    explain.text = compact_text.strip()
                else:
                    log.warning("[feature1][llm] compact explain parse failed raw=%s", compact_text[:2000])
                    warnings.append("LLM_EXPLAIN_PARSE_FAILED")
            if explain is None and not compact_warning:
                warnings.append("LLM_EXPLAIN_EMPTY")
            if compact_warning:
                warnings.append(compact_warning)
        elif not warning:
            warnings.append("LLM_EXPLAIN_EMPTY")

        if warning:
            warnings.append(warning)
    else:
        warnings.append("LLM_EXPLAIN_SKIPPED")

    deduped_warnings = list(dict.fromkeys(warnings))
    return Feature1Response(metrics=metrics, explain=explain, warnings=deduped_warnings)


async def analyze_feature2_explain(req: Feature2ExplainRequest) -> Feature2ExplainResponse:
    warnings: List[str] = []
    explain: Optional[ExplainResult] = None
    compact_retry_warnings = {
        "LLM_EXPLAIN_TIMEOUT",
        "LLM_EXPLAIN_MAX_OUTPUT_TOKENS",
        "LLM_EXPLAIN_RATE_LIMITED",
    }

    llm = get_llm_client(req.llm_vendor)
    text, warning = await llm.generate_feature2_explain(req)

    if text and text.strip():
        explain = _parse_structured_explain_json(
            text.strip(),
            required_sections=[
                "macro_environment",
                "investor_flow",
                "short_selling",
                "peer_cluster",
                "news_sentiment",
                "cross_signal",
            ],
        )
        if explain is not None:
            _sanitize_feature2_explain(explain, req.language_code)
            explain.text = text.strip()
        else:
            warnings.append("LLM_EXPLAIN_PARSE_FAILED")
    elif warning in compact_retry_warnings:
        log.warning("[feature2][llm] explain generation warning=%s, retrying compact", warning)
        compact_text, compact_warning = await llm.generate_feature2_explain(req, compact=True)
        if compact_text and compact_text.strip():
            explain = _parse_structured_explain_json(
                compact_text.strip(),
                required_sections=[
                    "macro_environment",
                    "investor_flow",
                    "short_selling",
                    "peer_cluster",
                    "news_sentiment",
                    "cross_signal",
                ],
            )
            if explain is not None:
                _sanitize_feature2_explain(explain, req.language_code)
                explain.text = compact_text.strip()
            else:
                warnings.append("LLM_EXPLAIN_PARSE_FAILED")
        if explain is None and not compact_warning:
            warnings.append("LLM_EXPLAIN_EMPTY")
        if compact_warning:
            warnings.append(compact_warning)
    else:
        explain = None
        if not warning:
            warnings.append("LLM_EXPLAIN_EMPTY")

    if warning:
        warnings.append(warning)

    return Feature2ExplainResponse(
        explain=explain,
        warnings=list(dict.fromkeys(warnings)),
    )


_FEATURE2_INTERNAL_TERM_REPLACEMENTS = [
    (re.compile(r"\banchor_return_pct\b", re.IGNORECASE), "기준 종목 수익률"),
    (re.compile(r"\banchor return pct\b", re.IGNORECASE), "기준 종목 수익률"),
    (re.compile(r"\banchor_return\b", re.IGNORECASE), "기준 종목 수익률"),
    (re.compile(r"\bpeer_cluster_summary\b", re.IGNORECASE), "유사종목 반응 요약"),
    (re.compile(r"\btop_peers\b", re.IGNORECASE), "주요 유사종목"),
    (re.compile(r"\bpeer_centroid\b", re.IGNORECASE), "유사종목 평균 흐름"),
    (re.compile(r"\bpeer centroid\b", re.IGNORECASE), "유사종목 평균 흐름"),
    (re.compile(r"\bcentroid\b", re.IGNORECASE), "평균 흐름"),
    (re.compile(r"\bpeer_band\b", re.IGNORECASE), "유사종목 분포 범위"),
    (re.compile(r"\bpeer band\b", re.IGNORECASE), "유사종목 분포 범위"),
    (re.compile(r"\bpeer_coverage\b", re.IGNORECASE), "유사종목 데이터 커버리지"),
    (re.compile(r"\bpeers\b", re.IGNORECASE), "유사종목"),
    (re.compile(r"\bpeer\b", re.IGNORECASE), "유사종목"),
    (re.compile(r"\braw_corr\b", re.IGNORECASE), "원시 상관계수"),
    (re.compile(r"\badjusted_corr\b", re.IGNORECASE), "산업 조정 상관계수"),
    (re.compile(r"\bbest_lag\b", re.IGNORECASE), "가장 뚜렷한 시차"),
    (re.compile(r"\blead_lag_corr\b", re.IGNORECASE), "선행·후행 상관"),
    (re.compile(r"\blag_confidence\b", re.IGNORECASE), "시차 신뢰도"),
    (re.compile(r"\bdisplay_status\b", re.IGNORECASE), "표시 상태"),
    (re.compile(r"\bCOINCIDENT\b", re.IGNORECASE), "동행"),
    (re.compile(r"\bLEADER\b", re.IGNORECASE), "선행"),
    (re.compile(r"\bFOLLOWER\b", re.IGNORECASE), "후행"),
    (re.compile(r"\bRAW_ONLY\b", re.IGNORECASE), "원시 상관 기준"),
    (re.compile(r"\bADJUSTED_ONLY\b", re.IGNORECASE), "산업 조정 기준"),
    (re.compile(r"\bFALLBACK_RAW\b", re.IGNORECASE), "원시 상관 대체"),
    (re.compile(r"\bSELECTED\b", re.IGNORECASE), "선정"),
]

_FEATURE2_INTERNAL_TERM_REPLACEMENTS_EN = [
    (re.compile(r"\banchor_return_pct\b", re.IGNORECASE), "anchor stock return"),
    (re.compile(r"\banchor return pct\b", re.IGNORECASE), "anchor stock return"),
    (re.compile(r"\banchor_return\b", re.IGNORECASE), "anchor stock return"),
    (re.compile(r"\bpeer_cluster_summary\b", re.IGNORECASE), "similar-stock reaction summary"),
    (re.compile(r"\btop_peers\b", re.IGNORECASE), "major similar stocks"),
    (re.compile(r"\bpeer_centroid\b", re.IGNORECASE), "average similar-stock flow"),
    (re.compile(r"\bpeer centroid\b", re.IGNORECASE), "average similar-stock flow"),
    (re.compile(r"\bcentroid\b", re.IGNORECASE), "average flow"),
    (re.compile(r"\bpeer_band\b", re.IGNORECASE), "similar-stock range"),
    (re.compile(r"\bpeer band\b", re.IGNORECASE), "similar-stock range"),
    (re.compile(r"\bpeer_coverage\b", re.IGNORECASE), "similar-stock data coverage"),
    (re.compile(r"\bpeers\b", re.IGNORECASE), "similar stocks"),
    (re.compile(r"\bpeer\b", re.IGNORECASE), "similar stock"),
    (re.compile(r"\braw_corr\b", re.IGNORECASE), "raw correlation"),
    (re.compile(r"\badjusted_corr\b", re.IGNORECASE), "industry-adjusted correlation"),
    (re.compile(r"\bbest_lag\b", re.IGNORECASE), "clearest lag"),
    (re.compile(r"\blead_lag_corr\b", re.IGNORECASE), "lead-lag correlation"),
    (re.compile(r"\blag_confidence\b", re.IGNORECASE), "lag confidence"),
    (re.compile(r"\bdisplay_status\b", re.IGNORECASE), "display status"),
    (re.compile(r"\bCOINCIDENT\b", re.IGNORECASE), "moving together"),
    (re.compile(r"\bLEADER\b", re.IGNORECASE), "leading"),
    (re.compile(r"\bFOLLOWER\b", re.IGNORECASE), "lagging"),
    (re.compile(r"\bRAW_ONLY\b", re.IGNORECASE), "raw-correlation basis"),
    (re.compile(r"\bADJUSTED_ONLY\b", re.IGNORECASE), "industry-adjusted basis"),
    (re.compile(r"\bFALLBACK_RAW\b", re.IGNORECASE), "raw-correlation fallback"),
    (re.compile(r"\bSELECTED\b", re.IGNORECASE), "selected"),
]

def _sanitize_feature2_explain(explain: ExplainResult, language_code: str | None = None) -> None:
    if explain.sections:
        for section in explain.sections.values():
            section.title = _sanitize_feature2_text(section.title, language_code)
            section.summary = _sanitize_feature2_text(section.summary, language_code)
            section.bullets = [_sanitize_feature2_text(item, language_code) for item in section.bullets]
    if explain.overall:
        explain.overall.summary = _sanitize_feature2_text(explain.overall.summary, language_code)
        explain.overall.bullets = [_sanitize_feature2_text(item, language_code) for item in explain.overall.bullets]
        explain.overall.risks = [_sanitize_feature2_text(item, language_code) for item in explain.overall.risks]
        if explain.overall.conclusion:
            explain.overall.conclusion = _sanitize_feature2_text(explain.overall.conclusion, language_code)


def _sanitize_feature2_text(text: str, language_code: str | None = None) -> str:
    sanitized = text
    replacements = (
        _FEATURE2_INTERNAL_TERM_REPLACEMENTS_EN
        if normalize_language_code(language_code) == "en"
        else _FEATURE2_INTERNAL_TERM_REPLACEMENTS
    )
    for pattern, replacement in replacements:
        sanitized = pattern.sub(replacement, sanitized)
    return sanitized


def _parse_explain_json(text: str) -> Optional[Feature1Explain]:
    payload = _extract_json_object(text)
    if payload is None:
        log.warning("[feature1][llm] no JSON object found in explain raw=%s", text[:2000])
        return None

    try:
        data = json.loads(payload)
        sections = data.get("sections") or {}
        overall = data.get("overall") or {}

        return Feature1Explain(
            sections=Feature1ExplainSections(
                price_flow=_coerce_section(sections.get("price_flow"), "가격 흐름"),
                market_snapshot=_coerce_section(sections.get("market_snapshot"), "시장 스냅샷"),
                indicators=_coerce_section(sections.get("indicators"), "보조지표"),
                financial_timeline=_coerce_section(sections.get("financial_timeline"), "재무 흐름"),
            ),
            overall=Feature1ExplainOverall(
                summary=_coerce_text(overall.get("summary"), "-"),
                bullets=_coerce_list(overall.get("bullets"), 2),
                risks=_coerce_list(overall.get("risks"), 2),
                conclusion=_coerce_optional_text(overall.get("conclusion")),
            ),
            provider="LLM",
            text=text,
        )
    except Exception as exc:
        log.warning("[feature1][llm] explain json decode failed error=%s payload=%s", exc, payload[:2000])
        return None


def _extract_json_object(text: str) -> Optional[str]:
    trimmed = text.strip()
    if trimmed.startswith("```"):
        trimmed = trimmed.removeprefix("```json").removeprefix("```JSON").removeprefix("```").strip()
        if trimmed.endswith("```"):
            trimmed = trimmed[:-3].strip()
    if trimmed.startswith("{") and trimmed.endswith("}"):
        return trimmed
    start = trimmed.find("{")
    end = trimmed.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    return trimmed[start:end + 1]


def _parse_structured_explain_json(text: str, required_sections: list[str]) -> Optional[ExplainResult]:
    payload = _extract_json_object(text)
    if payload is None:
        log.warning("[llm] structured explain has no JSON object raw=%s", text[:2000])
        return None

    try:
        data = json.loads(payload)
        sections_raw = data.get("sections") or {}
        overall_raw = data.get("overall") or {}
        if not isinstance(sections_raw, dict) or not isinstance(overall_raw, dict):
            return None
        sections = {
            key: _coerce_common_section(sections_raw.get(key), key)
            for key in required_sections
        }
        return ExplainResult(
            provider="LLM",
            text=text,
            sections=sections,
            overall=ExplainOverall(
                summary=_coerce_text(overall_raw.get("summary"), "-"),
                bullets=_coerce_list(overall_raw.get("bullets"), 3),
                risks=_coerce_list(overall_raw.get("risks"), 2),
                conclusion=_coerce_optional_text(overall_raw.get("conclusion")),
            ),
        )
    except Exception as exc:
        log.warning("[llm] structured explain json decode failed error=%s payload=%s", exc, payload[:2000])
        return None


def _coerce_common_section(value, fallback_title: str) -> ExplainSection:
    value = value if isinstance(value, dict) else {}
    return ExplainSection(
        title=_coerce_text(value.get("title"), fallback_title),
        summary=_coerce_text(value.get("summary"), "-"),
        bullets=_coerce_list(value.get("bullets"), 3),
    )


def _coerce_section(value, fallback_title: str) -> Feature1ExplainSection:
    value = value if isinstance(value, dict) else {}
    return Feature1ExplainSection(
        title=_coerce_text(value.get("title"), fallback_title),
        summary=_coerce_text(value.get("summary"), "-"),
        bullets=_coerce_list(value.get("bullets"), 2),
    )


def _coerce_text(value, default: str) -> str:
    if isinstance(value, str) and value.strip():
        return value.strip()
    return default


def _coerce_optional_text(value) -> Optional[str]:
    if isinstance(value, str) and value.strip():
        return value.strip()
    return None


def _coerce_list(value, limit: int) -> List[str]:
    if not isinstance(value, list):
        return []
    items = [str(item).strip() for item in value if str(item).strip()]
    return items[:limit]
