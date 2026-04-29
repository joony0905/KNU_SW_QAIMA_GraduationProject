# app/services/llm_client.py
from __future__ import annotations

import json
import logging
from typing import List, Optional

from app.services.llm.factory import get_llm_client

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
    compact_retry_warnings = {
        "LLM_EXPLAIN_TIMEOUT",
        "LLM_EXPLAIN_MAX_OUTPUT_TOKENS",
        "LLM_EXPLAIN_RATE_LIMITED",
    }

    llm = get_llm_client(req.llm_vendor)
    text, warning = await llm.generate_feature2_explain(req)

    if text and text.strip():
        explain = text.strip()
    elif warning in compact_retry_warnings:
        log.warning("[feature2][llm] explain generation warning=%s, retrying compact", warning)
        compact_text, compact_warning = await llm.generate_feature2_explain(req, compact=True)
        explain = compact_text.strip() if compact_text and compact_text.strip() else None
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
                price_flow=_coerce_section(sections.get("price_flow")),
                market_snapshot=_coerce_section(sections.get("market_snapshot")),
                indicators=_coerce_section(sections.get("indicators")),
                financial_timeline=_coerce_section(sections.get("financial_timeline")),
            ),
            overall=Feature1ExplainOverall(
                summary=_coerce_text(overall.get("summary"), "-"),
                bullets=_coerce_list(overall.get("bullets"), 2),
                risks=_coerce_list(overall.get("risks"), 2),
                conclusion=_coerce_optional_text(overall.get("conclusion")),
            ),
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


def _coerce_section(value) -> Feature1ExplainSection:
    value = value if isinstance(value, dict) else {}
    return Feature1ExplainSection(
        title=_coerce_optional_text(value.get("title")),
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
