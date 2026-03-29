# app/services/llm_client.py
from __future__ import annotations

from typing import List, Optional
from app.services.llm.gemini_client import _try_parse_json

from app.services.llm.factory import get_llm_client

from app.models.feature1 import (
    Feature1Explain,
    Feature1Metrics,
    Feature1Request,
    Feature1Response,
)

# =========================================================
# Public API
# =========================================================

async def analyze_feature1(req: Feature1Request, metrics: Feature1Metrics) -> Feature1Response:
    warnings: List[str] = []
    explain: Optional[Feature1Explain] = None

    if req.include_explain:
        llm = get_llm_client()
        text, warning = await llm.generate_explain(req, metrics)

        if text and text.strip():
            parsed, parse_warning = _try_parse_json(text)
            if parse_warning:
                warnings.append(parse_warning)

            explain = Feature1Explain(text=text.strip(), json=parsed)
        else:
            warnings.append("LLM_EXPLAIN_EMPTY")

        if warning:
            warnings.append(warning)
    else:
        warnings.append("LLM_EXPLAIN_SKIPPED")

    deduped_warnings = list(dict.fromkeys(warnings))
    return Feature1Response(metrics=metrics, explain=explain, warnings=deduped_warnings)
