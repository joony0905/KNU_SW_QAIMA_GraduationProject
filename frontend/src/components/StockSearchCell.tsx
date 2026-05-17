import { useState, useRef, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { searchStocks, getStockByCode } from "../api/stock";
import type { StockDto } from "../types/stock";

interface StockSearchCellProps {
  value: string;
  onSelect: (name: string, stockCode?: string) => void;
  placeholder?: string;
}

const DROPDOWN_WIDTH = 224; // w-56

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
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);

  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setInputValue(value);
  }, [value]);

  // 입력창 위치를 화면 좌표로 계산 (overflow 컨테이너에 가리지 않게 portal + fixed)
  const updateCoords = useCallback(() => {
    const el = wrapperRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setCoords({
      top: rect.bottom + 4,
      left: rect.left + rect.width / 2,
    });
  }, []);

  useEffect(() => {
    if (!showDropdown) return;
    updateCoords();
    // 내부 스크롤 컨테이너 스크롤까지 잡으려면 capture=true
    window.addEventListener("scroll", updateCoords, true);
    window.addEventListener("resize", updateCoords);
    return () => {
      window.removeEventListener("scroll", updateCoords, true);
      window.removeEventListener("resize", updateCoords);
    };
  }, [showDropdown, updateCoords]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      const inWrapper = wrapperRef.current?.contains(target);
      const inDropdown = dropdownRef.current?.contains(target);
      if (!inWrapper && !inDropdown) {
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
      const match = suggestions.find((s) => s.companyName === inputValue.trim());
      if (match) {
        handleSelectItem(match);
      } else {
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
        className="w-full text-center text-sm text-ink font-medium bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none focus:bg-accent/5 transition-colors"
        value={inputValue}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
      />

      {showDropdown && coords &&
        createPortal(
          <div
            ref={dropdownRef}
            style={{
              position: "fixed",
              top: coords.top,
              left: coords.left,
              width: DROPDOWN_WIDTH,
              transform: "translateX(-50%)",
            }}
            className="z-[200] bg-surface border border-line rounded-xl shadow-pop overflow-hidden"
          >
            {isSearching ? (
              <div className="px-3 py-2 text-sm text-ink-4">검색 중...</div>
            ) : suggestions.length > 0 ? (
              <ul className="max-h-40 overflow-y-auto">
                {suggestions.map((stock) => (
                  <li
                    key={stock.stockCode}
                    onClick={() => handleSelectItem(stock)}
                    className="px-3 py-2 text-sm text-ink cursor-pointer hover:bg-accent/5 transition-colors"
                  >
                    <span className="font-medium">{stock.companyName}</span>
                    <span className="ml-1.5 text-xs text-ink-4">
                      {stock.stockCode}
                    </span>
                  </li>
                ))}
              </ul>
            ) : isFallback ? (
              <div className="px-3 py-2">
                <p className="text-xs text-ink-4 mb-1.5">
                  검색 결과가 없습니다
                </p>
                <button
                  type="button"
                  onClick={handleFallbackConfirm}
                  className="w-full text-sm text-accent font-medium py-1.5 rounded-lg hover:bg-accent/5 transition-colors"
                >
                  "{inputValue}" 직접 입력
                </button>
              </div>
            ) : null}
          </div>,
          document.body,
        )}
    </div>
  );
}
