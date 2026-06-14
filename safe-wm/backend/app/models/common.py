from __future__ import annotations

from enum import Enum


class RiskGrade(str, Enum):
    LOW = "LOW"
    MID = "MID"
    HIGH = "HIGH"


class Priority(str, Enum):
    NORMAL = "NORMAL"
    WATCH = "WATCH"
    URGENT = "URGENT"


class Severity(str, Enum):
    LOW = "LOW"
    MID = "MID"
    HIGH = "HIGH"

