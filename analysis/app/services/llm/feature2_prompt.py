from __future__ import annotations

import json
import logging

from app.models.feature2 import Feature2ExplainRequest
from app.services.llm.invest_level import invest_level_prompt

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
        "macro_rates": metrics.macro_rates,
        "macro_trend_summaries": metrics.macro_trend_summaries,
        "stock_investor_flow_summary": metrics.stock_investor_flow_summary,
        "market_investor_flow_summary": metrics.market_investor_flow_summary,
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
        "외부요인 관점에서 금리/국채/환율, 외국인·기관 수급, 공매도, 산업지수, 유사종목, 뉴스심리를 종합하세요.\n"
        "데이터가 부족한 항목은 한계를 짧게 밝히되, 확인 가능한 신호 간 관계와 점검 포인트는 설명하세요.\n"
        "같은 방향의 신호는 신호 일관성으로, 반대 방향의 신호는 상충 또는 확인 필요 지점으로 해석하세요.\n\n"
        f"{invest_level_prompt(req.invest_level)}\n"
        "작성 규칙:\n"
        "1) 각 섹션은 title, summary, bullets 키를 포함하고 summary는 해당 섹션 데이터만 근거로 1~2문장 작성\n"
        "2) sections.macro_environment는 기준금리, 국채, 환율이 위험자산/외국인 수급에 주는 압력을 설명\n"
        "3) sections.investor_flow는 종목 수급과 시장 수급을 비교해 선별 매수/소외/동반 이탈 여부를 설명\n"
        "4) sections.short_selling는 공매도 추이와 수급 압력의 충돌 또는 정합성을 설명\n"
        "5) sections.peer_cluster는 산업지수와 유사종목 대비 상대강도를 설명\n"
        "6) sections.news_sentiment는 뉴스감성과 실제 수급/공매도 반응의 일치 여부를 설명\n"
        "7) sections.cross_signal는 매크로·수급·공매도·뉴스가 서로 강화/상쇄/모순되는 지점과 가장 중요한 간접 인사이트 1~2개를 설명\n"
        "8) overall.summary는 모든 섹션을 종합해 정확히 5문장 작성\n"
        "9) overall.bullets는 핵심 근거 3개, overall.risks는 주의점 2개, overall.conclusion은 1문장 작성\n"
        "10) 키 이름은 반드시 snake_case로 유지하고 번역하지 마세요.\n\n"
        "출력 JSON 스키마:\n"
        "{"
        "\"sections\":{"
        "\"macro_environment\":{\"title\":\"시장환경\",\"summary\":\"string\",\"bullets\":[]},"
        "\"investor_flow\":{\"title\":\"수급\",\"summary\":\"string\",\"bullets\":[]},"
        "\"short_selling\":{\"title\":\"공매도\",\"summary\":\"string\",\"bullets\":[]},"
        "\"peer_cluster\":{\"title\":\"유사종목 반응구조\",\"summary\":\"string\",\"bullets\":[]},"
        "\"news_sentiment\":{\"title\":\"뉴스감성\",\"summary\":\"string\",\"bullets\":[]},"
        "\"cross_signal\":{\"title\":\"신호 조합\",\"summary\":\"string\",\"bullets\":[]}"
        "},"
        "\"overall\":{\"summary\":\"string\",\"bullets\":[\"string\"],\"risks\":[\"string\"],\"conclusion\":\"string\"}"
        "}\n\n"
        "입력 데이터:\n"
        f"{json.dumps(payload, ensure_ascii=False, default=str)}"
    )
