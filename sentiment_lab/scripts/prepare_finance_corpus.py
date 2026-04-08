"""finance_data.csv를 QAIMA 1차 파인튜닝용 CSV로 변환한다.

입력:
- sentiment_lab/data/raw/finance_data.csv

출처: https://github.com/ukairia777/finance_sentiment_corpus

출력:
- sentiment_lab/data/processed/finance_sentiment_train.csv

원본의 labels/lables, sentence, kor_sentence 컬럼을 읽어
Trainer에 바로 넣기 쉬운 text, label, source 포맷으로 정리한다.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

try:
    from config.labels import label2id
    from config.paths import PROCESSED_DATA_DIR, RAW_DATA_DIR, ensure_directories
except ImportError as exc:
    raise RuntimeError(
        "config 모듈을 불러오지 못했습니다. "
        "sentiment_lab 루트 기준으로 실행 중인지 확인해 주세요."
    ) from exc

SOURCE_NAME = "finance_sentiment_corpus"
DEFAULT_INPUT_PATH = RAW_DATA_DIR / "finance_data.csv"
DEFAULT_OUTPUT_PATH = PROCESSED_DATA_DIR / "finance_sentiment_train.csv"
VALID_LABEL_IDS = set(label2id.values())


def load_raw_csv(input_path: Path) -> pd.DataFrame:
    """인코딩 fallback을 사용해 원본 CSV를 불러온다."""
    if not input_path.exists():
        raise FileNotFoundError(
            f"입력 CSV 파일을 찾을 수 없습니다: {input_path}"
        )

    encodings = ("utf-8", "utf-8-sig", "cp949")
    last_error = None

    for encoding in encodings:
        try:
            return pd.read_csv(input_path, encoding=encoding)
        except UnicodeDecodeError as exc:
            last_error = exc
        except Exception as exc:
            raise RuntimeError(
                f"CSV 파일을 읽는 중 오류가 발생했습니다: {input_path} / {exc}"
            ) from exc

    raise RuntimeError(
        f"지원된 인코딩으로 CSV를 읽지 못했습니다: {input_path} / {last_error}"
    )


def normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    """원본 컬럼명을 내부 표준 컬럼명으로 정규화한다."""
    renamed = df.rename(columns={"lables": "labels"}).copy()
    columns = set(renamed.columns)

    if "labels" not in columns:
        raise ValueError(
            "라벨 컬럼이 없습니다. 기대 컬럼: labels 또는 lables"
        )

    if "kor_sentence" not in columns and "sentence" not in columns:
        raise ValueError(
            "텍스트 컬럼이 없습니다. 기대 컬럼: kor_sentence 또는 sentence"
        )

    return renamed


def clean_text(text: object) -> str:
    """과한 정제 없이 최소한의 텍스트 정리를 수행한다."""
    if pd.isna(text):
        return ""

    cleaned = str(text)
    cleaned = re.sub(r"<[^>]+>", " ", cleaned)
    cleaned = cleaned.replace("\r", " ").replace("\n", " ").replace("\t", " ")
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def build_text(df: pd.DataFrame, min_text_length: int) -> pd.DataFrame:
    """kor_sentence 우선, sentence fallback으로 text 컬럼을 만든다."""
    working = df.copy()

    kor_series = (
        working["kor_sentence"]
        if "kor_sentence" in working.columns
        else pd.Series([""] * len(working), index=working.index)
    )
    sentence_series = (
        working["sentence"]
        if "sentence" in working.columns
        else pd.Series([""] * len(working), index=working.index)
    )

    kor_clean = kor_series.map(clean_text)
    sentence_clean = sentence_series.map(clean_text)

    working["text"] = kor_clean.where(kor_clean != "", sentence_clean)
    working["text"] = working["text"].map(clean_text)
    working = working[working["text"].str.len() >= min_text_length].copy()

    return working


def normalize_labels(df: pd.DataFrame) -> pd.DataFrame:
    """문자열 라벨을 고정 정수 라벨로 변환한다."""
    working = df.copy()
    raw_labels = working["labels"].map(
        lambda value: "" if pd.isna(value) else str(value).strip().lower()
    )

    blank_count = int((raw_labels == "").sum())
    if blank_count:
        raise ValueError(f"비어 있는 라벨이 {blank_count}건 있습니다.")

    unknown_mask = ~raw_labels.isin(label2id.keys())
    unknown_count = int(unknown_mask.sum())
    if unknown_count:
        unknown_labels = sorted(raw_labels[unknown_mask].unique().tolist())
        raise ValueError(
            f"알 수 없는 라벨이 {unknown_count}건 있습니다: {unknown_labels}"
        )

    working["label"] = raw_labels.map(label2id)
    return working


def validate_dataframe(df: pd.DataFrame) -> None:
    """최종 데이터프레임의 최소 품질 조건을 검증한다."""
    required_columns = {"text", "label", "source"}
    missing_columns = required_columns - set(df.columns)
    if missing_columns:
        raise ValueError(
            f"최종 데이터프레임에 필요한 컬럼이 없습니다: {sorted(missing_columns)}"
        )

    if df["text"].isna().any():
        raise ValueError("text 컬럼에 null 값이 있습니다.")

    if df["label"].isna().any():
        raise ValueError("label 컬럼에 null 값이 있습니다.")

    invalid_labels = sorted(set(df["label"].unique()) - VALID_LABEL_IDS)
    if invalid_labels:
        raise ValueError(
            f"label 컬럼에 허용되지 않은 값이 있습니다: {invalid_labels}"
        )


def save_processed_csv(df: pd.DataFrame, output_path: Path) -> None:
    """가공된 데이터프레임을 CSV로 저장한다."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")


