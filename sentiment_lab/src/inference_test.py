"""sentiment_lab 샌드박스용 단건 추론 스모크 테스트."""

from typing import Dict

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from config.labels import NEGATIVE, NEUTRAL, POSITIVE
from src.preprocess import build_model_input

MODEL_NAME = "kakaobank/kf-deberta-base"


def load_inference_components(model_name: str = MODEL_NAME):
    """추론에 필요한 토크나이저와 분류 모델을 불러온다."""
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSequenceClassification.from_pretrained(model_name, num_labels=3)
    model.eval()
    return tokenizer, model


def predict_single_text(text: str, model_name: str = MODEL_NAME) -> Dict[str, float]:
    """단건 텍스트 추론을 수행하고 클래스 확률과 점수를 반환한다."""
    tokenizer, model = load_inference_components(model_name)
    encoded = tokenizer(text, return_tensors="pt", truncation=True, padding=True)

    with torch.no_grad():
        logits = model(**encoded).logits
        probabilities = torch.softmax(logits, dim=-1).squeeze(0)

    negative_prob = float(probabilities[NEGATIVE].item())
    neutral_prob = float(probabilities[NEUTRAL].item())
    positive_prob = float(probabilities[POSITIVE].item())

    return {
        "negativeProb": negative_prob,
        "neutralProb": neutral_prob,
        "positiveProb": positive_prob,
        "score": positive_prob - negative_prob,
    }


def main() -> None:
    """포맷된 뉴스 입력 텍스트로 단건 추론 예시를 실행한다."""
    sample_text = build_model_input(
        title="카카오뱅크, 1분기 실적 기대감 확대",
        focus_text="시장에서는 이익 개선 가능성에 주목하고 있다.",
        detail_summary="비이자 수익 회복과 대출 성장 전망이 투자 심리를 지지했다.",
    )
    result = predict_single_text(sample_text)
    print(result)


if __name__ == "__main__":
    main()
