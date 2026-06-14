from __future__ import annotations

import os
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[2]
DATA_DIR = BASE_DIR / "app" / "data"
LOG_DIR = BASE_DIR / "logs"

SERVICE_NAME = "safe-wm"
SERVICE_VERSION = "0.1.0"

QAIMA_BASE_URL = os.getenv("SAFE_WM_QAIMA_BASE_URL", "http://localhost:8080")
QAIMA_ENABLED = os.getenv("SAFE_WM_ENABLE_QAIMA", "false").lower() == "true"
QAIMA_TIMEOUT_SECONDS = float(os.getenv("SAFE_WM_QAIMA_TIMEOUT_SECONDS", "5"))

