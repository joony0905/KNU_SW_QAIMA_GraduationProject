"""감성 실험용 데이터프레임 최소 검증 헬퍼."""

from typing import Iterable


REQUIRED_COLUMNS = ("text", "label")


def validate_dataframe_columns(columns: Iterable[str]) -> None:
    """필수 text/label 컬럼이 없으면 오류를 발생시킨다."""
    column_set = set(columns)
    missing = [column for column in REQUIRED_COLUMNS if column not in column_set]
    if missing:
        raise ValueError(f"Missing required columns: {missing}")


def validate_dataframe(df) -> None:
    """데이터프레임이 필요한 스키마를 갖췄는지 검증한다."""
    validate_dataframe_columns(df.columns)


if __name__ == "__main__":
    validate_dataframe_columns(["text", "label"])
    print("Dataset schema is valid.")
