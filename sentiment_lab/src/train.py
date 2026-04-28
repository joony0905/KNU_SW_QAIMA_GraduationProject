"""감성 분류 모델을 학습한다.

1차 finance_sentiment_corpus 학습과 2차 QAIMA 실제 뉴스 적응 학습을
같은 진입점에서 실행할 수 있도록 구성한다.
"""

from __future__ import annotations

import argparse
import json
import sys
from inspect import signature
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from datasets import Dataset, DatasetDict
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
)

# 스크립트 단독 실행 시에도 sentiment_lab 루트를 import 경로에 추가한다.
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from config.labels import NEGATIVE, NEUTRAL, POSITIVE, id2label, label2id
from config.paths import LOGS_DIR, MODELS_DIR, PROCESSED_DATA_DIR, ensure_directories
from src.dataset import validate_dataframe_columns
from src.utils import save_json, set_seed

DEFAULT_INPUT_PATH = PROCESSED_DATA_DIR / "finance_sentiment_train.csv"
DEFAULT_SECOND_INPUT_PATH = PROCESSED_DATA_DIR / "real_news_sentiment_train.csv"
DEFAULT_OUTPUT_DIR = MODELS_DIR / "kf_deberta_sentiment_v1"
DEFAULT_MODEL_NAME = "kakaobank/kf-deberta-base"
VALID_LABEL_IDS = {NEGATIVE, NEUTRAL, POSITIVE}


def load_training_dataframe(input_path: Path) -> pd.DataFrame:
    """학습 CSV를 로드하고 기본적인 파일 수준 오류를 검증한다."""
    if not input_path.exists():
        raise FileNotFoundError(
            f"학습 데이터 파일을 찾을 수 없습니다: {input_path}"
        )

    try:
        dataframe = pd.read_csv(input_path)
    except Exception as exc:  # pragma: no cover
        raise ValueError(f"CSV 파일을 읽는 중 오류가 발생했습니다: {input_path}") from exc

    return dataframe


def validate_training_dataframe(dataframe: pd.DataFrame) -> pd.DataFrame:
    """필수 컬럼과 라벨 규약을 확인하고 학습에 사용할 행만 남긴다."""
    validate_dataframe_columns(dataframe.columns)

    validated = dataframe.copy()

    # 공백 텍스트와 null 라벨은 학습에 직접 사용할 수 없어 제거한다.
    text_series = validated["text"]
    text_mask = text_series.notna() & text_series.astype(str).str.strip().ne("")
    label_mask = validated["label"].notna()
    validated = validated.loc[text_mask & label_mask].copy()
    validated["text"] = validated["text"].astype(str)

    if validated.empty:
        raise ValueError("유효한 학습 데이터가 없습니다. text/label 값을 확인하세요.")

    try:
        validated["label"] = validated["label"].astype(int)
    except ValueError as exc:
        raise ValueError("label 컬럼은 0, 1, 2의 정수형이어야 합니다.") from exc

    invalid_labels = sorted(set(validated["label"].unique()) - VALID_LABEL_IDS)
    if invalid_labels:
        raise ValueError(
            f"label 컬럼에 허용되지 않은 값이 있습니다: {invalid_labels}. "
            "허용 값은 0(negative), 1(neutral), 2(positive) 입니다."
        )

    label_counts = validated["label"].value_counts().sort_index()
    too_small_labels = label_counts[label_counts < 2]
    if not too_small_labels.empty:
        raise ValueError(
            "stratify 분할을 위해 각 라벨은 최소 2개 이상 필요합니다: "
            f"{too_small_labels.to_dict()}"
        )

    return validated


