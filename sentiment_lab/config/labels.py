"""고정 감성 라벨과 매핑 정보를 정의한다."""

NEGATIVE = 0
NEUTRAL = 1
POSITIVE = 2

id2label = {
    NEGATIVE: "negative",
    NEUTRAL: "neutral",
    POSITIVE: "positive",
}

label2id = {label: idx for idx, label in id2label.items()}


if __name__ == "__main__":
    print(id2label)
