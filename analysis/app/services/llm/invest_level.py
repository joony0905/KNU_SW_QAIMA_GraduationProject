VALID_INVEST_LEVELS = {"초급자", "중급자", "고급자", "전문가"}


def normalize_invest_level(value: str | None) -> str:
    if value in VALID_INVEST_LEVELS:
        return value
    return "초급자"


def invest_level_prompt(value: str | None) -> str:
    level = normalize_invest_level(value)
    policies = {
        "초급자": "전문용어를 줄이고, 필요한 용어는 쉬운 말로 풀어 설명한다. 결론을 먼저 말하고 숫자는 의미 중심으로 설명한다.",
        "중급자": "주요 지표 용어는 사용하되 짧은 해설을 붙이고, 수급·기술·재무 지표의 연결에서 보이는 간접 신호를 설명한다.",
        "고급자": "PER, PBR, ROE, RSI, 수급, 매크로 변수 등 지표 용어를 자연스럽게 사용하고 상충 신호, 리스크, 점검 포인트를 함께 설명한다.",
        "전문가": "기초 용어 설명은 최소화하고, 가정·민감도·밸류에이션·팩터 관점에서 데이터가 시사하는 조건부 인사이트를 압축적으로 설명한다.",
    }
    return (
        f"사용자 투자레벨: {level}\n"
        "투자레벨은 추천, 위험평가, 계산 결과를 바꾸는 데 사용하지 말고 설명 난이도, 용어 밀도, 인사이트 깊이 조정에만 사용한다.\n"
        "제공된 데이터에서 합리적으로 이어지는 간접 인사이트와 점검 포인트는 제시하되, 매수/매도/비중 조절 같은 행동 지시는 하지 않는다.\n"
        "'시사한다', '점검할 만하다', '가능성이 있다', '보조 신호로 볼 수 있다'처럼 근거 수준을 드러내는 표현을 사용한다.\n"
        f"설명 방식: {policies[level]}\n"
    )


def normalize_language_code(value: str | None) -> str:
    if value and value.strip().lower().startswith("en"):
        return "en"
    return "ko"


def output_language_prompt(value: str | None) -> str:
    if normalize_language_code(value) == "en":
        return (
            "Output language: English.\n"
            "All user-facing JSON string values such as title, summary, bullets, risks, and conclusion must be written in English.\n"
            "Keep JSON keys unchanged.\n"
            "Do not use Korean except for proper nouns, original company names, and market names when necessary.\n"
        )
    return (
        "출력 언어: 한국어.\n"
        "title, summary, bullets, risks, conclusion 같은 사용자 표시 문구는 모두 한국어로 작성한다.\n"
        "JSON 키 이름은 그대로 유지한다.\n"
    )
