// frontend/src/pages/StocksMockPage.tsx
import TradingViewWidget from "../components/TradingViewWidget";
import { useRef, useEffect, useState } from "react";
import StockCard from "../components/StockCard";
import StockInputBox from "../components/StockInputBox";
import { Star } from "lucide-react";
import {
  profitabilitySection,
  valuationSection,
  stabilitySection,
  liquiditySection,
  type IndicatorSection,
} from "../mocks/financialIndicators";
import type { FinancialDto } from "../types/financial";
import { buildSectionsFromDto } from "../mappers/financialMapper";
import { fetchFinancials } from "../api/financial";
import { fetchAnalysis } from "../api/analysis";
import { getStockByCode } from "../api/stock";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import type { AnalysisResponse, ParsedExplainText } from "../types/analysis";

/* =========================
   Zoom-out Loading Policy
========================= */

// "왼쪽 끝이 거의 닿았을 때" 추가 로딩 단위(캔들 개수)
const LOAD_MORE_LIMIT = 5;

// 초기 표시 범위(일)
const INITIAL_HISTORY_DAYS = 30;

// 줌아웃 확장 가능한 최대 히스토리(일)
const MAX_HISTORY_DAYS = 365;

function useKSTTime() {
  const [time, setTime] = useState("");

  useEffect(() => {
    const update = () => {
      const now = new Date().toLocaleString("ko-KR", {
        timeZone: "Asia/Seoul",
        month: "2-digit",
        day: "2-digit",
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
      });
      setTime(now);
    };

    update();
    const interval = setInterval(update, 1000 * 30);
    return () => clearInterval(interval);
  }, []);

  return time;
}

type IndicatorData = {
  ema: Record<string, { t: string; value: number | null }[]> | null;
  bb20_2: { t: string; mid: number | null; upper: number | null; lower: number | null }[] | null;
  stoch14_3_3: { t: string; k: number | null; d: number | null }[] | null;
  warnings: string[];
};

type MainStockState = {
  name: string;
  symbol: string;
  price: number | null;
  change: number | null;
  changeRate: number | null;
};

type LoadMode = "INITIAL" | "ANALYZE";

const parseExplainText = (text?: string | null): ParsedExplainText | null => {
  if (!text || !text.trim()) return null;

  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === "object" ? (parsed as ParsedExplainText) : null;
  } catch (error) {
    console.warn("[analysis] explain JSON parse failed", error);
    return null;
  }
};

