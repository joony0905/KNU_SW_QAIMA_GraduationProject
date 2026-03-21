from __future__ import annotations

import os
import json
import re
from typing import Any, Dict, Optional, Tuple
import httpx

from app.services.llm.base import LLMClient
from app.models.feature1 import Feature1Request, Feature1Metrics


# v1 사용
GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_TIMEOUT = 10.0


class GeminiClient(LLMClient):
    def __init__(self) -> None:
        self.api_key: Optional[str] = os.getenv("GEMINI_API_KEY")
        self.model: str = os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
        try:
            self.timeout: float = float(os.getenv("GEMINI_TIMEOUT", DEFAULT_TIMEOUT))
        except ValueError:
            self.timeout = DEFAULT_TIMEOUT

    async def generate_explain(
        self,
        req: Feature1Request,
        metrics: Feature1Metrics,
    ) -> Tuple[Optional[str], Optional[str]]:
        if not self.api_key:
            return None, "LLM_API_KEY_MISSING"

        prompt = self._build_prompt(req, metrics)
        url = f"{GEMINI_BASE_URL}/{self.model}:generateContent?key={self.api_key}"

        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0.2, "maxOutputTokens": 1200},
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(url, json=payload)

                # 왜 실패했는지 warnings에 찍기
                if resp.status_code == 429:
                    return None, "LLM_EXPLAIN_RATE_LIMITED"

                if resp.status_code >= 400:
                    body = (resp.text or "").strip().replace("\n", " ")
                    body = body[:250]
                    return None, f"LLM_EXPLAIN_HTTP_{resp.status_code}:{body}"

                data = resp.json()
                
                text = (
                    data.get("candidates", [{}])[0]
                    .get("content", {})
                    .get("parts", [{}])[0]
                    .get("text", "")
                    .strip()
                )

                if not text:
                    return None, "LLM_EXPLAIN_EMPTY"

                # JSON 파싱 시도 (경고는 warning으로)
                parsed, parse_warning = _try_parse_json(text)
                if parse_warning:
                    # JSON 강제 정책이면 여기서 None 처리해도 되지만, MVP는 text라도 살려두자
                    return text, parse_warning

                return text, None

        except httpx.TimeoutException:
            return None, "LLM_EXPLAIN_TIMEOUT"
        except Exception as e:
            # 예외 종류도 같이 남겨서 원인 확정
            return None, f"LLM_EXPLAIN_EXCEPTION:{type(e).__name__}:{str(e)[:120]}"

    def _build_prompt(self, req: Feature1Request, metrics: Feature1Metrics) -> str:
        o = metrics.ohlcv_summary
        f = metrics.financial_summary
        indicator_summary = metrics.indicator_summary or "indicator summary unavailable"

        return (
            "아래 규칙을 반드시 지키고 JSON 객체만 출력하세요.\n"
            "마크다운/코드블록(```) 사용 금지.\n"
            "출력은 { 로 시작해서 } 로 끝나야 합니다.\n\n"

            "규칙:\n"
            "1) stock_code는 입력값을 그대로 복사 (선행 0 유지)\n"
            "2) summary는 3개 항목, risks는 2개 항목\n"
            "3) 각 summary/risks 항목은 60자 이내\n"
            "4) conclusion은 1문장, 80자 이내\n"
            "5) 투자 권유/매수·매도 추천 금지\n\n"

            "출력 JSON 스키마:\n"
            "{"
            "\"stock_code\":\"string\","
            "\"summary\":[\"string\",\"string\",\"string\"],"
            "\"risks\":[\"string\",\"string\"],"
            "\"conclusion\":\"string\""
            "}\n\n"

            "입력:\n"
            f"stock_code={metrics.stock_code}\n"
            f"ohlcv_count={o.count}\n"
            f"period_from={o.from_}\n"
            f"period_to={o.to}\n"
            f"last_close={o.last_close}\n\n"
            "INDICATORS:\n"
            f"{indicator_summary}\n"
        )
    

def _extract_json_object(text: str) -> Optional[str]:
    if not text:
        return None

    # 코드펜스 제거
    t = re.sub(r"```(?:json)?\s*", "", text, flags=re.IGNORECASE)
    t = t.replace("```", "").strip()

    # 첫 { ~ 마지막 } 만 추출
    start = t.find("{")
    end = t.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    return t[start:end + 1]

def _try_parse_json(text: str) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    raw = _extract_json_object(text)
    if not raw:
        return None, "LLM_EXPLAIN_NO_JSON_OBJECT"
    try:
        return json.loads(raw), None
    except Exception as e:
        return None, f"LLM_EXPLAIN_INVALID_JSON:{type(e).__name__}"