import React, { useState, useEffect, useRef, useCallback } from "react";
import searchIcon from "../assets/search.png";
import type { WatchlistItem } from "../types/watchlist";
import type { StockDto } from "../types/stock";
import { searchStocks, getStockByCode } from "../api/stock";

interface StockInputBoxProps {
  placeholder?: string;
  onSearch?: (value: string) => void;
}

export default function StockInputBox({
  placeholder = "종목을 입력해주세요",
  onSearch,
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

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
        setShowRecentSearches(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    // TODO: 나중에 실제 watchlist API로 교체
    const dummy: WatchlistItem[] = [
      {
        watchlistItemId: 1,
        stockId: 1,
        stockName: "삼성전자",
        note: null,
        industryName: "전자",
      },
      {
        watchlistItemId: 2,
        stockId: 2,
        stockName: "LG에너지솔루션",
        note: null,
        industryName: "2차전지",
      },
      {
        watchlistItemId: 3,
        stockId: 3,
        stockName: "카카오",
        note: null,
        industryName: "인터넷",
      },
    ];
    setInterests(dummy);
  }, []);

  const saveRecentSearch = useCallback((companyName: string) => {
    const stored = localStorage.getItem("recentSearches");
    let recent: string[] = stored ? JSON.parse(stored) : [];
    recent = recent.filter((item) => item !== companyName);
    recent.unshift(companyName);
    recent = recent.slice(0, 5);
    localStorage.setItem("recentSearches", JSON.stringify(recent));
  }, []);

  const handleFocus = () => {
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
      // 입력 비우면 최근 검색어 표시
      const stored = localStorage.getItem("recentSearches");
      const recent: string[] = stored ? JSON.parse(stored) : [];
      if (recent.length > 0) {
        setRecentSearches(recent);
        setShowRecentSearches(true);
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
          const asResult = [stock] as StockDto[];
          setStockResults(asResult);
          setSuggestions([`${stock.companyName} (${stock.stockCode})`]);
        } else {
          const results = await searchStocks(q);
          const filtered = results.filter((s) => /^\d{6}$/.test(s.stockCode));
          setStockResults(filtered);
          const labels = filtered.map((s) => `${s.companyName} (${s.stockCode})`);
          setSuggestions(labels);
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
      const matched = stockResults.find(
        (s) => s.stockCode === stockCode
      );
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

    // 현재 stockResults에서 매칭되는 항목이 있으면 stockCode 전달 + 최근 검색어 저장
    const match = stockResults.find(
      (s) =>
        s.companyName === q ||
        `${s.companyName} (${s.stockCode})` === q
    );
    if (match) {
      const matchName = match.companyName;
      const matchCode = match.stockCode;
      saveRecentSearch(matchName);
      setGuideMessage("");
      onSearch?.(matchCode);
    } else if (/^\d{6}$/.test(q)) {
      setGuideMessage("");
      onSearch?.(q);
    } else {
      setGuideMessage("목록에서 종목을 선택해주세요.");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      submitSearch();
    }
  };

  const showDropdown =
    showSuggestions || showRecentSearches || isInterestListOpen;

  return (
    <div
      ref={wrapperRef}
      className="
        w-full max-w-[702px]
        px-2 sm:px-3
        py-1 sm:py-1.5
        bg-white rounded-[8px]
        flex flex-col gap-1
        relative
      "
    >
      <div className="flex flex-col sm:flex-row justify-start items-center gap-2 sm:gap-3 w-full">
        {/* 관심 버튼 */}
        <div className="w-full sm:w-28 flex justify-start items-center">
          <button
            onClick={() => {
              setIsInterestListOpen((prev) => !prev);
              setShowSuggestions(false);
              setShowRecentSearches(false);
            }}
            type="button"
            className="
              inline-flex items-center gap-2 px-3 py-1.5 rounded-full
              bg-gray-200 text-gray-800
            "
          >
            <span className="font-medium text-sm sm:text-base">관심</span>
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="20"
              height="20"
              fill="currentColor"
              stroke="currentColor"
              strokeWidth="2"
              viewBox="0 0 24 24"
              className="text-yellow-400"
            >
              <path d="M12 17.75l-6.16 3.73 1.18-6.88L2 9.77l6.92-1L12 2.5l3.08 6.27 6.92 1-5.02 4.83 1.18 6.88z" />
            </svg>
          </button>
        </div>

        {/* 입력 필드 + 검색 아이콘 */}
        <div className="flex-1 flex items-center gap-1.5 w-full">
          <input
            type="text"
            value={inputValue}
            onChange={handleChange}
            onFocus={handleFocus}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            className="
              flex-1
              px-2 sm:px-2.5
              py-0.5 sm:py-1
              text-xs sm:text-sm md:text-base
              font-medium font-['Inter']
              text-black
              focus:outline-none focus:ring-1 focus:ring-blue-400
            "
          />
          <button
            type="button"
            onClick={submitSearch}
            className="
              shrink-0
              w-8 h-8 sm:w-9 sm:h-9
              flex items-center justify-center
              bg-white rounded-md
              hover:bg-zinc-100
              transition-colors
            "
          >
            <img
              src={searchIcon}
              alt="검색 아이콘"
              className="block w-7 h-7 sm:w-7 sm:h-7"
            />
          </button>
        </div>
      </div>

      {/* 자동완성 / 최근 검색어 / 관심 리스트 */}
      {showDropdown ? (
        <div className="absolute top-full left-0 mt-1 w-full z-50">
          {/* 실시간 자동완성 */}
          {showSuggestions ? (
            <ul className="max-h-40 overflow-y-auto bg-white border border-stone-300 rounded-md shadow-md">
              {isSearching ? (
                <li className="px-3 py-1.5 text-sm sm:text-base text-gray-500 font-['Inter']">
                  검색 중...
                </li>
              ) : suggestions.length > 0 ? (
                suggestions.map((s, idx) => (
                  <li
                    key={idx}
                    onClick={() =>
                      handleSelect(s, stockResults[idx]?.stockCode)
                    }
                    className="px-3 py-1.5 cursor-pointer hover:bg-zinc-100 text-sm sm:text-base md:text-lg font-['Inter']"
                  >
                    {s}
                  </li>
                ))
              ) : (
                <li className="px-3 py-1.5 text-sm sm:text-base text-gray-500 font-['Inter']">
                  검색 결과 없음
                </li>
              )}
            </ul>
          ) : showRecentSearches ? (
            /* 최근 검색어 */
            <div className="bg-white border border-stone-300 rounded-md shadow-md">
              <div className="px-3 py-1.5 text-xs text-gray-500 font-medium border-b border-stone-200">
                최근 검색어
              </div>
              <ul className="max-h-40 overflow-y-auto">
                {recentSearches.map((item, idx) => (
                  <li
                    key={idx}
                    onClick={() => handleRecentSelect(item)}
                    className="px-3 py-1.5 cursor-pointer hover:bg-zinc-100 text-sm sm:text-base md:text-lg font-['Inter']"
                  >
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            // 관심 리스트
            isInterestListOpen && (
              <div className="bg-white border border-stone-300 rounded-md shadow-md">
                <ul className="max-h-40 overflow-y-auto">
                  {interests.map((item) => (
                    <li
                      key={item.watchlistItemId}
                      onClick={() => {
                        if (item.stockName) {
                          handleSelect(item.stockName);
                        } else {
                          setIsInterestListOpen(false);
                        }
                      }}
                      className="px-3 py-1.5 cursor-pointer hover:bg-zinc-100 text-sm sm:text-base md:text-lg font-['Inter']"
                    >
                      {item.stockName ?? "(이름 없음)"}
                    </li>
                  ))}
                </ul>
              </div>
            )
          )}
        </div>
      ) : null}

      {guideMessage && (
        <p className="absolute top-full left-0 mt-1 w-full px-3 py-1.5 text-sm text-red-500 bg-white border border-stone-300 rounded-md shadow-md z-50">
          {guideMessage}
        </p>
      )}
    </div>
  );
}
