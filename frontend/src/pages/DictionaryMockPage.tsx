// src/pages/DictionaryMockPage.tsx
import { useState, useMemo } from "react";

const HANGUL_LETTERS = [
  "ㄱ",
  "ㄴ",
  "ㄷ",
  "ㄹ",
  "ㅁ",
  "ㅂ",
  "ㅅ",
  "ㅇ",
  "ㅈ",
  "ㅊ",
  "ㅋ",
  "ㅌ",
  "ㅍ",
  "ㅎ",
];

const ALPHABET_ROW1 = [
  "A",
  "B",
  "C",
  "D",
  "E",
  "F",
  "G",
  "H",
  "I",
  "J",
  "K",
  "L",
  "M",
];

const ALPHABET_ROW2 = [
  "N",
  "O",
  "P",
  "Q",
  "R",
  "S",
  "T",
  "U",
  "V",
  "W",
  "X",
  "Y",
  "Z",
];

// ---- 타입 & 더미 데이터 ----

export interface DictionaryTerm {
  id: string;
  term: string; // 한글 용어
  termEn?: string; // 영문 약어/이름
  korInitial?: string; // 초성
  alphaInitial?: string;
  fullDefinition: string;
  category?: string;
}

const DICTIONARY_TERMS: DictionaryTerm[] = [
  {
    id: "hdri",
    term: "가계부실위험지수",
    termEn: "HDRI",
    korInitial: "ㄱ",
    alphaInitial: "H",
    fullDefinition:
      "가구의 소득 흐름은 물론 금융 및 실물 자산까지 종합적으로 고려하여 가계부채의 부실위험을 평가하는 지표로, 가계의 채무상환능력을 소득 측면에서 평가하는 원리금상환비율(DSR; Debt Service Ratio)과 자산 측면에서 평가하는 부채/자산비율(DTA; Debt To Asset Ratio)을 결합하여 산출한 지수이다. 가계부실위험지수는 가구의 DSR과 DTA가 각각 40%, 100%일 때 100의 값을 갖도록 설정되어 있으며, 동 지수가 100을 초과하는 가구를 ‘위험가구’로 분류한다. 위험가구는 소득 및 자산 측면에서 모두 취약한 ‘고위험가구’, 자산 측면에서 취약한 ‘고DTA가구’, 소득 측면에서 취약한 ‘고DSR가구’로 구분할 수 있다. 다만 위험 및 고위험 가구는 가구의 채무상환능력 취약성 정도를 평가하기 위한 것이며 이들 가구가 당장 채무상환 불이행, 즉 임계상황에 직면한 것을 의미하지 않는다.",
    category: "경제지표",
  },
  {
    id: "gajebu",
    term: "가계수지",
    korInitial: "ㄱ",
    alphaInitial: "H",
    fullDefinition:
      "가계의 소득과 지출 구조를 보여주는 통계로, 가계의 소비 성향과 저축률을 파악하는 데 활용된다.",
    category: "경제지표",
  },
  {
    id: "gaje-sun",
    term: "가계순저축률",
    korInitial: "ㄱ",
    alphaInitial: "H",
    fullDefinition:
      "가계의 처분가능소득에서 소비지출을 제외한 순저축이 차지하는 비율로, 가계의 저축 성향을 나타낸다.",
    category: "경제지표",
  },
  {
    id: "gaje-credit",
    term: "가계신용통계",
    korInitial: "ㄱ",
    alphaInitial: "H",
    fullDefinition:
      "가계가 보유한 금융기관 및 기타 차입금 등을 포함한 신용 규모를 파악하기 위한 통계이다.",
    category: "통계",
  },
  {
    id: "disposable",
    term: "가계처분가능소득",
    korInitial: "ㄱ",
    alphaInitial: "D",
    fullDefinition:
      "가계가 세금과 사회보장기여금 등을 납부한 후 자유롭게 소비와 저축에 사용할 수 있는 소득을 말한다.",
    category: "소득",
  },
];

// ---- 컴포넌트 ----

