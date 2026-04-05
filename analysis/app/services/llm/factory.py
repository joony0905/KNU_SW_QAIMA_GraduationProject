# app/services/llm/factory.py
from __future__ import annotations

import os
import logging

from app.services.llm.base import LLMClient
from app.services.llm.gemini_client import GeminiClient
from app.services.llm.openai_client import OpenAIClient

log = logging.getLogger(__name__)

def get_llm_client(vendor_override: str | None = None) -> LLMClient:
    """
    LLM vendor factory
    - env: LLM_VENDOR=gemini | openai
    - 기본값: gemini
    """

    vendor = (vendor_override or os.getenv("LLM_VENDOR", "gemini")).lower()
    normalized = _normalize_vendor(vendor)

    if normalized == "gemini":
        return GeminiClient()

    if normalized == "openai":
        return OpenAIClient()

    log.warning("Unsupported or unavailable LLM vendor requested: %s, falling back to gemini", vendor)
    return GeminiClient()


def _normalize_vendor(vendor: str) -> str:
    vendor_key = vendor.strip().lower()

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
    if vendor_key in {
        "openai",
        "gpt-5.4",
        "gpt-5.2",
        "gpt-5 mini",
        "gpt-4.1",
        "gpt-4o",
        "gpt4o",
    }:
        return "openai"
    if vendor_key in {
        "grok",
        "xai",
        "grok 4",
        "grok 4.1 fast",
        "grok 4 fast",
        "grok 3",
        "grok 3 mini",
    }:
        return "grok"
    if vendor_key in {
        "claude opus 4.6",
        "claude opus 4.5",
        "claude sonnet 4.6",
        "claude sonnet 4",
        "claude haiku 4.5",
        "claude",
        "anthropic",
    }:
        return "claude"
    return vendor_key
