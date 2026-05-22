VALID_INVEST_LEVELS = {"초급자", "중급자", "고급자", "전문가"}


def normalize_invest_level(value: str | None) -> str:
    if value in VALID_INVEST_LEVELS:
        return value
    return "초급자"


def invest_level_prompt(value: str | None) -> str:
    level = normalize_invest_level(value)
    policies = {
        "초급자": "전문용어를 줄이고, 필요한 용어는 쉬운 말로 풀어 설명한다. 결론을 먼저 말하고 숫자는 의미 중심으로 설명한다.",
        "중급자": "주요 지표 용어는 사용하되 짧은 해설을 붙이고, 수급·기술·재무 지표의 연결을 설명한다.",
        "고급자": "PER, PBR, ROE, RSI, 수급, 매크로 변수 등 지표 용어를 자연스럽게 사용하고 상충 신호와 리스크를 함께 설명한다.",
        "전문가": "기초 용어 설명은 최소화하고, 가정·민감도·밸류에이션·팩터 관점까지 압축적으로 설명한다.",
    }
    return (
        f"사용자 투자레벨: {level}\n"
        "투자레벨은 추천, 위험평가, 계산 결과를 바꾸는 데 사용하지 말고 설명 난이도와 용어 밀도 조정에만 사용한다.\n"
        f"설명 방식: {policies[level]}\n"
    )
