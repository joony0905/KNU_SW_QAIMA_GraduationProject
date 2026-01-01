# app/main.py
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.feature1 import router as feature1_router
# from api.feature2 import router as feature2_router
# from api.feature3 import router as feature3_router


app = FastAPI(
    title="QAIMA Analysis API",
    version="0.0.1",
)

# 개발 단계니 일단 전체 허용함. 나중에 세팅ㄱ
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # 나중에 Spring 도메인으로 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
app.include_router(feature1_router)
# app.include_router(feature2_router)
# app.include_router(feature3_router)


@app.get("/health")
async def health_check():
    return {"status": "ok"}
