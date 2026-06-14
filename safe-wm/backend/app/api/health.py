from __future__ import annotations

from fastapi import APIRouter

from app.core.config import SERVICE_NAME, SERVICE_VERSION

router = APIRouter(prefix="/api/wm", tags=["health"])


@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": SERVICE_NAME, "version": SERVICE_VERSION}

