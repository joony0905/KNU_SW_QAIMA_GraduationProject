import { useState, useRef, useEffect } from "react";
import { ChevronDown, ChevronUp, Plus, Minus } from "lucide-react";
import { useTranslation } from "react-i18next";
import StockInputBox from "./StockInputBox";
import StockCard from "./StockCard";
import { fetchFeaturedStocks } from "../api/featuredStock";
import type { FeaturedStockDto, FeaturedStockTopic } from "../types/featuredStock";
import { clientLog } from "../utils/clientLog";

interface StockItem {
  name: string;
  symbol: string;
  exchange: string;
  price: string;
  volume: string;
  change: string;
  changeRate: string;
}

const TOPIC_CODES: FeaturedStockTopic[] = [
  "GAINERS", "LOSERS", "NEAR_NEW_HIGH", "NEAR_NEW_LOW", "TOP_TURNOVER", "VOLUME_SURGE",
];

// 특징주 조회 실패 시 노출되는 임시 데이터
const FALLBACK_STOCKS: StockItem[] = [
  { name: "삼성전자",        symbol: "005930", exchange: "KOSPI", price: "72,800",  volume: "14,293,826", change: "+800",    changeRate: "+1.11%" },
  { name: "SK하이닉스",      symbol: "000660", exchange: "KOSPI", price: "189,500", volume: "8,124,310",  change: "+3,500",  changeRate: "+1.88%" },
  { name: "NAVER",           symbol: "035420", exchange: "KOSPI", price: "182,000", volume: "3,045,720",  change: "-2,500",  changeRate: "-1.36%" },
  { name: "LG에너지솔루션",   symbol: "373220", exchange: "KOSPI", price: "312,000", volume: "1,204,850",  change: "+5,000",  changeRate: "+1.63%" },
  { name: "삼성바이오로직스", symbol: "207940", exchange: "KOSPI", price: "856,000", volume: "421,300",    change: "-12,000", changeRate: "-1.38%" },
  { name: "현대차",          symbol: "005380", exchange: "KOSPI", price: "218,500", volume: "2,381,600",  change: "+2,000",  changeRate: "+0.92%" },
  { name: "셀트리온",        symbol: "068270", exchange: "KOSPI", price: "178,300", volume: "1,893,420",  change: "-800",    changeRate: "-0.45%" },
  { name: "카카오",          symbol: "035720", exchange: "KOSPI", price: "43,250",  volume: "5,621,780",  change: "+550",    changeRate: "+1.29%" },
  { name: "POSCO홀딩스",     symbol: "005490", exchange: "KOSPI", price: "287,000", volume: "934,210",    change: "-4,500",  changeRate: "-1.54%" },
  { name: "기아",            symbol: "000270", exchange: "KOSPI", price: "96,400",  volume: "3,102,540",  change: "+800",    changeRate: "+0.84%" },
  { name: "LG화학",          symbol: "051910", exchange: "KOSPI", price: "312,500", volume: "748,630",    change: "+2,500",  changeRate: "+0.81%" },
];

const formatPrice = (n: number) => Math.round(n).toLocaleString("ko-KR");
const formatVolume = (n: number) => Math.round(n).toLocaleString("ko-KR");
const formatSignedNumber = (n: number) => {
  if (!Number.isFinite(n) || n === 0) return "0";
  const sign = n > 0 ? "+" : "";
  return `${sign}${Math.round(n).toLocaleString("ko-KR")}`;
};
const formatSignedPercent = (n: number) => {
  if (!Number.isFinite(n) || n === 0) return "0.00%";
  const sign = n > 0 ? "+" : "";
  return `${sign}${n.toFixed(2)}%`;
};

const dtoToStockItem = (dto: FeaturedStockDto): StockItem => ({
  name: dto.companyName,
  symbol: dto.stockCode,
  exchange: dto.exchangeCode || "KOSPI",
  price: formatPrice(dto.price),
  volume: formatVolume(dto.volume),
  change: formatSignedNumber(dto.change),
  changeRate: formatSignedPercent(dto.changeRate),
});

const getColorClass = (rate: string) => {
  if (rate.startsWith("+")) return "text-rise";
  if (rate.startsWith("-")) return "text-fall";
  return "text-flat";
};

