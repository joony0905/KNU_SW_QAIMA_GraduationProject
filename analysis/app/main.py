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
import traceback
from fastapi.responses import JSONResponse

log = logging.getLogger(__name__)

#앱 시작 시 .env 로드 (로컬 개발용)
load_dotenv()

app = FastAPI(
    title="QAIMA Analysis API",
    version="0.0.1",
)

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL")
set_market_data_provider(SpringMarketDataProvider(SpringClientConfig(base_url=SPRING_BASE_URL)))
print("SPRING_LOCAL:", os.getenv("SPRING_BASE_URL"))


@app.on_event("startup")
async def warmup_news_sentiment_model() -> None:
    if os.getenv("QAIMA_DISABLE_NEWS_WARMUP", "").lower() in {"1", "true", "yes"}:
        log.info("news sentiment local model warm-up disabled")
        return
    log.info("starting news sentiment local model warm-up")
    start_local_model_warmup()

# 개발 단계니 일단 전체 허용함. 나중에 세팅ㄱ
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # 나중에 Spring 도메인으로 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    traceback.print_exc()
    return JSONResponse(
        status_code=500,
        content={
            "detail": f"{type(exc).__name__}: {str(exc)[:200]}",
        },
    )

# 라우터 등록
app.include_router(feature1_router)
app.include_router(feature2_router)
app.include_router(feature3_router)


@app.get("/health")
async def health_check():
    return {"status": "ok"}