def split_dataframe(
    dataframe: pd.DataFrame,
    val_size: float,
    seed: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """라벨 비율을 유지한 채 train/validation 데이터프레임으로 분할한다."""
    if not 0.0 < val_size < 1.0:
        raise ValueError(f"val_size는 0과 1 사이여야 합니다: {val_size}")

    try:
        train_df, validation_df = train_test_split(
            dataframe,
            test_size=val_size,
            stratify=dataframe["label"],
            random_state=seed,
        )
    except ValueError as exc:
        raise ValueError(
            "train/validation 분할에 실패했습니다. 데이터 수와 라벨 분포를 확인하세요."
        ) from exc

    return train_df.reset_index(drop=True), validation_df.reset_index(drop=True)


def oversample_training_dataframe(
    dataframe: pd.DataFrame,
    target_count: int | None,
    seed: int,
) -> pd.DataFrame:
    """train split에만 minority class oversampling을 적용한다.

    validation 데이터는 실제 서비스 분포를 보존해야 하므로 이 함수에 넣지 않는다.
    target_count가 없으면 train split 내부의 최대 class count에 맞춘다.
    이미 target_count 이상인 class는 줄이지 않는다.
    """
    if dataframe.empty:
        return dataframe

    label_counts = dataframe["label"].value_counts().sort_index()
    if target_count is None:
        target_count = int(label_counts.max())
    if target_count <= 0:
        raise ValueError(f"oversample target은 1 이상이어야 합니다: {target_count}")

    sampled_frames: list[pd.DataFrame] = []
    for label_id in sorted(VALID_LABEL_IDS):
        label_frame = dataframe[dataframe["label"] == label_id]
        if label_frame.empty:
            continue
        if len(label_frame) >= target_count:
            sampled_frames.append(label_frame)
            continue
        replace = True
        sampled = label_frame.sample(
            n=target_count,
            replace=replace,
            random_state=seed + int(label_id),
        )
        sampled_frames.append(sampled)

    oversampled = pd.concat(sampled_frames, ignore_index=True)
    return oversampled.sample(frac=1.0, random_state=seed).reset_index(drop=True)


def tokenize_batch(batch: dict[str, list[Any]], tokenizer, max_length: int) -> dict[str, Any]:
    """text 컬럼만 사용해 토큰화한다."""
    return tokenizer(
        batch["text"],
        truncation=True,
        max_length=max_length,
    )


def build_datasets(
    train_df: pd.DataFrame,
    validation_df: pd.DataFrame,
    tokenizer,
    max_length: int,
) -> DatasetDict:
    """학습용 데이터프레임을 Hugging Face DatasetDict로 변환한다."""
    dataset_dict = DatasetDict(
        {
            "train": Dataset.from_pandas(
                train_df[["text", "label"]], preserve_index=False
            ),
            "validation": Dataset.from_pandas(
                validation_df[["text", "label"]], preserve_index=False
            ),
        }
    )

    tokenized = dataset_dict.map(
        lambda batch: tokenize_batch(batch, tokenizer=tokenizer, max_length=max_length),
        batched=True,
        desc="Tokenizing dataset",
    )
    tokenized = tokenized.rename_column("label", "labels")
    tokenized = tokenized.remove_columns(["text"])

    return tokenized


def compute_metrics(eval_pred) -> dict[str, float]:
    """정확도와 macro F1을 계산한다."""
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=-1)

    return {
        "accuracy": float(accuracy_score(labels, predictions)),
        "macro_f1": float(f1_score(labels, predictions, average="macro", zero_division=0)),
        "negative_f1": float(
            f1_score(labels, predictions, labels=[NEGATIVE], average="macro", zero_division=0)
        ),
        "neutral_f1": float(
            f1_score(labels, predictions, labels=[NEUTRAL], average="macro", zero_division=0)
        ),
        "positive_f1": float(
            f1_score(labels, predictions, labels=[POSITIVE], average="macro", zero_division=0)
        ),
    }


def save_metrics(metrics: dict[str, Any], output_path: Path) -> None:
    """Trainer 결과를 JSON 직렬화 가능한 형태로 저장한다."""
    serializable_metrics: dict[str, Any] = {}
    for key, value in metrics.items():
        if isinstance(value, np.generic):
            serializable_metrics[key] = value.item()
        else:
            serializable_metrics[key] = value

    save_json(serializable_metrics, output_path)


