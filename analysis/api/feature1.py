# app/api/feature1.py
from fastapi import APIRouter

from models.feature1 import FeatOneRequestDto, FeatOneResponseTextDto
from services.llm_client import analyze_feature1

router = APIRouter(
    prefix="/api/v1/analysis",
    tags=["feature1"],
)


@router.post("/stock", response_model=FeatOneResponseTextDto)
async def analyze_stock(req: FeatOneRequestDto) -> FeatOneResponseTextDto:
    """
    QAIMA 기능1: 종목 심층 분석 엔드포인트.
    Spring → FastAPI 로 넘어온 JSON을 받아 LLM 분석 후 결과 텍스트 반환
    (현재는 LLM 대신 더미 구현)
    """
    return await analyze_feature1(req)
