from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.analysis import router as analysis_router
from app.api.customers import router as customers_router
from app.api.dashboard import router as dashboard_router
from app.api.health import router as health_router
from app.core.logging import configure_logging

configure_logging()

app = FastAPI(title="SAFE-WM Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5174", "http://127.0.0.1:5174"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(customers_router)
app.include_router(analysis_router)
app.include_router(dashboard_router)
