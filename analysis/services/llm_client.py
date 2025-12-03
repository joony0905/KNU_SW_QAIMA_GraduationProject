# app/services/llm_client.py
from models.feature1 import FeatOneRequestDto, FeatOneResponseTextDto


async def analyze_feature1(req: FeatOneRequestDto) -> FeatOneResponseTextDto:
    """
    기능1 LLM 분석 더미 구현.
    - 나중에 OpenAI / 기타 LLM 호출 로직으로 교체할 예정.
    """
    stock = req.stock

    summary = f"{stock.companyName}({stock.stockCode})에 대한 임시 요약입니다."
    business = "여기에 사업/비즈니스 설명 섹션을 채울 예정입니다."
    financial = "여기에 재무 분석 섹션을 채울 예정입니다."
    valuation = "여기에 밸류에이션/밴드 설명 섹션을 채울 예정입니다."
    risk = "여기에 주요 리스크 요인 섹션을 채울 예정입니다."
    outlook = "여기에 향후 전망/코멘트 섹션을 채울 예정입니다."

    analysis_text = "\n\n".join(
        [
            f"[요약]\n{summary}",
            f"[사업]\n{business}",
            f"[재무]\n{financial}",
            f"[밸류에이션]\n{valuation}",
            f"[리스크]\n{risk}",
            f"[전망]\n{outlook}",
        ]
    )

    return FeatOneResponseTextDto(
        stockId=stock.stockId,
        stockCode=stock.stockCode,
        summary=summary,
        business=business,
        financial=financial,
        valuation=valuation,
        risk=risk,
        outlook=outlook,
        rawPrompt=None,        # 나중에 실제 프롬프트 저장할 거면 채우기
        analysisText=analysis_text,
    )
