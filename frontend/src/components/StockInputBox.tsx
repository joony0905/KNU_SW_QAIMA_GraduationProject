import React, { useState, useEffect, useRef } from "react";
import api from "../lib/apiClient";
import searchIcon from "../assets/search.png";

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

  // 관심 리스트 토글 상태
  const [isInterestListOpen, setIsInterestListOpen] = useState(false);
  // 더미 관심 종목 목록
  const dummyInterests = ["삼성전자", "LG에너지솔루션", "카카오"];

  const wrapperRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(e.target as Node)
      ) {
        setShowSuggestions(false);
        // 필요하면 관심 리스트도 같이 닫기
        // setIsInterestListOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputValue(value);

    if (value.trim()) {
      try {
        const res = await api.get(
          `/api/v1/stocks/search?q=${encodeURIComponent(value)}`
        );
        setSuggestions(res?.data || []);
        setShowSuggestions(true);
      } catch (err) {
        console.error("자동완성 API 실패:", err);
        setSuggestions([]);
        setShowSuggestions(false);
      }
    } else {
      setShowSuggestions(false);
    }
  };

  const handleSelect = (value: string) => {
    setInputValue(value);
    setShowSuggestions(false);
    if (onSearch) onSearch(value);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && onSearch) {
      onSearch(inputValue);
      setShowSuggestions(false);
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
            onClick={() =>
              setIsInterestListOpen((prev: boolean) => !prev)
            }
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
              fill="currentColor"      // 항상 찬 별
              stroke="currentColor"
              strokeWidth="2"
              viewBox="0 0 24 24"
              className="text-yellow-400" // 노란색 별
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
            onClick={() => onSearch && onSearch(inputValue)}
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

      {/* 자동완성 / 관심 리스트 공통 레이어 */}
      {(showSuggestions && suggestions.length > 0) || isInterestListOpen ? (
        <div className="absolute top-full left-0 mt-1 w-full z-50">
          {/* 자동완성 우선 */}
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
            isInterestListOpen && (
              <div className="bg-white border border-stone-300 rounded-md shadow-md">
                <ul className="max-h-40 overflow-y-auto">
                  {dummyInterests.map((item) => (
                    <li
                      key={item}
                      onClick={() => {
                        setInputValue(item);
                        if (onSearch) onSearch(item);
                        setIsInterestListOpen(false);
                      }}
                      className="px-3 py-1.5 cursor-pointer hover:bg-zinc-100 text-sm sm:text-base md:text-lg font-['Inter']"
                    >
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )
          )}
        </div>
      ) : null}

      {/* TODO: 실제 관심 종목 API 예시 */}
      {/*
      useEffect(() => {
        const fetchInterests = async () => {
          try {
            const res = await api.get("/api/v1/interests");
            // 예: setInterests(res.data);
          } catch (e) {
            console.error("관심 종목 불러오기 실패:", e);
          }
        };
        fetchInterests();
      }, []);
      */}
    </div>
  );
}
