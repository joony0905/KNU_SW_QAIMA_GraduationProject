"""실제 뉴스 평가 샘플 전처리용 스크립트 뼈대."""

from config.paths import INTERIM_DATA_DIR, RAW_DATA_DIR, ensure_directories


def main() -> None:
    """실제 뉴스 전처리 흐름에 사용할 경로를 출력한다."""
    ensure_directories()
    source_path = RAW_DATA_DIR / "real_news_200.csv"
    target_path = INTERIM_DATA_DIR / "real_news_200.prepared.csv"

    print(f"Expected source: {source_path}")
    print(f"Planned output: {target_path}")
    print("TODO: load manually collected news dataset")
    print("TODO: map title/focus/detail fields into model input text")
    print("TODO: validate labels and export prepared evaluation set")


if __name__ == "__main__":
    main()
