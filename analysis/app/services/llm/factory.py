# app/services/llm/factory.py
from __future__ import annotations

import os

from app.services.llm.base import LLMClient
from app.services.llm.gemini_client import GeminiClient
# from app.services.llm.openai_client import OpenAIClient


def get_llm_client() -> LLMClient:
    """
    LLM vendor factory
    - env: LLM_VENDOR=gemini | openai
    - 기본값: gemini
    """

    vendor = os.getenv("LLM_VENDOR", "gemini").lower()

    if vendor == "gemini":
        return GeminiClient()

    # 추후 OpenAI 다시 붙일 때
    # if vendor == "openai":
    #     return OpenAIClient()

    raise ValueError(f"Unsupported LLM_VENDOR: {vendor}")