def print_stats(raw_count: int, processed_df: pd.DataFrame, output_path: Path) -> None:
    """행 수, 라벨 분포, 길이 통계를 콘솔에 출력한다."""
    valid_count = len(processed_df)
    removed_count = raw_count - valid_count
    duplicate_count = int(processed_df["text"].duplicated().sum())
    text_lengths = processed_df["text"].str.len()
    label_distribution = (
        processed_df["label"]
        .map({0: "negative", 1: "neutral", 2: "positive"})
        .value_counts()
        .reindex(["negative", "neutral", "positive"], fill_value=0)
    )

    print(f"총 원본 행 수: {raw_count}")
    print(f"유효 행 수: {valid_count}")
    print(f"제거된 행 수: {removed_count}")
    print(
        "라벨 분포: "
        f"negative={label_distribution['negative']}, "
        f"neutral={label_distribution['neutral']}, "
        f"positive={label_distribution['positive']}"
    )
    print(
        "text 길이 통계: "
        f"mean={text_lengths.mean():.2f}, "
        f"median={text_lengths.median():.2f}"
    )
    print(f"중복 text 개수: {duplicate_count}")
    print(f"저장 경로: {output_path}")


def parse_args() -> argparse.Namespace:
    """CLI 인자를 파싱한다."""
    parser = argparse.ArgumentParser(
        description="finance_sentiment_corpus 원본 CSV를 QAIMA 학습용 CSV로 변환합니다."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_INPUT_PATH,
        help="입력 CSV 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help="출력 CSV 경로",
    )
    parser.add_argument(
        "--remove-duplicates",
        action="store_true",
        help="동일한 text의 exact duplicate를 제거합니다.",
    )
    parser.add_argument(
        "--min-text-length",
        type=int,
        default=2,
        help="유지할 최소 text 길이입니다.",
    )
    return parser.parse_args()


def main() -> None:
    """원본 금융 감성 코퍼스를 학습용 CSV로 변환한다."""
    args = parse_args()
    ensure_directories()

    if args.min_text_length < 1:
        raise ValueError("--min-text-length는 1 이상이어야 합니다.")

    raw_df = load_raw_csv(args.input)
    raw_count = len(raw_df)

    normalized_df = normalize_columns(raw_df)
    normalized_df = build_text(normalized_df, min_text_length=args.min_text_length)
    normalized_df = normalize_labels(normalized_df)

    processed_df = normalized_df[["text", "label"]].copy()
    processed_df["source"] = SOURCE_NAME

    if args.remove_duplicates:
        processed_df = processed_df.drop_duplicates(subset=["text"]).copy()

    validate_dataframe(processed_df)
    save_processed_csv(processed_df, args.output)
    print_stats(raw_count=raw_count, processed_df=processed_df, output_path=args.output)


if __name__ == "__main__":
    # 실행 예시:
    # python scripts/prepare_finance_corpus.py
    # python scripts/prepare_finance_corpus.py --remove-duplicates --min-text-length 2
    main()


# 출력 CSV 예시:
# text,label,source
# "Gran에 따르면, 그 회사는 회사가 성장하고 있는 곳이지만, 모든 생산을 러시아로 옮길 계획이 없다고 한다.",1,finance_sentiment_corpus
# "국제 전자산업 회사인 엘코텍은 탈린 공장에서 수십 명의 직원을 해고했으며, 이전의 해고와는 달리 회사는 사무직 직원 수를 줄였다고 일간 포스티메스가 보도했다.",0,finance_sentiment_corpus
# "새로운 생산공장으로 인해 회사는 예상되는 수요 증가를 충족시킬 수 있는 능력을 증가시키고 원자재 사용을 개선하여 생산 수익성을 높일 것이다.",2,finance_sentiment_corpus
