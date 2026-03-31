from __future__ import annotations

import asyncio
import os
import re
from typing import List, Tuple

import httpx

from app.models.feature2 import NewsSentimentRequest


GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_TIMEOUT = 10.0
RATE_LIMIT_RETRY_DELAYS = (0.6, 1.2)
MAX_FOCUS_TEXT_LENGTH = 1500
SUPPORTED_GEMINI_PREFIX = "gemini-"


async def score_news_sentiment_batch(req: NewsSentimentRequest) -> Tuple[List[dict], List[str]]:
    warnings: List[str] = []
    results: List[dict] = []

    api_key = os.getenv("GEMINI_API_KEY")
    model, model_warnings = resolve_model(req.model)
    warnings.extend(model_warnings)
    timeout = float(os.getenv("GEMINI_TIMEOUT", DEFAULT_TIMEOUT))

    if not api_key:
        return results, ["NEWS_SENTIMENT_API_KEY_MISSING"]

    async with httpx.AsyncClient(timeout=timeout) as client:
        for item in req.items:
            try:
                if not item.url or not item.title or not item.focus_text.strip():
                    warnings.append("NEWS_SENTIMENT_INVALID_ITEM")
                    continue

                prompt = (
                    "Return only one decimal number between -1 and 1.\n"
                    "Negative means negative sentiment, positive means positive sentiment.\n"
                    "No explanation, no markdown.\n\n"
                    f"title={item.title}\n"
                    f"publisher={item.publisher}\n"
                    f"focus_text={item.focus_text[:MAX_FOCUS_TEXT_LENGTH]}\n"
                )
                payload = {
                    "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                    "generationConfig": {"temperature": 0.1, "maxOutputTokens": 32},
                }

                response = None
                for attempt in range(len(RATE_LIMIT_RETRY_DELAYS) + 1):
                    response = await client.post(
                        f"{GEMINI_BASE_URL}/{model}:generateContent?key={api_key}",
                        json=payload,
                    )
                    if response.status_code != 429:
                        break
                    if attempt < len(RATE_LIMIT_RETRY_DELAYS):
                        await asyncio.sleep(RATE_LIMIT_RETRY_DELAYS[attempt])

                if response is not None and response.status_code == 429:
                    warnings.append("NEWS_SENTIMENT_RATE_LIMITED")
                    continue
                if response is None:
                    warnings.append("NEWS_SENTIMENT_EMPTY_RESPONSE")
                    continue
                if response.status_code >= 400:
                    warnings.append(f"NEWS_SENTIMENT_HTTP_{response.status_code}")
                    continue

                data = response.json()
                text = extract_text(data)
                score = parse_score(text)
                if score is None:
                    warnings.append("NEWS_SENTIMENT_PARSE_FAILED")
                    continue

                results.append({"url": item.url, "sentiment_score": score})
            except httpx.TimeoutException:
                warnings.append("NEWS_SENTIMENT_TIMEOUT")
            except Exception:
                warnings.append("NEWS_SENTIMENT_EXCEPTION")

    return results, list(dict.fromkeys(warnings))


def resolve_model(requested_model: str | None) -> Tuple[str, List[str]]:
    fallback_model = os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
    normalized = (requested_model or "").strip()
    # TODO: 현재는 전달된 모델을 제한적으로 해석하고, 추후 클라이언트 선택 모델 검증/매핑을 확장한다.
    if not normalized:
        return fallback_model, []
    if normalized.startswith(SUPPORTED_GEMINI_PREFIX):
        return normalized, []
    return fallback_model, [f"NEWS_SENTIMENT_MODEL_FALLBACK:{normalized}->{fallback_model}"]


def extract_text(data: dict) -> str:
    return (
        data.get("candidates", [{}])[0]
        .get("content", {})
        .get("parts", [{}])[0]
        .get("text", "")
        .strip()
    )


def parse_score(text: str) -> float | None:
    if not text:
        return None
    match = re.search(r"-?\d+(?:\.\d+)?", text)
    if not match:
        return None
    return max(-1.0, min(1.0, float(match.group(0))))
