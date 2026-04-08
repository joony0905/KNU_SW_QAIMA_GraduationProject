"""감성 모델 입력 텍스트를 구성하는 헬퍼 모음."""

from typing import Optional


def _clean_text(value: Optional[str]) -> str:
    """입력이 비어 있으면 빈 문자열을, 아니면 trim된 문자열을 반환한다."""
    if value is None:
        return ""
    return str(value).strip()


def build_model_input(
    title: str,
    focus_text: Optional[str] = None,
    detail_summary: Optional[str] = None,
) -> str:
    """누락된 섹션에 fallback 규칙을 적용해 모델 입력 문자열을 만든다."""
    title_text = _clean_text(title)
    focus = _clean_text(focus_text) or _clean_text(detail_summary) or title_text
    detail = _clean_text(detail_summary) or title_text

    return "\n".join(
        [
            "[TITLE]",
            title_text,
            "",
            "[FOCUS]",
            focus,
            "",
            "[DETAIL]",
            detail,
        ]
    )


if __name__ == "__main__":
    sample = build_model_input(
        title="샘플 제목",
        focus_text="핵심 요약",
        detail_summary="상세 설명",
    )
    print(sample)
