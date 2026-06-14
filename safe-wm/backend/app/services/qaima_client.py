from __future__ import annotations

import httpx

from app.core import config


async def enrich_context_with_qaima(risk_context: dict, *, include_qaima: bool) -> tuple[dict, list[dict]]:
    if not include_qaima or not config.QAIMA_ENABLED:
        enriched = dict(risk_context)
        enriched["qaima"] = {"enabled": False, "source": "FALLBACK"}
        return enriched, [_warning("QAIMA_DISABLED", "QAIMA adapter disabled. Fallback context used.")]

    warnings: list[dict] = []
    enriched = dict(risk_context)
    try:
        async with httpx.AsyncClient(
            base_url=config.QAIMA_BASE_URL,
            timeout=config.QAIMA_TIMEOUT_SECONDS,
        ) as client:
            macro = await client.get("/api/v1/feature2/cards/macro-rates")
            macro.raise_for_status()
            enriched["qaima"] = {
                "enabled": True,
                "source": "QAIMA_API",
                "macroRatesStatus": macro.status_code,
            }
    except httpx.TimeoutException:
        warnings.append(_warning("QAIMA_TIMEOUT", "QAIMA request timed out. Fallback context used."))
        enriched["qaima"] = {"enabled": False, "source": "FALLBACK"}
    except Exception:
        warnings.append(_warning("QAIMA_CONNECTION_FAILED", "QAIMA API connection failed. Fallback context used."))
        enriched["qaima"] = {"enabled": False, "source": "FALLBACK"}
    return enriched, warnings


def _warning(code: str, message: str) -> dict:
    return {"code": code, "message": message, "severity": "WARN", "source": "QAIMA_API"}

