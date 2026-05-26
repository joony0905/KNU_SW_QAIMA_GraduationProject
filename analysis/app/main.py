# app/main.py
import os
import logging
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv
from app.api.feature1 import router as feature1_router
from app.api.feature2 import router as feature2_router
from app.api.feature3 import router as feature3_router
from app.services.clustering import set_market_data_provider
from app.services.market_data_spring import SpringMarketDataProvider, SpringClientConfig
from app.services.news_sentiment import start_local_model_warmup
from fastapi import HTTPException
from fastapi.responses import JSONResponse

log = logging.getLogger(__name__)

#앱 시작 시 .env 로드 (로컬 개발용)
load_dotenv()


def _csv_env(name: str, default: str = "") -> list[str]:
    raw = os.getenv(name, default)
    return [item.strip() for item in raw.split(",") if item.strip()]

app = FastAPI(
    title="QAIMA Analysis API",
    version="0.0.1",
)

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL")
set_market_data_provider(SpringMarketDataProvider(SpringClientConfig(base_url=SPRING_BASE_URL)))
log.info("analysis spring base url configured=%s", bool(SPRING_BASE_URL))


@app.on_event("startup")
async def warmup_news_sentiment_model() -> None:
    if os.getenv("QAIMA_DISABLE_NEWS_WARMUP", "").lower() in {"1", "true", "yes"}:
        log.info("news sentiment local model warm-up disabled")
        return
    log.info("starting news sentiment local model warm-up")
    start_local_model_warmup()

cors_allowed_origins = _csv_env(
    "QAIMA_CORS_ALLOWED_ORIGINS",
    "http://localhost:5173,http://localhost:3000",
)
if "*" in cors_allowed_origins:
    raise RuntimeError("QAIMA_CORS_ALLOWED_ORIGINS must not contain '*' when credentials are enabled")

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    log.exception("analysis unhandled exception. path=%s", request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "meta": {"status": "failure"},
            "data": None,
            "errors": [{"code": "INTERNAL_ERROR", "message": _error_message("INTERNAL_ERROR", request)}],
        },
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    code = _error_code_from_detail(exc.detail)
    log.warning("analysis http exception. path=%s status=%s code=%s", request.url.path, exc.status_code, code)
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "meta": {"status": "failure"},
            "data": None,
            "errors": [{"code": code, "message": _error_message(code, request)}],
        },
    )


def _error_code_from_detail(detail: object) -> str:
    if isinstance(detail, str) and detail:
        return detail.split(":", 1)[0]
    if isinstance(detail, dict) and detail.get("code"):
        return str(detail["code"])
    return "ANALYSIS_API_FAILED"


def _is_english_request(request: Request) -> bool:
    lang = request.headers.get("accept-language", "")
    return lang.lower().startswith("en")


def _error_message(code: str, request: Request) -> str:
    if _is_english_request(request):
        return {
            "INTERNAL_ERROR": "Internal analysis API error.",
            "PEER_CLUSTER_FAILED": "Similar-stock analysis failed.",
            "NEWS_SENTIMENT_FAILED": "News sentiment analysis failed.",
            "FEATURE2_ANALYZE_FAILED": "Feature 2 explanation generation failed.",
            "FEATURE3_ANALYZE_FAILED": "Feature 3 portfolio analysis failed.",
        }.get(code, "Analysis API request failed.")
    return {
        "INTERNAL_ERROR": "분석 API 내부 오류",
        "PEER_CLUSTER_FAILED": "유사 종목 분석에 실패했습니다.",
        "NEWS_SENTIMENT_FAILED": "뉴스 감성 분석에 실패했습니다.",
        "FEATURE2_ANALYZE_FAILED": "Feature2 설명 생성에 실패했습니다.",
        "FEATURE3_ANALYZE_FAILED": "Feature3 포트폴리오 분석에 실패했습니다.",
    }.get(code, "분석 API 호출에 실패했습니다.")

# 라우터 등록
app.include_router(feature1_router)
app.include_router(feature2_router)
app.include_router(feature3_router)


@app.get("/health")
async def health_check():
    return {"status": "ok"}
