from __future__ import annotations

from pydantic import BaseModel


class AnalysisRequest(BaseModel):
    includeDebug: bool = False
    includeQaima: bool = False
    forceUnsafeExplanation: bool = False

