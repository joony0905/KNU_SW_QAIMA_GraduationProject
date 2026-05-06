// src/pages/DictionaryMockPage.tsx
import { useState, useMemo, useEffect } from "react";
import { Sun, Moon } from "lucide-react";
import type { DictionaryTermDto } from "../types/dictionary";
import { fetchDictionaryTerms, fetchDictionaryTerm } from "../api/dictionary";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { useTheme } from "../hooks/useTheme";

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

// ---- 컴포넌트 ----

export default function DictionaryMockPage() {
  const { theme, toggle } = useTheme();
  // 검색/필터 상태
  const [searchQuery, setSearchQuery] = useState("");
  const [searchError, setSearchError] = useState<string | null>(null);
  const [selectedHangul, setSelectedHangul] = useState<string | null>(null);
  const [selectedAlpha, setSelectedAlpha] = useState<string | null>(null);

  // API 상태
  const [terms, setTerms] = useState<DictionaryTermDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [selectedTerm, setSelectedTerm] = useState<DictionaryTermDto | null>(null);

  // 초기 로딩
  useEffect(() => {
    setLoading(true);
    fetchDictionaryTerms({})
      .then((data) => {
        setTerms(data);
        if (data.length > 0) setSelectedTerm(data[0]);
      })
      .catch(() => setFetchError("데이터를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  // 필터링 로직
  const filteredTerms = useMemo(() => {
    return terms.filter((term) => {
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        const haystack = (term.term + " " + (term.description ?? "")).toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [terms, searchQuery]);

  const resultCount = filteredTerms.length;

  // 돋보기 버튼 클릭 시 동작
  const handleSearchClick = () => {
    const q = searchQuery.trim();
    if (!q) {
      setSearchError("검색어를 입력해주세요.");
      return;
    }
    setLoading(true);
    setSearchError(null);
    fetchDictionaryTerms({ q })
      .then((data) => {
        setTerms(data);
        if (data.length > 0) {
          setSelectedTerm(data[0]);
        } else {
          setSearchError("해당 단어는 사전에 존재하지 않습니다.");
        }
      })
      .catch(() => setSearchError("검색 중 오류가 발생했습니다."))
      .finally(() => setLoading(false));
  };

  const searchPlaceholder = searchError ?? "키워드를 입력해주세요";

  return (
    <div className="min-h-screen bg-bg ml-[84px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="flex items-center justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Finance · Dictionary
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              용어사전
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? "라이트 모드" : "다크 모드"}
              className="w-9 h-9 grid place-items-center rounded-xl bg-surface
                         border border-line text-ink-2 shadow-card
                         hover:bg-bg-sunk transition-colors"
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <TokenBalanceBadge />
          </div>
        </header>

        {/* 메인 영역 */}
        <main className="w-full flex flex-col lg:flex-row items-start justify-between gap-4 sm:gap-6">
          {/* 좌측: 선택된 용어 설명 카드 (고정 높이) */}
          <section className="w-full lg:flex-[0.9] rounded-2xl px-3.5 sm:px-4 md:px-5 py-4 sm:py-5 flex flex-col gap-2 h-[510px] bg-bg-sunk">
            {selectedTerm ? (
              <>
                <h2 className="text-base sm:text-lg md:text-xl font-semibold leading-snug text-accent tracking-tight">
                  {selectedTerm.term}
                </h2>
                <div className="mt-1 flex-1 overflow-y-auto">
                  <p className="text-[11px] sm:text-xs md:text-sm leading-relaxed text-ink-2 font-normal">
                    {selectedTerm.description}
                  </p>
                </div>
              </>
            ) : (
              <p className="text-sm text-ink-3">용어를 선택해주세요.</p>
            )}
          </section>

          {/* 우측: 검색/필터 + 결과 리스트 */}
          <section className="w-full lg:flex-[1.1] flex flex-col gap-3">
            {/* 검색 + 한글/알파벳 필터 카드 */}
            <div className="w-full rounded-2xl px-4 py-3 flex flex-col gap-3 bg-bg-sunk">
              {/* 상단: 말머리 + 제목 + 검색창 (한 줄) */}
              <div className="flex items-center gap-3">
                <span className="text-base sm:text-lg md:text-xl text-ink">
                  •
                </span>

                <span className="text-sm sm:text-base md:text-lg font-semibold whitespace-nowrap text-ink tracking-tight">
                  경제용어
                </span>

                {/* 검색창: 한 줄 안에서만, 고정 폭 */}
                <div className="w-full max-w-sm">
                  <div
                    className={`h-9 sm:h-10 px-3 py-1.5 rounded-[10px] flex items-center justify-between border bg-surface ${
                      searchError ? "border-danger" : "border-line"
                    }`}
                  >
                    <input
                      className={`flex-1 bg-transparent outline-none text-[11px] sm:text-xs md:text-sm ${
                        searchError
                          ? "text-danger placeholder:text-danger/70"
                          : "text-ink placeholder:text-ink-3"
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
                      className="ml-2 w-6 h-6 sm:w-7 sm:h-7 rounded-full flex items-center justify-center transition-colors bg-bg-sunk hover:bg-surface-2"
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
                  <p className="text-xs sm:text-sm md:text-base font-medium text-ink-2">
                    한글순
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {HANGUL_LETTERS.map((ch) => {
                      const isActive = selectedHangul === ch && !selectedAlpha;
                      return (
                        <button
                          key={ch}
                          onClick={() => {
                            const next = selectedHangul === ch ? null : ch;
                            setSelectedAlpha(null);
                            setSelectedHangul(next);
                            setLoading(true);
                            fetchDictionaryTerms({ initial: next ?? undefined })
                              .then((data) => setTerms(data))
                              .catch(() => setFetchError("데이터를 불러오지 못했습니다."))
                              .finally(() => setLoading(false));
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] flex items-center justify-center transition-colors outline-line ${
                            isActive ? "bg-accent-soft" : "bg-surface"
                          }`}
                        >
                          <span className={`text-sm sm:text-base font-semibold leading-none ${
                            isActive ? "text-accent" : "text-ink"
                          }`}>
                            {ch}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 알파벳순 */}
                <div className="flex flex-col gap-1.5">
                  <p className="text-xs sm:text-sm md:text-base font-medium text-ink-2">
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
                            const next = selectedAlpha === ch ? null : ch;
                            setSelectedHangul(null);
                            setSelectedAlpha(next);
                            setLoading(true);
                            fetchDictionaryTerms({ initial: next ?? undefined })
                              .then((data) => setTerms(data))
                              .catch(() => setFetchError("데이터를 불러오지 못했습니다."))
                              .finally(() => setLoading(false));
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] flex items-center justify-center transition-colors outline-line ${
                            isActive ? "bg-accent-soft" : "bg-surface"
                          }`}
                        >
                          <span className={`text-sm sm:text-base font-semibold leading-none ${
                            isActive ? "text-accent" : "text-ink"
                          }`}>
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
                            const next = selectedAlpha === ch ? null : ch;
                            setSelectedHangul(null);
                            setSelectedAlpha(next);
                            setLoading(true);
                            fetchDictionaryTerms({ initial: next ?? undefined })
                              .then((data) => setTerms(data))
                              .catch(() => setFetchError("데이터를 불러오지 못했습니다."))
                              .finally(() => setLoading(false));
                          }}
                          className={`w-6 h-6 rounded-md outline outline-[0.5px] flex items-center justify-center transition-colors outline-line ${
                            isActive ? "bg-accent-soft" : "bg-surface"
                          }`}
                        >
                          <span className={`text-sm sm:text-base font-semibold leading-none ${
                            isActive ? "text-accent" : "text-ink"
                          }`}>
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
            <div className="w-full border-b pb-1 border-line">
              <p className="text-[11px] sm:text-xs md:text-sm font-medium text-ink-3">
                {selectedHangul
                  ? `'${selectedHangul}' 검색 결과 `
                  : "검색 결과 "}
                <span className="font-semibold text-accent">{resultCount}</span>
                건의 정보가 검색되었습니다.
              </p>
            </div>

            {/* 검색 결과 리스트 카드 */}
            <div className="w-full rounded-2xl px-3 sm:px-4 py-3 flex items-stretch bg-bg-sunk">
              {/* 좌측: 결과 목록 (고정 높이 + 내부 스크롤) */}
              <div className="flex-1 min-w-0 text-[11px] sm:text-xs md:text-sm leading-relaxed h-40 sm:h-48 md:h-52 overflow-y-auto">
                {loading ? (
                  <p className="text-[11px] py-2 text-ink-3">불러오는 중...</p>
                ) : fetchError ? (
                  <p className="text-[11px] py-2 text-danger">{fetchError}</p>
                ) : (
                  <>
                    {filteredTerms.map((term) => (
                      <button
                        key={term.term}
                        onClick={() => {
                          fetchDictionaryTerm(term.term)
                            .then((data) => setSelectedTerm(data))
                            .catch(() => setFetchError("상세 정보를 불러오지 못했습니다."));
                        }}
                        className={`block w-full text-left py-0.5 transition-colors ${
                          term.term === selectedTerm?.term
                            ? "font-bold text-accent underline"
                            : "font-medium text-ink hover:text-accent"
                        }`}
                      >
                        {term.term}
                      </button>
                    ))}

                    {filteredTerms.length === 0 && (
                      <p className="text-[11px] py-2 text-ink-3">
                        검색 결과가 없습니다.
                      </p>
                    )}
                  </>
                )}
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
