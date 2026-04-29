from __future__ import annotations

import asyncio
import os
from pathlib import Path
from threading import Lock
from typing import List, Tuple

from app.models.feature2 import NewsSentimentRequest


ANALYSIS_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MODEL_PATH = "app/models/kf_deberta_sentiment_v2"
DEFAULT_MODEL_VERSION = "kf-deberta-sentiment-v2"
DEFAULT_INPUT_FORMAT_VERSION = "focus_detail_fallback_v1"
DEFAULT_BATCH_SIZE = 8
DEFAULT_MAX_LENGTH = 256
LABELS = ("negative", "neutral", "positive")

_load_lock = Lock()
_tokenizer = None
_model = None
_torch = None


async def score_news_sentiment_batch(req: NewsSentimentRequest) -> Tuple[List[dict], List[str]]:
    return await asyncio.to_thread(score_news_sentiment_batch_sync, req)


def score_news_sentiment_batch_sync(req: NewsSentimentRequest) -> Tuple[List[dict], List[str]]:
    warnings: List[str] = []
    prepared_items = []
    prepared_texts = []

    for item in req.items:
        if not item.url or not item.focus_text or not item.focus_text.strip():
            warnings.append("NEWS_SENTIMENT_LOCAL_INVALID_ITEM")
            continue
        prepared_items.append(item)
        prepared_texts.append(build_model_input(item.focus_text))

    if not prepared_items:
        return [], dedupe_warnings(warnings)

    try:
        tokenizer, model, torch = load_local_model()
    except FileNotFoundError:
        return [], dedupe_warnings(warnings + ["NEWS_SENTIMENT_LOCAL_MODEL_NOT_FOUND"])
    except Exception:
        return [], dedupe_warnings(warnings + ["NEWS_SENTIMENT_LOCAL_LOAD_FAILED"])

    batch_size = read_int_env("NEWS_SENTIMENT_LOCAL_BATCH_SIZE", DEFAULT_BATCH_SIZE)
    max_length = read_int_env("NEWS_SENTIMENT_LOCAL_MAX_LENGTH", DEFAULT_MAX_LENGTH)
    model_version = os.getenv("NEWS_SENTIMENT_MODEL_VERSION", DEFAULT_MODEL_VERSION)
    input_format_version = os.getenv("NEWS_SENTIMENT_INPUT_FORMAT_VERSION", DEFAULT_INPUT_FORMAT_VERSION)

    results: List[dict] = []
    try:
        for start in range(0, len(prepared_texts), batch_size):
            batch_texts = prepared_texts[start:start + batch_size]
            batch_items = prepared_items[start:start + batch_size]
            encoded = tokenizer(
                batch_texts,
                padding=True,
                truncation=True,
                max_length=max_length,
                return_tensors="pt",
            )
            with torch.inference_mode():
                logits = model(**encoded).logits
                probs = torch.softmax(logits, dim=-1).cpu().tolist()

            for item, row in zip(batch_items, probs):
                negative_prob = float(row[0])
                neutral_prob = float(row[1])
                positive_prob = float(row[2])
                predicted_index = max(range(len(row)), key=row.__getitem__)
                # neutral 확률은 방향성이 모호하다는 신호이므로 운영 점수에서 방향 점수를 감쇠한다.
                sentiment_score = calculate_sentiment_score(negative_prob, neutral_prob, positive_prob)
                results.append({
                    "url": item.url,
                    "sentiment_score": sentiment_score,
                    "predicted_label": LABELS[predicted_index],
                    "negative_prob": negative_prob,
                    "neutral_prob": neutral_prob,
                    "positive_prob": positive_prob,
                    "model_version": model_version,
                    "input_format_version": input_format_version,
                })
    except Exception:
        return results, dedupe_warnings(warnings + ["NEWS_SENTIMENT_LOCAL_INFERENCE_FAILED"])

    return results, dedupe_warnings(warnings)


def load_local_model():
    global _tokenizer, _model, _torch

    if _tokenizer is not None and _model is not None and _torch is not None:
        return _tokenizer, _model, _torch

    with _load_lock:
        if _tokenizer is not None and _model is not None and _torch is not None:
            return _tokenizer, _model, _torch

        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        torch.set_num_threads(read_int_env("NEWS_SENTIMENT_TORCH_NUM_THREADS", 2))
        model_path = resolve_model_path(os.getenv("NEWS_SENTIMENT_MODEL_PATH", DEFAULT_MODEL_PATH))
        if not model_path.exists():
            raise FileNotFoundError(model_path)

        tokenizer = AutoTokenizer.from_pretrained(model_path)
        model = AutoModelForSequenceClassification.from_pretrained(model_path)
        model.to(torch.device("cpu"))
        model.eval()

        _tokenizer = tokenizer
        _model = model
        _torch = torch
        return _tokenizer, _model, _torch


def resolve_model_path(raw_path: str) -> Path:
    model_path = Path(raw_path)
    if model_path.is_absolute():
        return model_path
    return ANALYSIS_ROOT / model_path


def build_model_input(focus_text: str) -> str:
    normalized_focus = " ".join(focus_text.split())
    # v3 확장 지점: 운영 로그를 보고 [TITLE]/[FOCUS]/[DETAIL] 조합별 성능을 비교할 때
    # NEWS_SENTIMENT_INPUT_FORMAT_VERSION 값을 바꾸고 이 포맷을 함께 변경한다.
    return f"[FOCUS]\n{normalized_focus}\n\n[DETAIL]\n{normalized_focus}"


def calculate_sentiment_score(negative_prob: float, neutral_prob: float, positive_prob: float) -> float:
    raw_direction_score = positive_prob - negative_prob
    directional_confidence = 1.0 - neutral_prob
    sentiment_score = raw_direction_score * directional_confidence
    return max(-1.0, min(1.0, sentiment_score))


def read_int_env(name: str, default_value: int) -> int:
    try:
        value = int(os.getenv(name, str(default_value)))
    except ValueError:
        return default_value
    return max(1, value)


def dedupe_warnings(warnings: List[str]) -> List[str]:
    return list(dict.fromkeys(warning for warning in warnings if warning))
