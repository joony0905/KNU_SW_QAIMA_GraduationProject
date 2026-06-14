from __future__ import annotations

from time import perf_counter
from typing import Callable, TypeVar

T = TypeVar("T")


def run_step(state: dict, step: str, func: Callable[[], T]) -> T:
    started = perf_counter()
    try:
        result = func()
        status = "SUCCESS"
        message = f"{step} completed"
        return result
    except Exception as exc:
        status = "FAILED"
        message = str(exc)
        raise
    finally:
        state.setdefault("debugSteps", []).append(
            {
                "step": step,
                "status": status,
                "durationMs": round((perf_counter() - started) * 1000, 2),
                "message": message,
                "warnings": [],
            }
        )


def add_agent_trace(state: dict, agent: str, status: str, summary: str, warnings: list[str] | None = None) -> None:
    state.setdefault("agentTrace", []).append(
        {
            "agent": agent,
            "status": status,
            "summary": summary,
            "warnings": warnings or [],
        }
    )

