from __future__ import annotations

import json
import logging

from app.models.feature2 import Feature2ExplainRequest

log = logging.getLogger(__name__)


def build_feature2_prompt(req: Feature2ExplainRequest, compact: bool = False) -> str:
    metrics = req.metrics
    peer_summary = metrics.peer_cluster_summary or {}
    recent_news = metrics.recent_news or []
    log.info(
        "[feature2][llm][prompt-input] stock_code=%s industry_index=%s peer_cluster_summary=%s top_peers=%s news_sentiment_summary=%s recent_news=%s",
        req.stock_code,
        metrics.industry_index is not None,
        metrics.peer_cluster_summary is not None,
        len(peer_summary.get("top_peers") or []) if isinstance(peer_summary, dict) else 0,
        metrics.news_sentiment_summary is not None,
        len(recent_news),
    )
    payload = {
        "stock_code": req.stock_code,
        "freq": req.freq,
        "window": req.window,
        "stock": metrics.stock,
        "industry": metrics.industry,
        "base_rate": metrics.base_rate,
        "short_selling": metrics.short_selling,
        "base_rate_trend_summary": metrics.base_rate_trend_summary,
        "short_selling_trend_summary": metrics.short_selling_trend_summary,
        "industry_index": metrics.industry_index,
        "peer_cluster_summary": metrics.peer_cluster_summary,
        "news_sentiment_summary": metrics.news_sentiment_summary,
        "recent_news": metrics.recent_news,
    }

    return (
        "아래 규칙을 반드시 지키고 JSON 객체만 출력하세요.\n"
        "마크다운, 표, 코드블록 사용 금지.\n"
        "출력은 { 로 시작해서 } 로 끝나야 합니다.\n"
        "제공된 데이터만 근거로 작성하고 외부 지식은 사용하지 마세요.\n"
        "직접적인 투자 권유, 매수/매도 추천은 금지하세요.\n"
        "외부요인 관점에서 금리, 공매도, 산업지수, 유사종목, 뉴스심리를 종합하세요.\n"
        "데이터가 부족한 항목은 부족하다고 보수적으로 표현하세요.\n\n"
        "작성 규칙:\n"
        "1) 각 섹션 summary는 해당 섹션 데이터만 근거로 1~2문장 작성\n"
        "2) sections.peer_cluster는 유사종목 반응구조를 설명\n"
        "3) sections.news_sentiment는 뉴스감성과 점수 산출 뉴스 흐름을 설명\n"
        "4) sections.trend_summary는 기준금리/공매도 기간 추이의 조합을 설명\n"
        "5) sections.base_rate는 기준금리 추이만 설명\n"
        "6) sections.short_selling는 공매도 추이만 설명\n"
        "7) overall.summary는 모든 섹션을 종합해 정확히 5문장 작성\n"
        "8) 키 이름은 반드시 snake_case로 유지하고 번역하지 마세요.\n\n"
        "출력 JSON 스키마:\n"
        "{"
        "\"sections\":{"
        "\"peer_cluster\":{\"summary\":\"string\"},"
        "\"news_sentiment\":{\"summary\":\"string\"},"
        "\"trend_summary\":{\"summary\":\"string\"},"
        "\"base_rate\":{\"summary\":\"string\"},"
        "\"short_selling\":{\"summary\":\"string\"}"
        "},"
        "\"overall\":{\"summary\":\"string\"}"
        "}\n\n"
        "입력 데이터:\n"
        f"{json.dumps(payload, ensure_ascii=False, default=str)}"
    )
