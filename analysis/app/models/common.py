from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class AnalysisWarning(BaseModel):
    code: str
    message: str | None = None
    user_message: str | None = None
    severity: str = "WARN"
    target: str | None = None


class ExplainSection(BaseModel):
    title: str
    summary: str
    bullets: list[str] = Field(default_factory=list)


class ExplainOverall(BaseModel):
    summary: str
    bullets: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    conclusion: str | None = None


class ExplainResult(BaseModel):
    provider: str = "DETERMINISTIC"
    model: str | None = None
    text: str | None = None
    sections: dict[str, ExplainSection] = Field(default_factory=dict)
    overall: ExplainOverall | None = None
    warnings: list[Any] = Field(default_factory=list)
