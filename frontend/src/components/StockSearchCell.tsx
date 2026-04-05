import { useState, useRef, useEffect, useCallback } from "react";
import { searchStocks, getStockByCode } from "../api/stock";
import type { StockDto } from "../types/stock";

interface StockSearchCellProps {
  value: string;
  onSelect: (name: string, stockCode?: string) => void;
  placeholder?: string;
}

export default function StockSearchCell({
  value,
  onSelect,
  placeholder = "종목명 검색",
}: StockSearchCellProps) {
  const [inputValue, setInputValue] = useState(value);
  const [suggestions, setSuggestions] = useState<StockDto[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [isFallback, setIsFallback] = useState(false);

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 외부 value가 바뀌면 동기화
  useEffect(() => {
    setInputValue(value);
  }, [value]);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const doSearch = useCallback(async (q: string) => {
    setIsSearching(true);
    setIsFallback(false);
    try {
      let results: StockDto[];
      if (/^\d{6}$/.test(q)) {
        const stock = await getStockByCode(q);
        results = [stock];
      } else {
        const all = await searchStocks(q);
        results = all.filter((s) => /^\d{6}$/.test(s.stockCode));
      }
      setSuggestions(results);
      setShowDropdown(true);
      if (results.length === 0) {
        setIsFallback(true);
      }
    } catch {
      setSuggestions([]);
      setIsFallback(true);
      setShowDropdown(true);
    } finally {
      setIsSearching(false);
    }
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    setInputValue(v);
    setIsFallback(false);

    const q = v.trim();
    if (!q) {
      setSuggestions([]);
      setShowDropdown(false);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      onSelect("");
      return;
    }

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(q), 300);
  };

  const handleSelectItem = (stock: StockDto) => {
    setInputValue(stock.companyName);
    setShowDropdown(false);
    setSuggestions([]);
    setIsFallback(false);
    onSelect(stock.companyName, stock.stockCode);
  };

  const handleFallbackConfirm = () => {
    setShowDropdown(false);
    onSelect(inputValue.trim());
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      // 검색 결과 중 정확히 매칭되는 게 있으면 선택
      const match = suggestions.find((s) => s.companyName === inputValue.trim());
      if (match) {
        handleSelectItem(match);
      } else {
        // fallback: 수동 입력 확정
        setShowDropdown(false);
        onSelect(inputValue.trim());
      }
    }
    if (e.key === "Escape") {
      setShowDropdown(false);
    }
  };

  return (
    <div ref={wrapperRef} className="relative w-full">
      <input
        className="w-full text-center text-sm text-gray-700 font-medium bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none focus:bg-[#3F51B5]/5 transition-colors"
        value={inputValue}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
      />

      {showDropdown && (
        <div className="absolute top-full left-1/2 -translate-x-1/2 mt-1 w-56 z-50 bg-white border border-gray-200 rounded-xl shadow-lg overflow-hidden">
          {isSearching ? (
            <div className="px-3 py-2 text-sm text-gray-400">검색 중...</div>
          ) : suggestions.length > 0 ? (
            <ul className="max-h-40 overflow-y-auto">
              {suggestions.map((stock) => (
                <li
                  key={stock.stockCode}
                  onClick={() => handleSelectItem(stock)}
                  className="px-3 py-2 text-sm text-gray-700 cursor-pointer hover:bg-[#3F51B5]/5 transition-colors"
                >
                  <span className="font-medium">{stock.companyName}</span>
                  <span className="ml-1.5 text-xs text-gray-400">
                    {stock.stockCode}
                  </span>
                </li>
              ))}
            </ul>
          ) : isFallback ? (
            <div className="px-3 py-2">
              <p className="text-xs text-gray-400 mb-1.5">
                검색 결과가 없습니다
              </p>
              <button
                type="button"
                onClick={handleFallbackConfirm}
                className="w-full text-sm text-[#3F51B5] font-medium py-1.5 rounded-lg hover:bg-[#3F51B5]/5 transition-colors"
              >
                "{inputValue}" 직접 입력
              </button>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
}