export default function StocksMockPage() {
  const currentTime = useKSTTime();

  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [analysisResult, setAnalysisResult] = useState<AnalysisResponse | null>(null);

  // 차트용 indicators state (analysisResult와 분리)
  const [indicatorData, setIndicatorData] = useState<IndicatorData | null>(null);

  // 분석 요청 파라미터
  const [analysisFreq, setAnalysisFreq] = useState<
    | "ONE_MIN"
    | "FIVE_MIN"
    | "FIFTEEN_MIN"
    | "ONE_H"
    | "ONE_D"
    | "ONE_W"
    | "ONE_M"
  >("ONE_D");
  const [analysisFrom, setAnalysisFrom] = useState<string>("");
  const [analysisTo, setAnalysisTo] = useState<string>("");
  const [marketDivCode] = useState<string>("J");
  const [includeExplain] = useState<boolean>(true);

  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const [activeTab, setActiveTab] = useState<"재무제표" | "공매도">("재무제표");
  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  const [financial, setFinancial] = useState<FinancialDto | null>(null);
  const [sections, setSections] = useState<IndicatorSection[]>([
    profitabilitySection,
    valuationSection,
    stabilitySection,
    liquiditySection,
  ]);

  const [isOpen, setIsOpen] = useState(false);

  const isLoadingMoreRef = useRef(false);
  const requestedRangesRef = useRef<Set<string>>(new Set());

  const chartRangeRef = useRef<{
    stockCode: string;
    freq: "ONE_D";
    fromIso: string;
    toIso: string;
    absoluteMinFromIso: string; // 항상 1년 하한 (줌아웃 확장 허용)
    mode: LoadMode;
  } | null>(null);

  const getColorClass = (rate: string) => {
    if (rate.startsWith("+")) return "text-red-600";
    if (rate.startsWith("-")) return "text-blue-600";
    return "text-black";
  };

  const getColorClassByNumber = (n: number | null) => {
    if (n === null || !Number.isFinite(n)) return "text-black";
    if (n > 0) return "text-red-600";
    if (n < 0) return "text-blue-600";
    return "text-black";
  };

  // 초기값은 종목정보만, 가격/등락은 candles로만
  const [mainStock, setMainStock] = useState<MainStockState>({
    name: "삼성전자",
    symbol: "005930",
    price: null,
    change: null,
    changeRate: null,
  });

  // OHLCV 기반 표시값 포맷
  const formatPrice = (n: number) => {
    if (!Number.isFinite(n)) return "0";
    return Math.round(n).toLocaleString("ko-KR");
  };

  const formatSignedNumber = (n: number) => {
    if (!Number.isFinite(n)) return "0";
    if (n === 0) return "0";
    const sign = n > 0 ? "+" : "-";
    return `${sign}${Math.abs(Math.round(n)).toLocaleString("ko-KR")}`;
  };

  const formatSignedPercent = (n: number) => {
    if (!Number.isFinite(n)) return "0%";
    if (n === 0) return "0.00%";
    const sign = n > 0 ? "+" : "-";
    return `${sign}${Math.abs(n).toFixed(2)}%`;
  };

  // mode에 따라 초기 범위는 달리 로딩하되, 줌아웃 하한은 항상 1년으로 설정
  const loadCandles = async (stockCode: string, mode: LoadMode) => {
    setChartLoading(true);
    setChartError(null);

    const toDate = new Date();
    const fromDate = new Date();

    const days = mode === "ANALYZE" ? MAX_HISTORY_DAYS : INITIAL_HISTORY_DAYS;
    fromDate.setDate(toDate.getDate() - (days - 1));

    try {
      const usedFreq = "ONE_D" as const;

      // 차트 로딩과 동시에 분석 파라미터도 동일하게 맞춰둠
      setAnalysisFreq(usedFreq);
      setAnalysisFrom(fromDate.toISOString());
      setAnalysisTo(toDate.toISOString());

      const response = await fetchCandles(
        stockCode,
        usedFreq,
        fromDate.toISOString(),
        toDate.toISOString()
      );

      const data = response.data;

      if (data.length === 0) {
        setChartError("차트 데이터가 없습니다.");
      }

      setCandles(data);

      // 줌아웃 확장 하한은 항상 1년
      const absoluteMin = new Date(toDate);
      absoluteMin.setDate(absoluteMin.getDate() - MAX_HISTORY_DAYS);

      chartRangeRef.current = {
        stockCode,
        freq: usedFreq,
        fromIso: fromDate.toISOString(),
        toIso: toDate.toISOString(),
        absoluteMinFromIso: absoluteMin.toISOString(),
        mode,
      };

      requestedRangesRef.current.clear();

      // 현재가/전일대비/등락률은 candles 기반으로만 산출
      if (data.length > 0) {
        const last = data[data.length - 1];
        const prev = data.length > 1 ? data[data.length - 2] : null;

        const lastClose = Number((last as any).c);
        const prevClose = prev ? Number((prev as any).c) : lastClose;

        if (Number.isFinite(lastClose) && Number.isFinite(prevClose)) {
          const diff = lastClose - prevClose;
          const rate = prevClose !== 0 ? (diff / prevClose) * 100 : 0;

          setMainStock((prevState) => ({
            ...prevState,
            symbol: stockCode,
            price: lastClose,
            change: diff,
            changeRate: rate,
          }));
        } else {
          setMainStock((prevState) => ({
            ...prevState,
            symbol: stockCode,
            price: null,
            change: null,
            changeRate: null,
          }));
        }
      } else {
        setMainStock((prevState) => ({
          ...prevState,
          symbol: stockCode,
          price: null,
          change: null,
          changeRate: null,
        }));
      }
    } catch (e: any) {
      console.error("차트 데이터 조회 실패:", {
        message: e?.message,
        status: e?.response?.status,
        data: e?.response?.data,
        url: e?.config?.baseURL
          ? `${e.config.baseURL}${e.config.url}`
          : e?.config?.url,
        params: e?.config?.params,
      });
      setChartError("차트를 불러오지 못했습니다.");
      setCandles([]);

      setMainStock((prevState) => ({
        ...prevState,
        symbol: stockCode,
        price: null,
        change: null,
        changeRate: null,
      }));
    } finally {
      setChartLoading(false);
    }
  };

  const handleRequestMoreHistory = async () => {
    if (isLoadingMoreRef.current) return;
    if (!chartRangeRef.current) return;
    if (!candles || candles.length === 0) return;

    const { stockCode, freq, absoluteMinFromIso } = chartRangeRef.current;

    const oldest = candles[0];
    if (typeof oldest.t !== "number") return;

    const oldestEpochSec = oldest.t;
    const oldestIso = new Date(oldestEpochSec * 1000).toISOString();
    const absoluteMinDate = new Date(absoluteMinFromIso);

    // 1년 하한선 도달 시 중단
    if (new Date(oldestIso) <= absoluteMinDate) return;

    const rangeKey = `to=${oldestEpochSec}__limit=${LOAD_MORE_LIMIT}`;
    if (requestedRangesRef.current.has(rangeKey)) return;
    requestedRangesRef.current.add(rangeKey);

    isLoadingMoreRef.current = true;
    try {
      const response = await fetchCandlesBefore(
        stockCode,
        freq,
        oldestIso,
        LOAD_MORE_LIMIT
      );

      const incoming: Candle[] = response.data ?? [];
      if (incoming.length === 0) return;

      setCandles((prev) => {
        const map = new Map<number, Candle>();

        for (const c of prev) map.set(c.t, c);
        for (const c of incoming) map.set(c.t, c);

        return Array.from(map.values()).sort((a, b) => a.t - b.t);
      });

      const minIncomingT = Math.min(...incoming.map((c) => c.t));
      if (Number.isFinite(minIncomingT)) {
        chartRangeRef.current = {
          ...chartRangeRef.current!,
          fromIso: new Date(minIncomingT * 1000).toISOString(),
        };
      }
    } catch (e) {
      console.error("추가 캔들 로딩 실패:", e);
    } finally {
      isLoadingMoreRef.current = false;
    }
  };

  const looksLikeCode = (s: string) => {
    return /^\d{6}$/.test(s) || /^[A-Za-z0-9.\-]{1,15}$/.test(s);
  };

  const handleSearch = async (value: string) => {
    console.log("검색 실행:", value);

    const q = value.trim();
    if (!q) {
      setErr("종목을 입력해주세요.");
      setHasSelectedStock(false);
      return;
    }

    setErr("");
    setHasSelectedStock(true);

    setAnalysisResult(null);
    setIndicatorData(null);

    setMainStock((prev) => ({
      ...prev,
      price: null,
      change: null,
      changeRate: null,
    }));

    let resolvedCode: string | null = null;

    try {
      const stockInfo = await getStockByCode(q);
      resolvedCode = stockInfo.stockCode;

      setMainStock((prev) => ({
        ...prev,
        name: stockInfo.companyName,
        symbol: stockInfo.stockCode,
        price: null,
        change: null,
        changeRate: null,
      }));
    } catch (e) {
      console.error("종목 정보 조회 실패(임시 무시):", e);

      if (looksLikeCode(q)) {
        resolvedCode = q;

        setMainStock((prev) => ({
          ...prev,
          symbol: q,
          price: null,
          change: null,
          changeRate: null,
        }));
      } else {
        setErr("종목 코드를 확인할 수 없습니다. (예: 005930, AAPL)");
        setHasSelectedStock(false);
        return;
      }
    }

    const code = resolvedCode;

    try {
      const financials = await fetchFinancials(code, 5);
      if (financials.length > 0) {
        const latest = financials[0];
        setFinancial(latest);
        setSections(buildSectionsFromDto(latest));
      }
    } catch (e) {
      console.error("재무제표 조회 실패:", e);
    }

    // 초기 표시만 30일
    await loadCandles(code, "INITIAL");
  };

  const handleAnalyzeClick = async () => {
    setLoading(true);
    setErr("");
    setAnalysisResult(null);
    setIndicatorData(null);

    if (!mainStock.symbol) {
      setErr("먼저 종목을 검색한 뒤 분석을 실행해주세요.");
      setLoading(false);
      return;
    }

    try {
      // 분석 버튼에서만 1년 로딩으로 교체
      await loadCandles(mainStock.symbol, "ANALYZE");

      const range = chartRangeRef.current;
      if (!range) {
        throw new Error("Chart range is not ready");
      }

      const result = await fetchAnalysis({
        stockCode: mainStock.symbol,
        freq: analysisFreq,
        from: range.fromIso,
        to: range.toIso,
        marketDivCode,
        includeExplain,
      });

      setAnalysisResult(result);

      const ind = result?.metrics?.indicators;

      setIndicatorData({
        ema: ind?.ema ?? null,
        bb20_2: ind?.bb20_2 ?? null,
        stoch14_3_3: ind?.stoch14_3_3 ?? null,
        warnings: ind?.warnings ?? [],
      });
    } catch (e) {
      console.error("분석 결과 조회 실패:", e);
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const formatNumber = (value?: number | null) => {
    if (value === null || value === undefined) {
      return "-";
    }
    return value.toLocaleString();
  };

  const formatDate = (value?: string | null) => {
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
  };

  const featuredListWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  const [isInterested, setIsInterested] = useState(false);

  const [panelPos, setPanelPos] = useState<{
    top: number;
    left: number;
  } | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        featuredListWrapperRef.current?.contains(e.target as Node) ||
        dropdownRef.current?.contains(e.target as Node)
      ) {
        return;
      }
      setIsOpen(false);
    };

    document.addEventListener("click", handleClickOutside);
    return () => document.removeEventListener("click", handleClickOutside);
  }, []);

  const [toast, setToast] = useState<{ message: string; visible: boolean }>({
    message: "",
    visible: false,
  });

  useEffect(() => {
    if (!toast.visible) return;
    const t = setTimeout(() => {
      setToast((prev) => ({ ...prev, visible: false }));
    }, 1800);
    return () => clearTimeout(t);
  }, [toast.visible]);

  const toggleInterest = async () => {
    console.log("toggleInterest clicked, isInterested =", isInterested);
    try {
      setIsInterested((prev) => !prev);
      setToast({
        message: isInterested
          ? "관심종목에서 삭제되었습니다."
          : "관심종목에 추가되었습니다.",
        visible: true,
      });
    } catch (err2) {
      console.error("관심 종목 토글 실패:", err2);
    }
  };

  const [topic, setTopic] = useState<
    | "상승종목"
    | "상한가 임박종목"
    | "하락종목"
    | "상한가 이탈종목"
    | "거래량 상위종목"
    | "거래대금 상위종목"
    | "거래량 급등종목"
  >("상승종목");

  const [isTopicOpen, setIsTopicOpen] = useState(false);
  const topicRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutsideTopic = (e: MouseEvent) => {
      if (!topicRef.current) return;
      if (topicRef.current.contains(e.target as Node)) return;
      setIsTopicOpen(false);
    };

    document.addEventListener("click", handleClickOutsideTopic);
    return () => document.removeEventListener("click", handleClickOutsideTopic);
  }, []);

  const displayPrice =
    mainStock.price !== null && Number.isFinite(mainStock.price)
      ? formatPrice(mainStock.price)
      : "-";

  const mainNumericChange =
    mainStock.change !== null && Number.isFinite(mainStock.change)
      ? mainStock.change
      : 0;

  const displayChange =
    mainStock.change !== null && Number.isFinite(mainStock.change)
      ? formatSignedNumber(mainStock.change)
      : "0";

  const displayRate =
    mainStock.changeRate !== null && Number.isFinite(mainStock.changeRate)
      ? formatSignedPercent(mainStock.changeRate)
      : "0.00%";

  const mainColorClass = getColorClassByNumber(mainNumericChange);

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            심층분석
          </h1>
        </header>

        <section className="w-full flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="w-full lg:max-w-md bg-white rounded-[10px] outline outline-1 outline-stone-300 px-2 py-1 sm:px-2 sm:py-1 flex flex-col gap-2">
            <StockInputBox placeholder="종목을 입력해주세요" onSearch={handleSearch} />
          </div>

          <div
            ref={featuredListWrapperRef}
            className="w-full lg:flex-1 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-end"
          >
            <div ref={topicRef} className="relative w-40">
              <button
                onClick={() => setIsTopicOpen((prev) => !prev)}
                className="
                  w-full h-9 sm:h-10
                  border border-black rounded-md
                  bg-white
                  flex items-center
                  px-3
                  font-medium
                  relative
                "
              >
                <span
                  className="
                    flex-1 text-center truncate whitespace-nowrap
                    text-xs sm:text-sm
                  "
                >
                  {topic}
                </span>

                <span className="absolute right-1 sm:right-2 text-[10px] sm:text-xs">
                  {isTopicOpen ? "▲" : "▼"}
                </span>
              </button>

              {isTopicOpen && (
                <div
                  className="
                    absolute mt-1 w-full
                    bg-white border border-stone-300 rounded-md shadow-md
                    z-50
                  "
                >
                  {[
                    "상승종목",
                    "상한가 임박종목",
                    "하락종목",
                    "상한가 이탈종목",
                    "거래량 상위종목",
                    "거래대금 상위종목",
                    "거래량 급등종목",
                  ].map((item) => (
                    <button
                      key={item}
                      onClick={() => {
                        setTopic(item as typeof topic);
                        setIsTopicOpen(false);
                      }}
                      className="
                        w-full text-left px-3 py-2 text-sm sm:text-base
                        hover:bg-zinc-100
                      "
                    >
                      {item}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="flex items-center gap-2 relative">
              <div ref={cardRef} className="inline-block w-[260px] sm:w-[280px] lg:w-[380px]">
                <StockCard
                  name={mainStock.name}
                  price={displayPrice === "-" ? "-" : displayPrice}
                  volume={"-"}
                  change={displayChange}
                  changeRate={displayRate}
                  getColorClass={getColorClass}
                />
              </div>

              <button
                onClick={() => {
                  setIsOpen((prev) => !prev);

                  if (!cardRef.current) return;
                  const rect = cardRef.current.getBoundingClientRect();

                  setPanelPos({
                    top: rect.top,
                    left: rect.left,
                  });
                }}
                className="
                  w-6 h-6
                  bg-zinc-300
                  rounded-full
                  flex items-center justify-center
                  transition-colors hover:bg-zinc-400
                  flex-shrink-0
                "
              >
                <span className="text-lg font-bold leading-none">
                  {isOpen ? "-" : "+"}
                </span>
              </button>
            </div>
          </div>
        </section>

        {isOpen && panelPos && (
          <div
            ref={dropdownRef}
            className="
              fixed
              w-[260px] sm:w-[280px] lg:w-[380px]
              max-h-[400px]
              bg-white border border-stone-300 rounded-sm shadow-md
              overflow-y-auto
              overflow-x-hidden
              z-50
            "
            style={{
              top: panelPos.top,
              left: panelPos.left,
            }}
          >
            <div className="pr-3">
              <div className="px-3 py-3 text-sm text-zinc-600">
                목업데이터 지운 상태임 이름, 종목코드 매핑후 순위 정렬해야함
              </div>
            </div>
          </div>
        )}

        {hasSelectedStock && (
          <main className="w-full flex flex-col gap-4 sm:gap-5">
            <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1.2fr)] gap-4 lg:gap-6 items-start">
              <div className="flex flex-col gap-3 sm:gap-4">
                <section className="w-full bg-zinc-100 rounded-2xl p-3 sm:p-4 md:p-5 flex flex-col gap-3 xl:h-[520px] min-h-0">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex flex-wrap items-end gap-1.5">
                      <h2 className="text-lg sm:text-xl md:text-2xl font-medium text-black">
                        {mainStock.name}
                      </h2>
                      <span className="text-sm sm:text-base md:text-lg text-black">
                        ({mainStock.symbol})
                      </span>
                      <button onClick={toggleInterest} className="ml-2 inline-block">
                        <Star
                          size={22}
                          className="relative -top-1 transition-colors text-yellow-400"
                          fill={isInterested ? "currentColor" : "none"}
                        />
                      </button>
                    </div>

                    <span className="text-[10px] sm:text-xs font-medium text-black">
                      {currentTime} KST
                    </span>

                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-2xl md:text-3xl font-medium text-black">
                        {displayPrice}
                      </span>

                      <div className="flex items-center gap-1.5 text-sm md:text-base font-medium">
                        <span className={mainColorClass}>{displayChange}</span>
                        <span className={mainColorClass}>({displayRate})</span>
                        {mainNumericChange > 0 && (
                          <div
                            className={`${mainColorClass} w-0 h-0 
                            border-l-[6px] border-r-[6px] 
                            border-b-[9px] border-transparent 
                            border-b-current`}
                          />
                        )}
                        {mainNumericChange < 0 && (
                          <div
                            className={`${mainColorClass} w-0 h-0 
                            border-l-[6px] border-r-[6px] 
                            border-t-[9px] border-transparent 
                            border-t-current`}
                          />
                        )}
                        {mainNumericChange === 0 && (
                          <span
                            className={`
                            ${mainColorClass}
                            text-xl sm:text-2xl
                            font-extrabold
                            leading-none
                          `}
                          >
                            -
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="w-full flex-1 bg-white rounded-xl overflow-hidden min-h-0 flex flex-col">
                    {/* 차트영역은 stretch + min-h-0 */}
                    <div className="flex-1 min-h-0 w-full flex items-stretch">
                      {chartLoading && (
                        <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                          <p className="text-sm sm:text-base text-gray-600">차트를 불러오는 중입니다...</p>
                        </div>
                      )}

                      {chartError && !chartLoading && (
                        <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                          <p className="text-sm sm:text-base text-red-600">{chartError}</p>
                        </div>
                      )}

                      {!chartLoading && !chartError && (
                        <div className="flex-1 min-h-0 w-full">
                          {/* TradingViewWidget 부모 높이를 100% 사용 */}
                          <TradingViewWidget
                            candles={candles}
                            indicators={indicatorData}
                            showSubPanes={Boolean(indicatorData)}
                            onRequestMoreHistory={handleRequestMoreHistory}
                          />
                        </div>
                      )}
                    </div>
                  </div>
                </section>
              </div>

              <div className="w-full h-[520px] px-4 sm:px-6 py-8 bg-zinc-100 rounded-2xl flex flex-col items-center overflow-hidden">
                <div className="flex w-full mb-4">
                  <button
                    className={`flex-1 text-[20px] py-2 font-semibold ${
                      activeTab === "재무제표" ? "bg-zinc-200" : "bg-white"
                    } rounded-l-[15px]`}
                    onClick={() => setActiveTab("재무제표")}
                  >
                    재무제표
                  </button>
                  <button
                    className={`flex-1 text-[20px] py-2 font-semibold ${
                      activeTab === "공매도" ? "bg-zinc-200" : "bg-white"
                    } rounded-r-[15px]`}
                    onClick={() => setActiveTab("공매도")}
                  >
                    공매도
                  </button>
                </div>

                <div className="w-full h-full overflow-y-auto flex flex-col gap-5 pr-2">
                  {activeTab === "재무제표" ? (
                    sections ? (
                      sections.map((section) => (
                        <IndicatorSectionBlock
                          key={section.sectionTitle}
                          section={section}
                          layout={
                            section.sectionTitle === "수익성"
                              ? "3-2"
                              : section.sectionTitle === "가치(밸류에이션)"
                              ? "2-2"
                              : "2"
                          }
                        />
                      ))
                    ) : (
                      <p className="text-sm text-gray-500">
                        재무제표 데이터를 불러오는 중입니다.
                      </p>
                    )
                  ) : (
                    <div className="flex flex-col justify-center items-center w-full h-full min-h-[520px]">
                      <span className="text-black text-base sm:text-lg md:text-xl font-normal">
                        공매도 정보 준비 중입니다.
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </div>

            <section className="w-full bg-zinc-100 rounded-2xl py-8 sm:py-10 flex flex-col items-center justify-center gap-4 mt-2">
              <button
                onClick={handleAnalyzeClick}
                className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
              >
                분석 결과 보기
              </button>
    
              {loading && (
                <p className="text-sm sm:text-base text-gray-600">분석 중입니다...</p>
              )}

              {err && <p className="text-sm sm:text-base text-red-600">{err}</p>}

              {analysisResult && (
                <div className="w-[90%] max-w-4xl bg-white rounded-2xl shadow-sm border border-zinc-200 p-4 sm:p-6 flex flex-col gap-4">
                  <div>
                    <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                      설명
                    </h3>
                    {(() => {
                      const parsedExplain = parseExplainText(analysisResult.explain?.text);
                      const rawExplain = analysisResult.explain?.text;

                      if (parsedExplain) {
                        return (
                          <div className="mt-2 text-sm sm:text-base text-zinc-700 flex flex-col gap-2">
                            <div>
                              <p className="font-medium text-zinc-900">요약</p>
                              <ul className="list-disc list-inside">
                                {(parsedExplain.summary ?? []).slice(0, 3).map((item, idx) => (
                                  <li key={`summary-${idx}`}>{item}</li>
                                ))}
                              </ul>
                            </div>
                            <div>
                              <p className="font-medium text-zinc-900">리스크</p>
                              <ul className="list-disc list-inside">
                                {(parsedExplain.risks ?? []).slice(0, 2).map((item, idx) => (
                                  <li key={`risk-${idx}`}>{item}</li>
                                ))}
                              </ul>
                            </div>
                            <div>
                              <p className="font-medium text-zinc-900">결론</p>
                              <p>{parsedExplain.conclusion ?? "-"}</p>
                            </div>
                          </div>
                        );
                      }

                      if (rawExplain && rawExplain.trim()) {
                        return (
                          <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                            {rawExplain}
                          </p>
                        );
                      }

                      return (
                        <p className="text-sm sm:text-base text-zinc-500 mt-2">
                          설명 생성이 비활성화되었거나 실패했습니다.
                        </p>
                      );
                    })()}
                  </div>

                  <div>
                    <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                      OHLCV 요약
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm sm:text-base text-zinc-700 mt-2">
                      <div>
                        캔들 수:{" "}
                        {analysisResult.metrics.ohlcvSummary.count.toLocaleString()}
                      </div>
                      <div>
                        기간: {formatDate(analysisResult.metrics.ohlcvSummary.from)} ~{" "}
                        {formatDate(analysisResult.metrics.ohlcvSummary.to)}
                      </div>
                      <div>
                        마지막 종가:{" "}
                        {formatNumber(analysisResult.metrics.ohlcvSummary.lastClose)}
                      </div>
                    </div>
                  </div>

                  <div>
                    <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                      Indicator Summary
                    </h3>
                    <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                      {analysisResult.metrics.indicatorSummary?.trim()
                        ? analysisResult.metrics.indicatorSummary
                        : "지표 요약 데이터를 생성하지 못했습니다."}
                    </p>
                  </div>

                  <div>
                    <h3 className="text-base sm:text-lg font-semibold text-zinc-900">
                      재무 요약 (5개년)
                    </h3>
                    {analysisResult.metrics.financialSummary.years.length > 0 ? (
                      <div className="overflow-x-auto mt-2">
                        <table className="min-w-full text-xs sm:text-sm text-zinc-700 border border-zinc-200">
                          <thead className="bg-zinc-100 text-zinc-900">
                            <tr>
                              <th className="px-3 py-2 text-left border-b">구분</th>
                              {analysisResult.metrics.financialSummary.years.map((year) => (
                                <th key={year} className="px-3 py-2 text-right border-b">
                                  {year}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody>
                            <tr>
                              <td className="px-3 py-2 border-b">매출</td>
                              {analysisResult.metrics.financialSummary.years.map((year) => (
                                <td
                                  key={`revenue-${year}`}
                                  className="px-3 py-2 text-right border-b"
                                >
                                  {formatNumber(
                                    analysisResult.metrics.financialSummary.revenue[String(year)]
                                  )}
                                </td>
                              ))}
                            </tr>
                            <tr>
                              <td className="px-3 py-2 border-b">영업이익</td>
                              {analysisResult.metrics.financialSummary.years.map((year) => (
                                <td
                                  key={`op-${year}`}
                                  className="px-3 py-2 text-right border-b"
                                >
                                  {formatNumber(
                                    analysisResult.metrics.financialSummary.operatingIncome[String(year)]
                                  )}
                                </td>
                              ))}
                            </tr>
                            <tr>
                              <td className="px-3 py-2 border-b">순이익</td>
                              {analysisResult.metrics.financialSummary.years.map((year) => (
                                <td
                                  key={`net-${year}`}
                                  className="px-3 py-2 text-right border-b"
                                >
                                  {formatNumber(
                                    analysisResult.metrics.financialSummary.netIncome[String(year)]
                                  )}
                                </td>
                              ))}
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <p className="text-sm sm:text-base text-zinc-500 mt-2">
                        재무 요약 데이터를 확보하지 못했습니다.
                      </p>
                    )}
                  </div>

                  {(analysisResult.meta?.warnings ?? []).length > 0 && (
                    <div>
                      <h3 className="text-base sm:text-lg font-semibold text-zinc-900">경고</h3>
                      <ul className="text-sm sm:text-base text-amber-700 mt-2 list-disc list-inside">
                        {analysisResult.meta?.warnings?.map((warning) => (
                          <li key={warning}>{warning}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </section>
          </main>
        )}

        {toast.visible && (
          <div
            className="
              fixed bottom-8 left-1/2 -translate-x-1/2
              bg-black/70 text-white
              px-4 py-2 rounded-md
              text-sm sm:text-base
            "
          >
            {toast.message}
          </div>
        )}
      </div>
    </div>
  );
}

/** 공통 셀 */
function Cell({
  title,
  subtitle,
  value,
  className = "",
}: {
  title: string;
  subtitle: string;
  value: string;
  className?: string;
}) {
  return (
    <div className={`px-3 py-2 bg-white flex flex-col ${className}`}>
      <div className="flex justify-between items-center">
        <span className="text-black text-sm sm:text-base font-semibold">
          {title}
        </span>
        <span className="text-black text-sm sm:text-base font-semibold whitespace-nowrap">
          {value}
        </span>
      </div>

      <div className="h-[3px] sm:h-[4px]" />

      <span className="text-black text-[11px] sm:text-xs font-normal leading-tight">
        {subtitle}
      </span>

      <div className="h-[3px] sm:h-[4px]" />
    </div>
  );
}

type IndicatorSectionBlockProps = {
  section: IndicatorSection;
  layout: "3-2" | "2-2" | "2";
};

function IndicatorSectionBlock({ section, layout }: IndicatorSectionBlockProps) {
  const rows = section.rows;

  if (layout === "3-2") {
    const top = rows.slice(0, 3);
    const bottom = rows.slice(3);

    return (
      <div className="flex flex-col gap-2.5">
        <div className="text-black text-base sm:text-lg md:text-xl font-normal">
          {section.sectionTitle}
        </div>
        <div className="border border-stone-300 rounded-2xl overflow-hidden">
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
            {top.map((cell, idx) => (
              <Cell
                key={`${cell.title}-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
                className={`border-b ${idx !== top.length - 1 ? "border-r" : ""}`}
              />
            ))}
          </div>
          <div className="grid grid-cols-2">
            {bottom.map((cell, idx) => (
              <Cell
                key={`${cell.title}-bottom-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
                className={idx === 0 ? "border-r" : ""}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (layout === "2-2") {
    return (
      <div className="flex flex-col gap-2.5">
        <div className="text-black text-base sm:text-lg md:text-xl font-normal">
          {section.sectionTitle}
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 border border-stone-300 rounded-2xl overflow-hidden">
          {rows.map((cell, idx) => (
            <Cell
              key={`${cell.title}-${idx}`}
              title={cell.title}
              subtitle={cell.subtitle}
              value={cell.value}
              className={`${idx < 2 ? "border-b" : ""} ${
                idx % 2 === 0 ? "border-r" : ""
              }`}
            />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2.5">
      <div className="text-black text-base sm:text-lg md:text-xl font-normal">
        {section.sectionTitle}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 border border-stone-300 rounded-2xl overflow-hidden">
        {rows.map((cell, idx) => (
          <Cell
            key={`${cell.title}-${idx}`}
            title={cell.title}
            subtitle={cell.subtitle}
            value={cell.value}
            className={idx % 2 === 0 ? "border-r" : ""}
          />
        ))}
      </div>
    </div>
  );
}