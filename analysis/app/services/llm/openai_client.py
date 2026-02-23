import os

from app.services.llm.base import LLMClient
from app.services.llm.gemini_client import GeminiClient
# from app.services.llm.openai_client import OpenAIClient


def get_llm_client() -> LLMClient:
    vendor = os.getenv("LLM_VENDOR", "gemini").lower()

    if vendor == "gemini":
        return GeminiClient()

    # if vendor == "openai":
    #     return OpenAIClient()

    raise ValueError(f"Unsupported LLM_VENDOR={vendor}")