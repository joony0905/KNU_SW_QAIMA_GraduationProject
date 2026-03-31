from __future__ import annotations

import os
import re
from typing import List, Tuple

import httpx

from app.models.feature2 import NewsSentimentRequest


GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_TIMEOUT = 10.0


async def score_news_sentiment_batch(req: NewsSentimentRequest) -> Tuple[List[dict], List[str]]:
    warnings: List[str] = []
    results: List[dict] = []

    api_key = os.getenv("GEMINI_API_KEY")
    model = os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
    timeout = float(os.getenv("GEMINI_TIMEOUT", DEFAULT_TIMEOUT))

    if not api_key:
        return results, ["NEWS_SENTIMENT_API_KEY_MISSING"]

    async with httpx.AsyncClient(timeout=timeout) as client:
        for item in req.items:
            try:
                prompt = (
                    "Return only one decimal number between -1 and 1.\n"
                    "Negative means negative sentiment, positive means positive sentiment.\n"
                    "No explanation, no markdown.\n\n"
                    f"title={item.title}\n"
                    f"publisher={item.publisher}\n"
                    f"focus_text={item.focus_text[:1500]}\n"
                )
                payload = {
                    "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                    "generationConfig": {"temperature": 0.1, "maxOutputTokens": 32},
                }
                response = await client.post(
                    f"{GEMINI_BASE_URL}/{model}:generateContent?key={api_key}",
                    json=payload,
                )
                if response.status_code == 429:
                    warnings.append("NEWS_SENTIMENT_RATE_LIMITED")
                    continue
                if response.status_code >= 400:
                    warnings.append(f"NEWS_SENTIMENT_HTTP_{response.status_code}")
                    continue

                data = response.json()
                text = (
                    data.get("candidates", [{}])[0]
                    .get("content", {})
                    .get("parts", [{}])[0]
                    .get("text", "")
                    .strip()
                )
                match = re.search(r"-?\d+(?:\.\d+)?", text)
                if not match:
                    warnings.append("NEWS_SENTIMENT_PARSE_FAILED")
                    continue

                score = max(-1.0, min(1.0, float(match.group(0))))
                results.append({"url": item.url, "sentiment_score": score})
            except Exception:
                warnings.append("NEWS_SENTIMENT_EXCEPTION")

    return results, list(dict.fromkeys(warnings))
