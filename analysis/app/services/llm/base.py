from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Optional, Tuple

from app.models.feature1 import Feature1Request, Feature1Metrics
from app.models.feature2 import Feature2ExplainRequest


class LLMClient(ABC):
    """
    LLM 공통 인터페이스
    - 실패해도 exception 던지지 않음
    - (text, warning_code) 반환
    """

    @abstractmethod
    async def generate_explain(
        self,
        req: Feature1Request,
        metrics: Feature1Metrics,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        pass

    @abstractmethod
    async def generate_feature2_explain(
        self,
        req: Feature2ExplainRequest,
        compact: bool = False,
    ) -> Tuple[Optional[str], Optional[str]]:
        pass
