import React, { useState, useEffect, useRef } from "react";
import searchIcon from "../assets/search.png";
import type { WatchlistItem } from "../types/watchlist";

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

  const [isInterestListOpen, setIsInterestListOpen] = useState(false);
  const [interests, setInterests] = useState<WatchlistItem[]>([]);

  const wrapperRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
        // setIsInterestListOpen(false);
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

  /**
   * 자동완성 API 비활성화 버전
   * - 타이핑 중에는 API 호출하지 않음
   * - 필요하면 관심 목록에서만 "로컬 자동완성" 제공
   */
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputValue(value);

    const q = value.trim();
    if (!q) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    // (선택) 로컬 자동완성: 관심 목록에서만 필터
    const local = interests
      .map((x) => x.stockName)
      .filter((name): name is string => !!name)
      .filter((name) => name.includes(q))
      .slice(0, 10);

    setSuggestions(local);
    setShowSuggestions(local.length > 0);
  };

  const handleSelect = (value: string) => {
    setInputValue(value);
    setShowSuggestions(false);
    setIsInterestListOpen(false);
    onSearch?.(value);
  };

  const submitSearch = () => {
    const q = inputValue.trim();
    if (!q) return;
    setShowSuggestions(false);
    setIsInterestListOpen(false);
    onSearch?.(q);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      submitSearch();
    }
  };

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

      {/* 자동완성 / 관심 리스트 */}
      {(showSuggestions && suggestions.length > 0) || isInterestListOpen ? (
        <div className="absolute top-full left-0 mt-1 w-full z-50">
          {/* 자동완성 (로컬) */}
          {showSuggestions && suggestions.length > 0 ? (
            <ul className="max-h-40 overflow-y-auto bg-white border border-stone-300 rounded-md shadow-md">
              {suggestions.map((s, idx) => (
                <li
                  key={idx}
                  onClick={() => handleSelect(s)}
                  className="px-3 py-1.5 cursor-pointer hover:bg-zinc-100 text-sm sm:text-base md:text-lg font-['Inter']"
                >
                  {s}
                </li>
              ))}
            </ul>
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
    </div>
  );
}
