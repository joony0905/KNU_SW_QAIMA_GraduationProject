from __future__ import annotations

from app.core.config import DATA_DIR


KEYWORDS = {
    "REAL_ESTATE_CONCENTRATION": ["부동산", "유동성", "실거주"],
    "LIQUIDITY_SHORTAGE": ["유동성", "생활비", "비상자금"],
    "CASHFLOW_DEFICIT": ["현금흐름", "생활비", "지출"],
    "DEBT_INTEREST_RATE": ["대출", "금리", "변동금리"],
    "MARKET_VOLATILITY": ["시장", "변동성", "금융자산"],
    "PENSION_SHORTFALL": ["연금", "은퇴", "소진"],
}


def retrieve_guides(risk_codes: list[str], limit: int = 4) -> list[dict]:
    path = DATA_DIR / "rag_guides.md"
    text = path.read_text(encoding="utf-8") if path.exists() else ""
    paragraphs = [chunk.strip() for chunk in text.split("\n\n") if chunk.strip()]
    wanted = {kw for code in risk_codes for kw in KEYWORDS.get(code, [])}

    scored: list[tuple[int, str]] = []
    for paragraph in paragraphs:
        score = sum(1 for keyword in wanted if keyword in paragraph)
        if score:
            scored.append((score, paragraph))

    scored.sort(key=lambda item: item[0], reverse=True)
    return [
        {
            "source": "rag_guides.md",
            "section": "SAFE-WM guide",
            "snippet": paragraph[:300],
            "score": min(1.0, score / 3),
        }
        for score, paragraph in scored[:limit]
    ]

