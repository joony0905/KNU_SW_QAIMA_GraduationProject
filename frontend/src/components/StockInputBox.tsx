import React, { useState, useEffect, useRef, useCallback } from "react";
import { Search } from "lucide-react";
import type { WatchlistItem } from "../types/watchlist";
import type { StockDto } from "../types/stock";
import { searchStocks, getStockByCode } from "../api/stock";
import { fetchWatchlist } from "../api/watchlist";

interface StockInputBoxProps {
  placeholder?: string;
  onSearch?: (value: string) => void;
  /** 좌측 "관심"(워치리스트) 버튼 표시 여부. 메인 랜딩 등에서는 false */
  showInterest?: boolean;
  /** 최근 검색어 저장/표시 사용 여부. 메인 검색창에서는 false */
  enableRecent?: boolean;
}

export default function StockInputBox({
  placeholder = "종목을 입력해주세요",
  onSearch,
  showInterest = true,
  enableRecent = true,
}: StockInputBoxProps) {
  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [stockResults, setStockResults] = useState<StockDto[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showRecentSearches, setShowRecentSearches] = useState(false);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [guideMessage, setGuideMessage] = useState("");
  const [isInterestListOpen, setIsInterestListOpen] = useState(false);
  const [interests, setInterests] = useState<WatchlistItem[]>([]);
  const [interestsLoading, setInterestsLoading] = useState(false);

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
        setShowRecentSearches(false);
        setIsInterestListOpen(false);
        setGuideMessage("");
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // "관심" 목록을 열 때마다 실제 워치리스트를 조회한다(다른 화면에서 추가/삭제된
  // 내용을 반영하기 위해 매번 갱신). 비로그인 시 fetchWatchlist 가 401 →
  // apiClient 인터셉터가 /login 으로 이동시킨다(의도된 동작).
  const loadInterests = useCallback(async () => {
    setInterestsLoading(true);
    try {
      const items = await fetchWatchlist();
      setInterests(items);
    } catch {
      // 일시적 실패 시 직전에 불러온 목록을 그대로 둔다(빈 목록으로 깜빡이지 않게).
      // 최초 조회 실패면 interests 가 이미 [] 라 "관심종목이 없습니다"가 표시됨.
    } finally {
      setInterestsLoading(false);
    }
  }, []);

  const saveRecentSearch = useCallback((companyName: string) => {
    if (!enableRecent) return;
    const stored = localStorage.getItem("recentSearches");
    let recent: string[] = stored ? JSON.parse(stored) : [];
    recent = recent.filter((item) => item !== companyName);
    recent.unshift(companyName);
    recent = recent.slice(0, 5);
    localStorage.setItem("recentSearches", JSON.stringify(recent));
  }, [enableRecent]);

  const handleFocus = () => {
    // 입력창을 누르면 "관심" 리스트는 닫는다(버튼 상태와 화면 표시를 일치시켜
    // 다음 관심 클릭이 어긋나지 않도록).
    setIsInterestListOpen(false);
    if (!enableRecent) return;
    if (inputValue.trim() === "") {
      const stored = localStorage.getItem("recentSearches");
      const recent: string[] = stored ? JSON.parse(stored) : [];
      if (recent.length > 0) {
        setRecentSearches(recent);
        setShowRecentSearches(true);
        setShowSuggestions(false);
      }
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputValue(value);
    setShowRecentSearches(false);
    setGuideMessage("");

    const q = value.trim();
    if (!q) {
      setSuggestions([]);
      setStockResults([]);
      setShowSuggestions(false);
      setIsSearching(false);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      if (enableRecent) {
        const stored = localStorage.getItem("recentSearches");
        const recent: string[] = stored ? JSON.parse(stored) : [];
        if (recent.length > 0) {
          setRecentSearches(recent);
          setShowRecentSearches(true);
        }
      }
      return;
    }

    setIsSearching(true);
    setShowSuggestions(true);

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        if (/^\d{6}$/.test(q)) {
          const stock = await getStockByCode(q);
          setStockResults([stock] as StockDto[]);
          setSuggestions([`${stock.companyName} (${stock.stockCode})`]);
        } else {
          const results = await searchStocks(q);
          const filtered = results.filter((s) => /^\d{6}$/.test(s.stockCode));
          setStockResults(filtered);
          setSuggestions(filtered.map((s) => `${s.companyName} (${s.stockCode})`));
        }
      } catch {
        setStockResults([]);
        setSuggestions([]);
      } finally {
        setIsSearching(false);
      }
    }, 300);
  };

  const handleSelect = (value: string, stockCode?: string) => {
    setShowSuggestions(false);
    setShowRecentSearches(false);
    setIsInterestListOpen(false);
    setGuideMessage("");

    if (stockCode) {
      const matched = stockResults.find((s) => s.stockCode === stockCode);
      const name = matched?.companyName ?? value.replace(/ \(.*\)$/, "");
      setInputValue(name);
      saveRecentSearch(name);
      onSearch?.(stockCode);
    } else {
      onSearch?.(value);
    }
  };

  const handleRecentSelect = async (value: string) => {
    setInputValue(value);
    setShowRecentSearches(false);
    setIsInterestListOpen(false);
    setGuideMessage("");

    try {
      const results = await searchStocks(value);
      const match = results.find(
        (s) => /^\d{6}$/.test(s.stockCode) && s.companyName === value
      );
      if (match) {
        onSearch?.(match.stockCode);
      } else {
        setGuideMessage("유효하지 않은 종목입니다.");
      }
    } catch {
      setGuideMessage("유효하지 않은 종목입니다.");
    }
  };

  const submitSearch = () => {
    const q = inputValue.trim();
    if (!q) return;
    setShowSuggestions(false);
    setShowRecentSearches(false);
    setIsInterestListOpen(false);

    const match = stockResults.find(
      (s) => s.companyName === q || `${s.companyName} (${s.stockCode})` === q
    );
    if (match) {
      saveRecentSearch(match.companyName);
      setGuideMessage("");
      onSearch?.(match.stockCode);
    } else if (/^\d{6}$/.test(q)) {
      setGuideMessage("");
      onSearch?.(q);
    } else {
      setGuideMessage("목록에서 종목을 선택해주세요.");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") submitSearch();
  };

  const showDropdown = showSuggestions || showRecentSearches || isInterestListOpen || !!guideMessage;

  return (
    <div ref={wrapperRef} className={`w-full relative flex items-center gap-2 px-3 py-[9px] bg-surface border border-line shadow-card ${showDropdown ? "rounded-t-2xl border-b-surface" : "rounded-2xl"}`}>
      {/* 관심 버튼 — showInterest=false 면 숨김 (메인 랜딩 등) */}
      {showInterest && (
        <button
          onClick={() => {
            // 상태값(prev)이 아니라 "관심 리스트가 지금 실제로 보이는지"를
            // 기준으로 토글한다. 입력 포커스 등으로 다른 드롭다운이 가려도
            // 버튼 동작이 화면과 어긋나지 않게 한다.
            const interestVisible =
              isInterestListOpen &&
              !showSuggestions &&
              !showRecentSearches &&
              !guideMessage;
            setShowSuggestions(false);
            setShowRecentSearches(false);
            setGuideMessage("");
            if (interestVisible) {
              setIsInterestListOpen(false);
            } else {
              setIsInterestListOpen(true);
              void loadInterests();
            }
          }}
          type="button"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                     bg-bg-sunk text-ink-2 font-semibold text-sm flex-shrink-0"
        >
          관심
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
               fill="currentColor" viewBox="0 0 24 24" className="text-warn">
            <path d="M12 17.75l-6.16 3.73 1.18-6.88L2 9.77l6.92-1L12 2.5l3.08 6.27 6.92 1-5.02 4.83 1.18 6.88z" />
          </svg>
        </button>
      )}

      {/* 입력 필드 */}
      <input
        type="text"
        value={inputValue}
        onChange={handleChange}
        onFocus={handleFocus}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        className="flex-1 py-1 text-base font-medium text-ink bg-transparent
                   placeholder:text-ink-4 focus:outline-none"
      />

      {/* 검색 버튼 — accent 색 */}
      <button
        type="button"
        onClick={submitSearch}
        className="w-9 h-9 grid place-items-center rounded-xl bg-accent text-white
                   hover:opacity-90 transition-opacity flex-shrink-0"
      >
        <Search size={16} strokeWidth={2.5} />
      </button>

      {/* 드롭다운 */}
      {showDropdown && (
        <div className="absolute top-full left-0 w-full z-50">
          {showSuggestions ? (
            <ul className="max-h-40 overflow-y-auto bg-surface border border-line border-t-0 rounded-b-2xl shadow-pop">
              {isSearching ? (
                <li className="px-3 py-2 text-sm text-ink-3">검색 중...</li>
              ) : suggestions.length > 0 ? (
                suggestions.map((s, idx) => (
                  <li
                    key={idx}
                    onClick={() => handleSelect(s, stockResults[idx]?.stockCode)}
                    className="px-3 py-2 cursor-pointer hover:bg-bg-sunk text-sm text-ink last:rounded-b-2xl"
                  >
                    {s}
                  </li>
                ))
              ) : (
                <li className="px-3 py-2 text-sm text-ink-3 rounded-b-2xl">검색 결과 없음</li>
              )}
            </ul>
          ) : showRecentSearches ? (
            <div className="bg-surface border border-line border-t-0 rounded-b-2xl shadow-pop">
              <div className="px-3 py-2 text-xs text-ink-3 font-medium border-b border-line">
                최근 검색어
              </div>
              <ul className="max-h-40 overflow-y-auto">
                {recentSearches.map((item, idx) => (
                  <li
                    key={idx}
                    onClick={() => handleRecentSelect(item)}
                    className="px-3 py-2 cursor-pointer hover:bg-bg-sunk text-sm text-ink last:rounded-b-2xl"
                  >
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          ) : isInterestListOpen && (
            <div className="bg-surface border border-line border-t-0 rounded-b-2xl shadow-pop">
              {interestsLoading && interests.length === 0 ? (
                // 최초 조회 중에는 아무것도 그리지 않는다(로딩/"없습니다" 깜빡임 방지).
                // 데이터가 도착하면 바로 아래 분기로 리스트가 그려진다.
                null
              ) : interests.length === 0 ? (
                <p className="px-3 py-3 text-sm text-ink-3 rounded-b-2xl">
                  관심종목이 없습니다.
                </p>
              ) : (
                <ul className="max-h-40 overflow-y-auto">
                  {interests.map((item) => (
                    <li
                      key={item.watchlistItemId}
                      onClick={() => {
                        if (item.stockName) handleRecentSelect(item.stockName);
                        else setIsInterestListOpen(false);
                      }}
                      className="px-3 py-2 cursor-pointer hover:bg-bg-sunk text-sm text-ink last:rounded-b-2xl"
                    >
                      {item.stockName ?? "(이름 없음)"}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}

      {guideMessage && (
        <p className="absolute top-full left-0 w-full px-3 py-2 text-sm text-danger
                      bg-surface border border-line border-t-0 rounded-b-2xl shadow-pop z-50">
          {guideMessage}
        </p>
      )}
    </div>
  );
}