def parse_args() -> argparse.Namespace:
    """CLI 인자를 파싱한다."""
    parser = argparse.ArgumentParser(
        description="QAIMA 뉴스 감성 분류 학습 스크립트"
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT_PATH)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--model-name", type=str, default=DEFAULT_MODEL_NAME)
    parser.add_argument(
        "--stage",
        choices=("first", "second"),
        default="first",
        help="학습 단계 메타데이터입니다. second 선택 시 input/output 기본값만 2차용으로 바뀝니다.",
    )
    parser.add_argument("--max-length", type=int, default=256)
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--train-batch-size", type=int, default=8)
    parser.add_argument("--eval-batch-size", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--weight-decay", type=float, default=0.01)
    parser.add_argument("--val-size", type=float, default=0.1)
    parser.add_argument(
        "--oversample",
        action="store_true",
        help="train split에만 minority class oversampling을 적용합니다.",
    )
    parser.add_argument(
        "--oversample-target",
        type=int,
        default=None,
        help="각 라벨별 train row 목표 수입니다. 생략하면 train split의 최대 라벨 수에 맞춥니다.",
    )
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    if args.stage == "second":
        if args.input == DEFAULT_INPUT_PATH:
            args.input = DEFAULT_SECOND_INPUT_PATH
        if args.output_dir == DEFAULT_OUTPUT_DIR:
            args.output_dir = MODELS_DIR / "kf_deberta_sentiment_v2"
        if args.oversample and args.oversample_target is None:
            args.oversample_target = 350

    return args


def build_training_arguments(
    output_dir: Path,
    epochs: float,
    train_batch_size: int,
    eval_batch_size: int,
    learning_rate: float,
    weight_decay: float,
) -> TrainingArguments:
    """설치된 transformers 버전에 맞는 TrainingArguments를 만든다."""
    logging_dir = LOGS_DIR / output_dir.name
    logging_dir.mkdir(parents=True, exist_ok=True)

    training_kwargs: dict[str, Any] = {
        "output_dir": str(output_dir),
        "logging_dir": str(logging_dir),
        "num_train_epochs": epochs,
        "per_device_train_batch_size": train_batch_size,
        "per_device_eval_batch_size": eval_batch_size,
        "learning_rate": learning_rate,
        "weight_decay": weight_decay,
        "save_strategy": "epoch",
        "logging_strategy": "epoch",
        "load_best_model_at_end": True,
        "metric_for_best_model": "macro_f1",
        "greater_is_better": True,
        "save_total_limit": 2,
        "report_to": [],
    }

    # transformers 버전에 따라 evaluation_strategy 또는 eval_strategy를 사용한다.
    argument_signature = signature(TrainingArguments.__init__)
    if "evaluation_strategy" in argument_signature.parameters:
        training_kwargs["evaluation_strategy"] = "epoch"
    elif "eval_strategy" in argument_signature.parameters:
        training_kwargs["eval_strategy"] = "epoch"
    else:  # pragma: no cover
        raise RuntimeError(
            "현재 transformers 버전에서 평가 전략 인자를 찾을 수 없습니다."
        )

    return TrainingArguments(**training_kwargs)


def main() -> None:
    """학습 데이터 로드부터 모델 저장까지 전체 파이프라인을 실행한다."""
    args = parse_args()
    ensure_directories()
    set_seed(args.seed)

    input_path = args.input.resolve()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    dataframe = load_training_dataframe(input_path)
    original_count = len(dataframe)
    dataframe = validate_training_dataframe(dataframe)
    cleaned_count = len(dataframe)

    train_df, validation_df = split_dataframe(
        dataframe=dataframe,
        val_size=args.val_size,
        seed=args.seed,
    )
    original_train_df = train_df.copy()
    if args.oversample:
        train_df = oversample_training_dataframe(
            dataframe=train_df,
            target_count=args.oversample_target,
            seed=args.seed,
        )

    print(f"Loaded rows: {cleaned_count} (raw: {original_count})")
    print(f"Train rows: {len(train_df)} (before oversampling: {len(original_train_df)})")
    print(f"Validation rows: {len(validation_df)}")
    print(
        "Label distribution: "
        + json.dumps(
            {id2label[int(k)]: int(v) for k, v in dataframe["label"].value_counts().sort_index().items()},
            ensure_ascii=False,
        )
    )
    print(
        "Train label distribution: "
        + json.dumps(
            {id2label[int(k)]: int(v) for k, v in train_df["label"].value_counts().sort_index().items()},
            ensure_ascii=False,
        )
    )
    print(
        "Validation label distribution: "
        + json.dumps(
            {id2label[int(k)]: int(v) for k, v in validation_df["label"].value_counts().sort_index().items()},
            ensure_ascii=False,
        )
    )
    print(f"Save path: {output_dir}")

    tokenizer = AutoTokenizer.from_pretrained(args.model_name)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_name,
        num_labels=3,
        id2label=id2label,
        label2id=label2id,
    )

    tokenized_datasets = build_datasets(
        train_df=train_df,
        validation_df=validation_df,
        tokenizer=tokenizer,
        max_length=args.max_length,
    )
    data_collator = DataCollatorWithPadding(tokenizer=tokenizer)
    training_args = build_training_arguments(
        output_dir=output_dir,
        epochs=args.epochs,
        train_batch_size=args.train_batch_size,
        eval_batch_size=args.eval_batch_size,
        learning_rate=args.learning_rate,
        weight_decay=args.weight_decay,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_datasets["train"],
        eval_dataset=tokenized_datasets["validation"],
        data_collator=data_collator,
        compute_metrics=compute_metrics,
    )

    trainer.train()

    eval_metrics = trainer.evaluate()
    trainer.save_model(str(output_dir))
    model.save_pretrained(output_dir)
    tokenizer.save_pretrained(output_dir)
    save_metrics(eval_metrics, output_dir / "eval_metrics.json")
    save_metrics(
        {
            "stage": args.stage,
            "input": str(input_path),
            "base_model": args.model_name,
            "output_dir": str(output_dir),
            "max_length": args.max_length,
            "epochs": args.epochs,
            "learning_rate": args.learning_rate,
            "weight_decay": args.weight_decay,
            "val_size": args.val_size,
            "oversample": args.oversample,
            "oversample_target": args.oversample_target,
            "raw_label_distribution": {
                id2label[int(k)]: int(v)
                for k, v in dataframe["label"].value_counts().sort_index().items()
            },
            "train_label_distribution": {
                id2label[int(k)]: int(v)
                for k, v in train_df["label"].value_counts().sort_index().items()
            },
            "validation_label_distribution": {
                id2label[int(k)]: int(v)
                for k, v in validation_df["label"].value_counts().sort_index().items()
            },
        },
        output_dir / "training_config.json",
    )

    print("Evaluation metrics saved to:", output_dir / "eval_metrics.json")
    print("Training config saved to:", output_dir / "training_config.json")


if __name__ == "__main__":
    main()