export default function DictionaryMockPage() {
  // 검색/필터 상태
  const [searchQuery, setSearchQuery] = useState("");
  const [searchError, setSearchError] = useState<string | null>(null);
  const [selectedHangul, setSelectedHangul] = useState<string | null>(null);
  const [selectedAlpha, setSelectedAlpha] = useState<string | null>(null);

  // 선택된 용어 (좌측 카드)
  const [selectedTermId, setSelectedTermId] = useState<string>(
    DICTIONARY_TERMS[0]?.id ?? "",
  );

  const selectedTerm = useMemo(
    () =>
      DICTIONARY_TERMS.find((t) => t.id === selectedTermId) ??
      DICTIONARY_TERMS[0],
    [selectedTermId],
  );

  // 필터링 로직
  const filteredTerms = useMemo(() => {
    return DICTIONARY_TERMS.filter((term) => {
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        const haystack = (
          term.term +
          " " +
          (term.termEn ?? "") +
          " " +
          (term.fullDefinition ?? "")
        ).toLowerCase();
        if (!haystack.includes(q)) return false;
      }

      if (selectedHangul && term.korInitial !== selectedHangul) {
        return false;
      }

      if (selectedAlpha && term.alphaInitial !== selectedAlpha) {
        return false;
      }

      return true;
    });
  }, [searchQuery, selectedHangul, selectedAlpha]);

  const resultCount = filteredTerms.length;

  // 돋보기 버튼 클릭 시 동작
  const handleSearchClick = () => {
    const q = searchQuery.trim();
    if (!q) {
      setSearchError("검색어를 입력해주세요.");
      return;
    }

    const found = DICTIONARY_TERMS.find((term) => {
      const koreanMatch =
        term.term === q || term.term.toLowerCase() === q.toLowerCase();
      const englishMatch =
        term.termEn &&
        (term.termEn === q || term.termEn.toLowerCase() === q.toLowerCase());
      return koreanMatch || englishMatch;
    });

    if (found) {
      setSelectedTermId(found.id);
      setSearchError(null);
    } else {
      setSearchError("해당 단어는 사전에 존재하지 않습니다.");
    }
  };

  const searchPlaceholder = searchError ?? "키워드를 입력해주세요";

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            용어사전
          </h1>
        </header>

        {/* 메인 영역 */}
        <main className="w-full flex flex-col lg:flex-row items-start justify-between gap-4 sm:gap-6">
          {/* 좌측: 선택된 용어 설명 카드 (고정 높이) */}
          <section className="w-full lg:flex-[0.9] bg-zinc-100 rounded-2xl px-3.5 sm:px-4 md:px-5 py-4 sm:py-5 flex flex-col gap-2 h-[510px]">
            <h2 className="text-sky-500 text-base sm:text-lg md:text-xl font-medium leading-snug">
              {selectedTerm.term}
              {selectedTerm.termEn ? `(${selectedTerm.termEn})` : ""}
            </h2>
            <div className="mt-1 flex-1 overflow-y-auto">
              <p className="text-black text-[11px] sm:text-xs md:text-sm font-medium leading-relaxed">
                {selectedTerm.fullDefinition}
              </p>
            </div>
          </section>

          {/* 우측: 검색/필터 + 결과 리스트 */}
          <section className="w-full lg:flex-[1.1] flex flex-col gap-3">
            {/* 검색 + 한글/알파벳 필터 카드 */}
            <div className="w-full bg-zinc-100 rounded-2xl px-4 py-3 flex flex-col gap-3">
              {/* 상단: 말머리 + 제목 + 검색창 (한 줄) */}
              <div className="flex items-center gap-3">
                <span className="text-black text-base sm:text-lg md:text-xl">
                  •
                </span>

                <span className="text-black text-sm sm:text-base md:text-lg font-medium whitespace-nowrap">
                  경제용어
                </span>

                {/* 검색창: 한 줄 안에서만, 고정 폭 */}
                <div className="w-full max-w-sm">
                  <div
                    className={`h-9 sm:h-10 px-3 py-1.5 bg-white rounded-[10px] flex items-center justify-between border ${
                      searchError ? "border-red-400" : "border-transparent"
                    }`}
                  >
                    <input
                      className={`flex-1 bg-transparent outline-none text-[11px] sm:text-xs md:text-sm ${
                        searchError
                          ? "text-red-500 placeholder:text-red-400"
                          : "text-black placeholder:text-zinc-500"
                      }`}
                      placeholder={searchPlaceholder}
                      value={searchQuery}
                      onChange={(e) => {
                        setSearchQuery(e.target.value);
                        if (searchError) setSearchError(null);
                      }}
                    />
                    <button
                      type="button"
                      onClick={handleSearchClick}
                      className="ml-2 w-6 h-6 sm:w-7 sm:h-7 bg-zinc-200 rounded-full flex items-center justify-center hover:bg-zinc-300 transition-colors"
                    >
                      <img
                        src="/src/assets/search.png"
                        alt="검색"
                        className="w-3.5 h-3.5 sm:w-4 sm:h-4 object-contain"
                      />
                    </button>
                  </div>
                </div>
              </div>

              {/* 한글순 / 알파벳순 */}
              <div className="mt-1 flex flex-col gap-2.5 pl-6">
                {/* 한글순 */}
                <div className="flex flex-col gap-1.5">
                  <p className="text-black text-xs sm:text-sm md:text-base font-medium">
                    한글순
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {HANGUL_LETTERS.map((ch) => {
                      const isActive = selectedHangul === ch && !selectedAlpha;
                      return (
                        <button
                          key={ch}
                          onClick={() => {
                            setSelectedAlpha(null);
                            setSelectedHangul((prev) =>
                              prev === ch ? null : ch,
                            );
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] outline-stone-300 flex items-center justify-center transition-colors ${
                            isActive ? "bg-zinc-900/10" : "bg-white"
                          }`}
                        >
                          <span className="text-sm sm:text-base font-semibold leading-none text-black">
                            {ch}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 알파벳순 */}
                <div className="flex flex-col gap-1.5">
                  <p className="text-black text-xs sm:text-sm md:text-base font-medium">
                    알파벳순
                  </p>

                  {/* 첫 줄 A~M */}
                  <div className="flex flex-wrap gap-1">
                    {ALPHABET_ROW1.map((ch) => {
                      const isActive = selectedAlpha === ch && !selectedHangul;
                      return (
                        <button
                          key={ch}
                          onClick={() => {
                            setSelectedHangul(null);
                            setSelectedAlpha((prev) =>
                              prev === ch ? null : ch,
                            );
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] outline-stone-300 flex items-center justify-center transition-colors ${
                            isActive ? "bg-zinc-900/10" : "bg-white"
                          }`}
                        >
                          <span className="text-sm sm:text-base font-semibold leading-none text-black">
                            {ch}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  {/* 둘째 줄 N~Z */}
                  <div className="flex flex-wrap gap-1">
                    {ALPHABET_ROW2.map((ch) => {
                      const isActive = selectedAlpha === ch && !selectedHangul;
                      return (
                        <button
                          key={ch}
                          onClick={() => {
                            setSelectedHangul(null);
                            setSelectedAlpha((prev) =>
                              prev === ch ? null : ch,
                            );
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] outline-stone-300 flex items-center justify-center transition-colors ${
                            isActive ? "bg-zinc-900/10" : "bg-white"
                          }`}
                        >
                          <span className="text-sm sm:text-base font-semibold leading-none text-black">
                            {ch}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>

            {/* 검색 결과 요약 라인 */}
            <div className="w-full border-b border-black pb-1">
              <p className="text-zinc-500 text-[11px] sm:text-xs md:text-sm font-medium">
                {selectedHangul
                  ? `‘${selectedHangul}’ 검색 결과 `
                  : "검색 결과 "}
                <span className="text-red-400 font-medium">{resultCount}</span>
                건의 정보가 검색되었습니다.
              </p>
            </div>

            {/* 검색 결과 리스트 카드 */}
            <div className="w-full bg-zinc-100 rounded-2xl px-3 sm:px-4 py-3 flex items-stretch">
              {/* 좌측: 결과 목록 (고정 높이 + 내부 스크롤) */}
              <div className="flex-1 min-w-0 text-[11px] sm:text-xs md:text-sm leading-relaxed h-40 sm:h-48 md:h-52 overflow-y-auto">
                {filteredTerms.map((term) => (
                  <button
                    key={term.id}
                    onClick={() => setSelectedTermId(term.id)}
                    className={`block w-full text-left py-0.5 ${
                      term.id === selectedTermId
                        ? "font-bold underline"
                        : "font-medium"
                    }`}
                  >
                    {term.term}
                    {term.termEn ? `(${term.termEn})` : ""}
                  </button>
                ))}

                {filteredTerms.length === 0 && (
                  <p className="text-[11px] text-gray-500 py-2">
                    검색 결과가 없습니다.
                  </p>
                )}
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
