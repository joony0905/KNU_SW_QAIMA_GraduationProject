"""감성 분류 실험용 평가 스켈레톤."""

from typing import Dict

from sklearn.metrics import accuracy_score, f1_score


def compute_basic_metrics(y_true, y_pred) -> Dict[str, float]:
    """나중에 확장할 수 있는 최소 평가 지표를 계산한다."""
    return {
        "accuracy": accuracy_score(y_true, y_pred),
        "macro_f1": f1_score(y_true, y_pred, average="macro"),
    }


def main() -> None:
    """평가 헬퍼의 간단한 스모크 테스트를 실행한다."""
    metrics = compute_basic_metrics([0, 1, 2], [0, 1, 2])
    print(metrics)


if __name__ == "__main__":
    main()
