from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def log_event(
    logger: logging.Logger,
    *,
    level: int = logging.INFO,
    trace_id: str | None = None,
    customer_id: str | None = None,
    step: str,
    event: str,
    message: str,
    metadata: dict[str, Any] | None = None,
) -> None:
    payload = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "service": "safe-wm",
        "traceId": trace_id,
        "customerId": customer_id,
        "step": step,
        "event": event,
        "message": message,
        "metadata": metadata or {},
    }
    logger.log(level, json.dumps(payload, ensure_ascii=False))