interface StockSearchBarProps {
  onSearch: (value: string) => void;
  placeholder?: string;
}

export default function StockSearchBar({
  onSearch,
  placeholder,
}: StockSearchBarProps) {
  const { t } = useTranslation("stockSearch");
  const [topic, setTopic] = useState<FeaturedStockTopic>("GAINERS");
  const [isTopicOpen, setIsTopicOpen] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  const [stocks, setStocks] = useState<StockItem[]>(FALLBACK_STOCKS);
  const [loading, setLoading] = useState(false);
  const [hasError, setHasError] = useState(false);

  const topicRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  const topicLabel = t(`featured.topics.${topic}` as `featured.topics.GAINERS`);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setHasError(false);
      try {
        const data = await fetchFeaturedStocks(topic, 30);
        if (cancelled) return;
        if (!Array.isArray(data) || data.length === 0) { setStocks([]); return; }
        setStocks(data.map(dtoToStockItem));
      } catch (e) {
        if (cancelled) return;
        clientLog.warn("Featured stock fallback used", e);
        setStocks(FALLBACK_STOCKS);
        setHasError(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    load();
    return () => { cancelled = true; controller.abort(); };
  }, [topic]);

  useEffect(() => {
    if (isOpen && dropdownRef.current) dropdownRef.current.scrollTop = 0;
  }, [isOpen]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (cardRef.current?.contains(e.target as Node)) return;
      if (topicRef.current?.contains(e.target as Node)) return;
      setIsOpen(false);
    };
    document.addEventListener("click", handler);
    return () => document.removeEventListener("click", handler);
  }, []);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!topicRef.current) return;
      if (topicRef.current.contains(e.target as Node)) return;
      setIsTopicOpen(false);
    };
    document.addEventListener("click", handler);
    return () => document.removeEventListener("click", handler);
  }, []);

  return (
    <section className="grid grid-cols-1 xl:grid-cols-[1.4fr_0.6fr_1fr] gap-3.5 items-start">
      {/* 검색 박스 */}
      <StockInputBox placeholder={placeholder} onSearch={onSearch} />

      {/* 특징주 카테고리 셀렉트 */}
      <div ref={topicRef} className="relative">
        <button
          onClick={(e) => { e.stopPropagation(); setIsTopicOpen((prev) => !prev); }}
          className={`inline-flex items-center gap-2 px-3.5 w-full h-full min-h-[54px] bg-surface border border-line shadow-card text-sm ${isTopicOpen ? "rounded-t-xl border-b-surface" : "rounded-xl"}`}
        >
          <span className="text-xs text-ink-3">{t("featured.label")}</span>
          <span className="flex-1 font-semibold text-base text-ink truncate">{topicLabel}</span>
          {isTopicOpen
            ? <ChevronUp size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />
            : <ChevronDown size={16} className="text-ink-3 flex-shrink-0 pointer-events-none" />}
        </button>

        {isTopicOpen && (
          <div className="absolute w-full bg-surface border border-line border-t-0 rounded-b-xl shadow-pop z-50">
            {TOPIC_CODES.map((code) => (
              <button
                key={code}
                onClick={(e) => { e.stopPropagation(); setTopic(code); setIsTopicOpen(false); setIsOpen(true); }}
                className={`w-full text-left px-3.5 py-2.5 text-sm hover:bg-bg-sunk last:rounded-b-xl ${code === topic ? "text-accent font-semibold bg-accent-soft" : "text-ink"}`}
              >
                {t(`featured.topics.${code}` as `featured.topics.GAINERS`)}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 특징주 카드 */}
      <div className="relative h-[54px]" ref={cardRef}>
        <div className="absolute top-0 left-0 right-0 flex gap-3 items-start z-10">
          <div className={`flex-1 min-w-0 ${isOpen ? "rounded-xl border border-line shadow-pop overflow-hidden" : ""}`}>
            <div ref={dropdownRef} className={isOpen ? "max-h-[400px] overflow-y-auto bg-surface" : ""}>
              {!isOpen ? (
                loading ? (
                  <div className="bg-surface border border-line shadow-card rounded-2xl px-3.5 py-[9px] h-[54px] flex items-center">
                    <span className="text-xs text-ink-3 animate-pulse">{t("featured.loading")}</span>
                  </div>
                ) : stocks[0] ? (
                  <StockCard
                    name={stocks[0].name}
                    symbol={stocks[0].symbol}
                    exchange={stocks[0].exchange}
                    price={stocks[0].price}
                    volume={stocks[0].volume}
                    change={stocks[0].change}
                    changeRate={stocks[0].changeRate}
                    getColorClass={getColorClass}
                    isOpen={false}
                  />
                ) : (
                  <div className="bg-surface border border-line shadow-card rounded-2xl px-3.5 py-[9px] h-[54px] flex items-center">
                    <span className="text-xs text-ink-3">{t("featured.noStocks")}</span>
                  </div>
                )
              ) : loading ? (
                <div className="px-3.5 py-6 text-center text-xs text-ink-3 animate-pulse">{t("featured.loading")}</div>
              ) : stocks.length === 0 ? (
                <div className="px-3.5 py-6 text-center text-xs text-ink-3">{t("featured.noStocks")}</div>
              ) : (
                <>
                  {hasError && (
                    <div className="px-3.5 py-1.5 bg-warn/10 text-[10px] text-warn border-b border-line">
                      {t("featured.fallbackNotice")}
                    </div>
                  )}
                  <div className="px-3.5 py-2 bg-bg-sunk text-[10px] leading-relaxed text-ink-3 border-b border-line">
                    {t("featured.disclaimer")}
                  </div>
                  {stocks.map((stock) => (
                    <div
                      key={stock.symbol}
                      onClick={() => { setIsOpen(false); onSearch(stock.symbol); }}
                      className="flex items-center gap-3 px-3.5 py-[9px] border-b border-line last:border-b-0 hover:bg-bg-sunk transition-colors cursor-pointer"
                    >
                      <div className="min-w-0 flex-shrink-0">
                        <div className="font-semibold text-ink text-sm leading-tight whitespace-nowrap">{stock.name}</div>
                        <div className="text-[10px] text-ink-3 font-mono mt-0.5 whitespace-nowrap">{stock.symbol} · {stock.exchange}</div>
                      </div>
                      <div className="flex-1 flex flex-col items-end min-w-0">
                        <div className={`font-bold font-mono tabular text-sm leading-tight whitespace-nowrap ${getColorClass(stock.changeRate)}`}>{stock.price}</div>
                        <div className="text-ink-3 font-mono tabular text-[10px] mt-0.5 whitespace-nowrap">{stock.volume}</div>
                      </div>
                      <div className="flex-shrink-0">
                        {stock.change.startsWith("+") && (
                          <div className={`${getColorClass(stock.changeRate)} w-0 h-0 border-l-[5px] border-r-[5px] border-b-[8px] border-transparent border-b-current`} />
                        )}
                        {stock.change.startsWith("-") && (
                          <div className={`${getColorClass(stock.changeRate)} w-0 h-0 border-l-[5px] border-r-[5px] border-t-[8px] border-transparent border-t-current`} />
                        )}
                        {!stock.change.startsWith("+") && !stock.change.startsWith("-") && (
                          <span className={`${getColorClass(stock.changeRate)} text-base font-extrabold leading-none`}>-</span>
                        )}
                      </div>
                      <div className="flex flex-col items-end flex-shrink-0 w-16">
                        <div className={`font-bold font-mono tabular leading-tight whitespace-nowrap ${stock.change.length > 6 ? "text-xs" : "text-sm"} ${getColorClass(stock.changeRate)}`}>{stock.change}</div>
                        <div className={`font-medium font-mono tabular text-[10px] mt-0.5 whitespace-nowrap ${getColorClass(stock.changeRate)}`}>{stock.changeRate}</div>
                      </div>
                    </div>
                  ))}
                </>
              )}
            </div>
          </div>
          <button
            onClick={() => setIsOpen((prev) => !prev)}
            className="w-7 h-7 mt-4 bg-accent-soft border border-accent/30 text-accent rounded-full grid place-items-center flex-shrink-0 hover:bg-accent/10 transition-colors"
          >
            <span className="pointer-events-none">
              {isOpen ? <Minus size={14} strokeWidth={2.5} /> : <Plus size={14} strokeWidth={2.5} />}
            </span>
          </button>
        </div>
      </div>
    </section>
  );
}
