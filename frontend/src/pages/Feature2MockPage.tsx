// Feature2MockPage.tsx
import { useState, useRef, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Star } from "lucide-react";
import StockInputBox from "../components/StockInputBox";
import StockCard from "../components/StockCard";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import TradingViewWidget from "../components/TradingViewWidget";
import Feature2ExternalFactorPanel from "../components/Feature2ExternalFactorPanel";

import AnalysisResultPanel from "../components/AnalysisResultPanel";
import type { AnalysisPanelResult } from "../types/analysisPanel";
import {
  fetchFeature2Analysis,
  fetchFeature2BaseRate,
  fetchFeature2BaseRateSeries,
  fetchFeature2IndustryIndex,
  fetchFeature2InvestorFlow,
  fetchFeature2MacroRates,
  fetchFeature2MacroRatesSeries,
  fetchFeature2RelatedStocks,
  fetchFeature2ShortSellingSeries,
  fetchFeature2ShortSelling,
} from "../api/feature2";
import RelativeLineWidget from "../components/RelativeLineWidget";
import type {
  BaseRateSeriesPoint,
  BaseRateMetrics,
  Feature2AnalyzeResponse,
  Feature2InvestorFlow,
  Feature2MacroRates,
  Feature2MacroRatesSeries,
  IndustryIndexBlock,
  RelatedStockCard,
  ShortSellingSeriesPoint,
  ShortSellingMetrics,
} from "../types/feature2";
import type { ApiResponse } from "../types/common/api";
import { fetchNewsByStock } from "../api/news";
import type { NewsItemDto } from "../types/news";
import { getStockByCode } from "../api/stock";
import { isLoggedIn } from "../utils/auth";
import DictTerm from "../components/DictTerm";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { formatKstOffsetDateTime, shiftKstDays } from "../utils/kst";

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

type MainStockState = {
  name: string;
  symbol: string;
  price: number | null;
  change: number | null;
  changeRate: number | null;
};

const INITIAL_INDUSTRY_FREQ = "ONE_D";
const INITIAL_INDUSTRY_WINDOW = 120;
const INITIAL_HISTORY_DAYS = 30;
const MAX_HISTORY_DAYS = 365;
const LOAD_MORE_LIMIT = 5;
const INITIAL_RELATED_STOCK_LIMIT = 30;

const clampChartWindow = (window: number) =>
  Math.max(INITIAL_HISTORY_DAYS, Math.min(window, MAX_HISTORY_DAYS));

const toKstDayKeyFromEpochSec = (sec: number) => {
  const normalizedSec = sec > 10_000_000_000 ? Math.floor(sec / 1000) : sec;
  const kstDate = new Date(normalizedSec * 1000 + 9 * 60 * 60 * 1000);
  const y = kstDate.getUTCFullYear();
  const m = String(kstDate.getUTCMonth() + 1).padStart(2, "0");
  const d = String(kstDate.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

const kstDayKeyToEpochSec = (dayKey: string) =>
  Math.floor(new Date(`${dayKey}T00:00:00+09:00`).getTime() / 1000);

const extractSeriesDayKey = (value: string): string | null => {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (match) return `${match[1]}-${match[2]}-${match[3]}`;

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return toKstDayKeyFromEpochSec(Math.floor(parsed.getTime() / 1000));
};

const collectSeriesDayKeys = (series?: { t?: string | null }[] | null): Set<string> => {
  const keys = new Set<string>();
  for (const point of series ?? []) {
    if (!point?.t) continue;
    const dayKey = extractSeriesDayKey(point.t);
    if (dayKey) keys.add(dayKey);
  }
  return keys;
};

interface RelatedStockDisplay {
  stockCode: string;
  companyName: string;
  price: number;
  change: number;
  changeRate: number;
  volume: number;
}

interface FeaturedStock {
  name: string;
  symbol: string;
  price: string;
  volume: string;
  change: string;
  changeRate: string;
}

type Feature2PanelExplain = NonNullable<AnalysisPanelResult["explain"]>;

const coerceExplainSection = (value: unknown, title: string) => {
  if (!value || typeof value !== "object") return null;
  const section = value as { summary?: unknown; bullets?: unknown };
  const summary = typeof section.summary === "string" ? section.summary.trim() : "";
  const bullets = Array.isArray(section.bullets)
    ? section.bullets.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];

  if (!summary && bullets.length === 0) return null;
  return {
    title,
    summary: summary || null,
    bullets: bullets.length > 0 ? bullets : null,
  };
};

const parseFeature2Explain = (raw?: string | null): Feature2PanelExplain | null => {
  if (!raw?.trim()) return null;
  try {
    const parsed = JSON.parse(raw) as {
      sections?: Record<string, unknown>;
      overall?: { summary?: unknown; bullets?: unknown; risks?: unknown; conclusion?: unknown };
    };
    const sections = parsed.sections ?? {};
    const overall = parsed.overall ?? {};
    const overallSummary = typeof overall.summary === "string" ? overall.summary.trim() : "";
    const conclusion = typeof overall.conclusion === "string" ? overall.conclusion.trim() : "";
    const bullets = Array.isArray(overall.bullets)
      ? overall.bullets.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
      : [];
    const risks = Array.isArray(overall.risks)
      ? overall.risks.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
      : [];

    return {
      text: overallSummary || raw,
      sections: {
        peerCluster: coerceExplainSection(sections.peer_cluster, "유사종목 반응구조 요약"),
        newsSentiment: coerceExplainSection(sections.news_sentiment, "뉴스감성 요약"),
        trendSummary: coerceExplainSection(sections.trend_summary, "기간추이 요약"),
        baseRate: coerceExplainSection(sections.base_rate, "기준금리 추이 요약"),
        shortSelling: coerceExplainSection(sections.short_selling, "공매도 추이 요약"),
      },
      overall: {
        summary: overallSummary || null,
        bullets: bullets.length > 0 ? bullets : null,
        risks: risks.length > 0 ? risks : null,
        conclusion: conclusion || null,
      },
    };
  } catch {
    return { text: raw };
  }
};

const mapRelatedStocks = (rows: RelatedStockCard[]): RelatedStockDisplay[] =>
  rows.map((row) => ({
    stockCode: row.stockCode,
    companyName: row.companyName,
    price: row.price ?? 0,
    change: row.changeAmount ?? 0,
    changeRate: row.changeRate ?? 0,
    volume: row.volume ?? 0,
  }));

const isMacroRatesEmpty = (data: Feature2MacroRates | null | undefined) =>
  !data?.usdKrw &&
  !data?.krBaseRate &&
  !data?.usFedFundsRate &&
  (!data?.bondYields || data.bondYields.length === 0);

export default function Feature2MockPage() {
  const navigate = useNavigate();
  const searchRequestIdRef = useRef(0);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const requestedRangesRef = useRef<Set<string>>(new Set());
  const isLoadingMoreRef = useRef(false);
  const chartRangeRef = useRef<{
    stockCode: string;
    freq: "ONE_D";
    fromIso: string;
    toIso: string;
    absoluteMinFromIso: string;
  } | null>(null);

  const applyCandleResponse = (
    stockCode: string,
    data: Candle[],
    fromIso: string,
    toIso: string,
    absoluteMinFromIso: string,
    allowedDayKeys?: Set<string>,
  ) => {
    const displayData = allowedDayKeys
      ? data
        .filter((candle) => allowedDayKeys.has(toKstDayKeyFromEpochSec(candle.t)))
        .map((candle) => ({
          ...candle,
          t: kstDayKeyToEpochSec(toKstDayKeyFromEpochSec(candle.t)),
        }))
      : data;

    if (displayData.length === 0) {
      setChartError("차트 데이터가 없습니다.");
    }

    setCandles(displayData);
    chartRangeRef.current = {
      stockCode,
      freq: "ONE_D",
      fromIso,
      toIso,
      absoluteMinFromIso,
    };
    requestedRangesRef.current.clear();

    // 현재가/전일대비/등락률을 candles 기반으로 산출 (기능1과 동일)
    if (displayData.length > 0) {
      const last = displayData[displayData.length - 1];
      const prev = displayData.length > 1 ? displayData[displayData.length - 2] : null;
      const lastClose = Number(last.c);
      const prevClose = prev ? Number(prev.c) : lastClose;

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
        return;
      }
    }

    setMainStock((prevState) => ({
      ...prevState,
      symbol: stockCode,
      price: null,
      change: null,
      changeRate: null,
    }));
  };

  const loadCandlesForRange = async (
    stockCode: string,
    fromIso: string,
    toIso: string,
    allowedDayKeys?: Set<string>,
  ) => {
    setChartLoading(true);
    setChartError(null);

    try {
      const response = await fetchCandles(
        stockCode,
        "ONE_D",
        fromIso,
        toIso,
      );

      applyCandleResponse(
        stockCode,
        response.data,
        fromIso,
        toIso,
        fromIso,
        allowedDayKeys,
      );
    } catch (e: unknown) {
      console.error("차트 데이터 조회 실패:", e);
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

  const loadCandles = async (stockCode: string, historyDays = INITIAL_HISTORY_DAYS) => {
    const toDate = new Date();
    const fromDate = shiftKstDays(toDate, -(clampChartWindow(historyDays) - 1));
    await loadCandlesForRange(
      stockCode,
      formatKstOffsetDateTime(fromDate),
      formatKstOffsetDateTime(toDate),
    );
  };

  const loadCandlesForIndustrySeries = async (
    stockCode: string,
    dayKeys: Set<string>,
    fallbackWindow: number,
  ) => {
    if (dayKeys.size === 0) {
      await loadCandles(stockCode, fallbackWindow);
      return;
    }

    const sortedDayKeys = Array.from(dayKeys).sort((a, b) => a.localeCompare(b));
    const fromIso = `${sortedDayKeys[0]}T00:00:00+09:00`;
    const toIso = `${sortedDayKeys[sortedDayKeys.length - 1]}T23:59:59+09:00`;
    await loadCandlesForRange(stockCode, fromIso, toIso, new Set(sortedDayKeys));
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
        LOAD_MORE_LIMIT,
      );

      const incoming: Candle[] = response.data ?? [];
      if (incoming.length === 0) return;

      setCandles((prev) => {
        const map = new Map<number, Candle>();
        for (const candle of prev) map.set(candle.t, candle);
        for (const candle of incoming) map.set(candle.t, candle);
        return Array.from(map.values()).sort((a, b) => a.t - b.t);
      });

      const minIncomingT = Math.min(...incoming.map((candle) => candle.t));
      if (Number.isFinite(minIncomingT)) {
        chartRangeRef.current = {
          ...chartRangeRef.current,
          fromIso: new Date(minIncomingT * 1000).toISOString(),
        };
      }
    } catch (e) {
      console.error("추가 캔들 로딩 실패:", e);
    } finally {
      isLoadingMoreRef.current = false;
    }
  };

  const [industryChartLoading, setIndustryChartLoading] = useState(false);
  const [industryChartError, setIndustryChartError] = useState<string | null>(null);

  const [featuredStocks] = useState<FeaturedStock[]>([
    {
      name: "삼성전자",
      symbol: "005930",
      price: "70,000",
      volume: "12,345,678",
      change: "500",
      changeRate: "+0.72%",
    },
    {
      name: "LG에너지솔루션",
      symbol: "373220",
      price: "400,000",
      volume: "3,210,987",
      change: "-2,000",
      changeRate: "-0.50%",
    },
    {
      name: "카카오",
      symbol: "035720",
      price: "55,000",
      volume: "8,765,432",
      change: "0",
      changeRate: "0%",
    },
  ]);

  const featuredListWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  const [isOpen, setIsOpen] = useState(false);
  const [panelPos, setPanelPos] = useState<{ top: number; left: number } | null>(null);

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

  const [analysisResult, setAnalysisResult] = useState<ApiResponse<Feature2AnalyzeResponse> | null>(null);
  const analysisData = analysisResult?.data ?? null;
  const [baseRateResult, setBaseRateResult] = useState<ApiResponse<BaseRateMetrics | null> | null>(null);
  const [macroRatesResult, setMacroRatesResult] = useState<ApiResponse<Feature2MacroRates | null> | null>(null);
  const [macroRatesSeriesResult, setMacroRatesSeriesResult] = useState<ApiResponse<Feature2MacroRatesSeries | null> | null>(null);
  const [macroRatesLoading, setMacroRatesLoading] = useState(false);
  const [macroRatesError, setMacroRatesError] = useState<string | null>(null);
  const [shortSellingResult, setShortSellingResult] = useState<ApiResponse<ShortSellingMetrics | null> | null>(null);
  const [shortSellingSeriesResult, setShortSellingSeriesResult] = useState<ApiResponse<ShortSellingSeriesPoint[]> | null>(null);
  const [baseRateSeriesResult, setBaseRateSeriesResult] = useState<ApiResponse<BaseRateSeriesPoint[]> | null>(null);
  const [industryIndexResult, setIndustryIndexResult] = useState<ApiResponse<IndustryIndexBlock | null> | null>(null);
  const [investorFlowResult, setInvestorFlowResult] = useState<ApiResponse<Feature2InvestorFlow | null> | null>(null);
  const [investorFlowLoading, setInvestorFlowLoading] = useState(false);
  const [investorFlowError, setInvestorFlowError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [displayText, setDisplayText] = useState("");
  const [llmVendor, setLlmVendor] = useState<string>("Gemini 2.5 Flash");
  const [selectedFreq, setSelectedFreq] = useState<"ONE_D" | "ONE_W">("ONE_D");
  const [selectedWindow, setSelectedWindow] = useState<60 | 120 | 180 | 252>(120);
  const feature2Explain = useMemo(
    () => parseFeature2Explain(analysisData?.explain),
    [analysisData?.explain],
  );

  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  const [mainStock, setMainStock] = useState<MainStockState>({
    name: "",
    symbol: "",
    price: null,
    change: null,
    changeRate: null,
  });

  const currentTime = useKSTTime();

  const [isInterested, setIsInterested] = useState(false);
  const [toast, setToast] = useState<{ message: string; visible: boolean }>({
    message: "",
    visible: false,
  });

  const loadMacroRates = async (requestId?: number) => {
    setMacroRatesLoading(true);
    setMacroRatesError(null);
    try {
      const [result, seriesResult] = await Promise.all([
        fetchFeature2MacroRates(),
        fetchFeature2MacroRatesSeries(120),
      ]);
      if (requestId != null && searchRequestIdRef.current !== requestId) return;
      setMacroRatesResult(result);
      setMacroRatesSeriesResult(seriesResult);
      if (isMacroRatesEmpty(result.data)) {
        setMacroRatesError("환율/국채 데이터를 찾지 못했습니다.");
      }
    } catch (error) {
      if (requestId != null && searchRequestIdRef.current !== requestId) return;
      console.error("매크로 금리/환율 조회 실패:", error);
      setMacroRatesResult(null);
      setMacroRatesSeriesResult(null);
      setMacroRatesError("환율/국채 데이터를 불러오지 못했습니다.");
    } finally {
      if (requestId == null || searchRequestIdRef.current === requestId) {
        setMacroRatesLoading(false);
      }
    }
  };

  useEffect(() => {
    if (!toast.visible) return;
    const t = setTimeout(() => {
      setToast((prev) => ({ ...prev, visible: false }));
    }, 1800);
    return () => clearTimeout(t);
  }, [toast.visible]);

  const toggleInterest = () => {
    setIsInterested((prev) => !prev);
    setToast({
      message: isInterested
        ? "관심종목에서 삭제되었습니다."
        : "관심종목에 추가되었습니다.",
      visible: true,
    });
  };

  const industrySeries = useMemo(() => {
  const raw = (analysisData?.metrics?.industryIndex ?? industryIndexResult?.data ?? null)?.series;

  console.log("🔥 [RAW industryIndex.series]", raw);

  if (!raw || !Array.isArray(raw)) return [];

  const mapped = raw
    .filter(
      (p: { t?: unknown; value?: unknown }): p is { t: string; value: unknown } =>
        p &&
        typeof p.t === "string" &&
        Number.isFinite(Number(p.value)),
    )
    .map((p) => ({
      t: p.t,
      value: Number(p.value),
    }));

  console.log("🔥 [MAPPED industrySeries]", mapped);

  return mapped;
}, [analysisData, industryIndexResult]);

  const handleSearch = async (value: string) => {
    const q = value.trim();
    if (!q) return;
    const requestId = ++searchRequestIdRef.current;

    setHasSelectedStock(true);
    setShowAnalyzeButton(true);
    setAnalysisResult(null);
    setMacroRatesResult(null);
    setMacroRatesSeriesResult(null);
    setMacroRatesError(null);
    setShortSellingResult(null);
    setShortSellingSeriesResult(null);
    setShortSellingSeriesResult(null);
    setBaseRateSeriesResult(null);
    setIndustryIndexResult(null);
    setInvestorFlowResult(null);
    setInvestorFlowError(null);
    setDisplayText("");
    setErr("");

    let resolvedStockCode = q;

    // 종목명 조회
    try {
      const stockInfo = await getStockByCode(q);
      if (stockInfo) {
        resolvedStockCode = stockInfo.stockCode || q;
        setMainStock({
          name: stockInfo.companyName || q,
          symbol: resolvedStockCode,
          price: stockInfo.price ?? null,
          change: null,
          changeRate: stockInfo.changeRate ?? null,
        });
      }
    } catch {
      // 종목명 조회 실패 시 코드를 이름으로 사용
      setMainStock({
        name: q,
        symbol: q,
        price: null,
        change: null,
        changeRate: null,
      });
    }

    setNewsLoading(true);
    setNewsError(null);
    setNewsItems([]);
    setRelatedLoading(true);
    setRelatedError(null);
    setRelatedStocks([]);
    setInvestorFlowLoading(true);

    const baseRateTask = (async () => {
      try {
        const result = await fetchFeature2BaseRate();
        setBaseRateResult(result);
      } catch {
        setBaseRateResult(null);
      }
    })();

    const macroRatesTask = loadMacroRates(requestId);

    const newsTask = (async () => {
      try {
        const news = await fetchNewsByStock(resolvedStockCode);
        if (searchRequestIdRef.current !== requestId) return;
        setNewsItems(news);
      } catch {
        if (searchRequestIdRef.current !== requestId) return;
        setNewsError("뉴스를 불러오지 못했습니다.");
      } finally {
        if (searchRequestIdRef.current === requestId) {
          setNewsLoading(false);
        }
      }
    })();

    const shortSellingTask = (async () => {
      try {
        const [result, seriesResult] = await Promise.all([
          fetchFeature2ShortSelling(resolvedStockCode),
          fetchFeature2ShortSellingSeries(resolvedStockCode, 120),
        ]);
        if (searchRequestIdRef.current !== requestId) return;
        setShortSellingResult(result);
        setShortSellingSeriesResult(seriesResult);
      } catch {
        if (searchRequestIdRef.current !== requestId) return;
        setShortSellingResult(null);
        setShortSellingSeriesResult(null);
      }
    })();

    const industryIndexTask = (async () => {
      setIndustryChartLoading(true);
      try {
        const result = await fetchFeature2IndustryIndex(
          resolvedStockCode,
          INITIAL_INDUSTRY_FREQ,
          INITIAL_INDUSTRY_WINDOW,
        );
        setIndustryIndexResult(result);
      } catch {
        setIndustryIndexResult(null);
      } finally {
        setIndustryChartLoading(false);
      }
    })();

    const relatedStocksTask = (async () => {
      try {
        const result = await fetchFeature2RelatedStocks(
          resolvedStockCode,
          INITIAL_RELATED_STOCK_LIMIT,
        );
        setRelatedStocks(mapRelatedStocks(result.data ?? []));
      } catch {
        setRelatedError("유사 종목을 불러오지 못했습니다.");
      } finally {
        setRelatedLoading(false);
      }
    })();

    const investorFlowTask = (async () => {
      try {
        const result = await fetchFeature2InvestorFlow(resolvedStockCode, 60);
        if (searchRequestIdRef.current !== requestId) return;
        setInvestorFlowResult(result);
      } catch {
        if (searchRequestIdRef.current !== requestId) return;
        setInvestorFlowResult(null);
        setInvestorFlowError("수급 데이터를 불러오지 못했습니다.");
      } finally {
        if (searchRequestIdRef.current === requestId) {
          setInvestorFlowLoading(false);
        }
      }
    })();

    await Promise.all([
      loadCandles(resolvedStockCode),
      newsTask,
      baseRateTask,
      macroRatesTask,
      shortSellingTask,
      industryIndexTask,
      relatedStocksTask,
      investorFlowTask,
    ]);
  };

  const [newsItems, setNewsItems] = useState<NewsItemDto[]>([]);
  const [newsLoading, setNewsLoading] = useState(false);
  const [newsError, setNewsError] = useState<string | null>(null);

  const [hoveredDayKey, setHoveredDayKey] = useState<string | null>(null);
  const [relatedStocks, setRelatedStocks] = useState<RelatedStockDisplay[]>([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [relatedError, setRelatedError] = useState<string | null>(null);
  const baseRateMetrics = analysisData?.metrics?.baseRate ?? baseRateResult?.data ?? null;
  const macroRates = macroRatesResult?.data ?? null;
  const macroTrendSeries = macroRatesSeriesResult?.data?.series ?? [];
  const shortSellingMetrics = analysisData?.metrics?.shortSelling ?? shortSellingResult?.data ?? null;
  const displayIndustryIndex = analysisData?.metrics?.industryIndex ?? industryIndexResult?.data ?? null;
  const investorFlow = investorFlowResult?.data ?? null;

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

  useEffect(() => {
    const handleClickOutsideTopic = (e: MouseEvent) => {
      if (!topicRef.current) return;
      if (topicRef.current.contains(e.target as Node)) return;
      setIsTopicOpen(false);
    };

    document.addEventListener("click", handleClickOutsideTopic);
    return () => document.removeEventListener("click", handleClickOutsideTopic);
  }, []);

  useEffect(() => {
    const fullText = feature2Explain?.overall?.summary ?? feature2Explain?.text;
    if (!fullText) {
      setDisplayText("");
      return;
    }

    setDisplayText("");
    let index = 0;
    const speed = 20;

    const timer = setInterval(() => {
      index += 1;
      setDisplayText((prev) => prev + fullText.charAt(index - 1));
      if (index >= fullText.length) clearInterval(timer);
    }, speed);

    return () => clearInterval(timer);
  }, [feature2Explain]);

  useEffect(() => {
    if (loading || industryChartLoading) {
      setIndustryChartError(null);
      return;
    }

    if (!displayIndustryIndex && !industryIndexResult && !analysisResult) {
      setIndustryChartError(null);
      return;
    }

    const industryIndex = displayIndustryIndex;
    const warnings: string[] = analysisResult?.meta?.warnings
      ?? industryIndexResult?.meta?.warnings
      ?? [];

    if (!industryIndex) {
      if (warnings.includes("INDUSTRY_INDEX_OHLCV_EMPTY")) {
        setIndustryChartError("산업 지수 데이터가 없습니다.");
      } else {
        setIndustryChartError("산업 지수 차트를 불러오지 못했습니다.");
      }
      return;
    }

    if (!industryIndex.series || industryIndex.series.length === 0) {
      setIndustryChartError("산업 지수 데이터가 없습니다.");
      return;
    }

    setIndustryChartError(null);
  }, [analysisResult, industryIndexResult, loading, industryChartLoading, displayIndustryIndex]);

  const handleAnalyzeClick = async () => {
    if (!isLoggedIn()) {
      sessionStorage.setItem("qaima_redirect", window.location.pathname + window.location.search);
      navigate("/login");
      return;
    }
    setLoading(true);
    setErr("");
    setAnalysisResult(null);
    setDisplayText("");

    try {
      setShowAnalyzeButton(false);
      const [result, shortSellingSeries, baseRateSeries] = await Promise.all([
        fetchFeature2Analysis(mainStock.symbol, selectedFreq, selectedWindow, 30, undefined, llmVendor),
        fetchFeature2ShortSellingSeries(mainStock.symbol, selectedWindow),
        fetchFeature2BaseRateSeries(Math.max(selectedWindow, 365)),
      ]);
      const industryDisplayDayKeys = collectSeriesDayKeys(result?.data?.metrics?.industryIndex?.series);
      await loadCandlesForIndustrySeries(
        mainStock.symbol,
        industryDisplayDayKeys,
        selectedWindow,
      );
      setAnalysisResult(result);
      setShortSellingSeriesResult(shortSellingSeries);
      setBaseRateSeriesResult(baseRateSeries);

      // 분석 응답의 뉴스 데이터로 갱신
      const analyzeNews = result?.data?.metrics?.newsList;
      if (analyzeNews && analyzeNews.length > 0) {
        setNewsItems(analyzeNews);
      }

    } catch {
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!mainStock.symbol) return;
    loadCandles(mainStock.symbol);
    // loadCandles가 mainStock 자체를 업데이트하므로 symbol만 의존성으로 둔다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainStock.symbol]);

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[60px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center justify-between gap-3">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            외부요인
          </h1>
          <TokenBalanceBadge />
        </header>

        <section className="w-full flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="w-full lg:max-w-md bg-white rounded-[10px] outline outline-1 outline-stone-300 px-2 py-1 sm:px-2 sm:py-1 flex flex-col gap-2">
            <StockInputBox
              placeholder="종목을 입력해주세요"
              onSearch={handleSearch}
            />
          </div>

          <div
            ref={featuredListWrapperRef}
            className="w-full lg:flex-1 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-end"
          >
            <div ref={topicRef} className="relative w-40">
              <button
                onClick={() => setIsTopicOpen((prev) => !prev)}
                className="w-full h-9 sm:h-10 border border-black rounded-md bg-white flex items-center px-3 font-medium relative"
              >
                <span className="flex-1 text-center truncate whitespace-nowrap text-xs sm:text-sm">
                  {topic}
                </span>
                <span className="absolute right-1 sm:right-2 text-[10px] sm:text-xs">
                  {isTopicOpen ? "▲" : "▼"}
                </span>
              </button>

              {isTopicOpen && (
                <div className="absolute mt-1 w-full bg-white border border-stone-300 rounded-md shadow-md z-50">
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
                      className={`w-full text-left px-3 py-2 text-sm sm:text-base hover:bg-zinc-100 ${
                        topic === item ? "bg-zinc-100 font-semibold" : ""
                      }`}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="flex items-center gap-2 relative">
              <div
                ref={cardRef}
                className="inline-block w-[260px] sm:w-[280px] lg:w-[380px]"
              >
                {featuredStocks[0] && (
                  <StockCard
                    name={featuredStocks[0].name}
                    price={featuredStocks[0].price}
                    volume={featuredStocks[0].volume}
                    change={featuredStocks[0].change}
                    changeRate={featuredStocks[0].changeRate}
                    getColorClass={getColorClass}
                  />
                )}
              </div>

              <button
                onClick={() => {
                  setIsOpen((prev) => !prev);
                  if (!cardRef.current) return;
                  const rect = cardRef.current.getBoundingClientRect();
                  setPanelPos({ top: rect.top, left: rect.left });
                }}
                className="w-6 h-6 bg-zinc-300 rounded-full flex items-center justify-center transition-colors hover:bg-zinc-400 flex-shrink-0"
              >
                <span className="text-lg font-bold leading-none">
                  {isOpen ? "-" : "+"}
                </span>
              </button>
            </div>
          </div>
        </section>

        {hasSelectedStock && (<>
        {(() => {
          const mainNumericChange =
            mainStock.change !== null && Number.isFinite(mainStock.change)
              ? mainStock.change
              : 0;
          const displayPrice =
            mainStock.price !== null && Number.isFinite(mainStock.price)
              ? formatPrice(mainStock.price)
              : "-";
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
        <main className="w-full mt-6 flex flex-col xl:flex-row justify-center items-start gap-6">
          <div className="flex-1 flex flex-col gap-5">
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <div className="flex flex-wrap items-end gap-1.5">
                  <h2 className="text-lg sm:text-xl md:text-2xl font-medium text-black">
                    {mainStock.name}
                  </h2>
                  <span className="text-sm sm:text-base md:text-lg text-black">
                    ({mainStock.symbol})
                  </span>
                  <button
                    onClick={toggleInterest}
                    className="ml-2 inline-block"
                  >
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

              <div className="w-full h-80 sm:h-[420px] bg-white rounded-xl overflow-hidden">
                {chartLoading && (
                  <div className="h-full flex items-center justify-center">
                    <p className="text-md text-gray-500">
                      차트를 불러오는 중입니다…
                    </p>
                  </div>
                )}

                {chartError && !chartLoading && (
                  <div className="h-full flex items-center justify-center">
                    <p className="text-md text-red-500">
                      {chartError ?? "차트를 불러오지 못했습니다."}
                    </p>
                  </div>
                )}

                {!chartLoading && !chartError && (
                  <TradingViewWidget
                  candles={candles}
                  showSubPanes={false}
                  onRequestMoreHistory={handleRequestMoreHistory}
                  hoveredDayKey={hoveredDayKey}
                  onHoverDayKeyChange={setHoveredDayKey}
                  />
                )}
              </div>
            </section>

            {/* [좌측 하단] 산업 지수 차트 카드 */}
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-2.5 overflow-hidden">
              <h2 className="text-black text-lg sm:text-2xl font-medium">
                {mainStock.name}({mainStock.symbol}) 관련 <DictTerm term="산업 지수">산업 지수</DictTerm>
              </h2>

              <div className="w-full h-80 sm:h-[420px] bg-white rounded-xl overflow-hidden">
              {industryChartLoading && (
                <div className="h-full flex items-center justify-center">
                  <p className="text-md text-gray-500">차트를 불러오는 중입니다…</p>
                </div>
              )}

              {industryChartError && !industryChartLoading && (
                <div className="h-full flex items-center justify-center">
                  <p className="text-md text-red-500">
                    {industryChartError ?? "차트를 불러오지 못했습니다."}
                  </p>
                </div>
              )}

              {!industryChartLoading && !industryChartError && industrySeries.length > 0 && (
              <RelativeLineWidget
              data={industrySeries}
              height={420}
              overlayAnchor={analysisData?.metrics?.peerCluster?.anchorSeries ?? null}
              overlayCentroid={
                analysisData?.metrics?.peerCluster?.peerCentroid
                ?? analysisData?.metrics?.peerCluster?.centroid
                ?? null
              }
              overlayBand={
                analysisData?.metrics?.peerCluster?.peerBand
                ?? analysisData?.metrics?.peerCluster?.band
                ?? null
              }
              overlayCoverage={analysisData?.metrics?.peerCluster?.peerCoverage ?? null}
              overlayPeers={analysisData?.metrics?.peerCluster?.peers ?? null}
              showPeerOverlay={Boolean(analysisData?.metrics?.peerCluster)}
              hoveredDayKey={hoveredDayKey}
              onHoverDayKeyChange={setHoveredDayKey}
              />
            )}

            {!industryChartLoading && !industryChartError && industrySeries.length === 0 && (
              <div className="h-full flex items-center justify-center">
                <p className="text-md text-gray-500">산업 지수 데이터가 없습니다.</p>
              </div>
            )}
            </div>
            </section>
          </div>

          <div className="w-full xl:w-[440px] 2xl:w-[480px] flex flex-col items-stretch">
            <Feature2ExternalFactorPanel
              stockName={mainStock.name}
              baseRateMetrics={baseRateMetrics}
              macroRates={macroRates}
              macroRatesLoading={macroRatesLoading}
              macroRatesError={macroRatesError}
              macroTrendSeries={macroTrendSeries}
              shortSellingSeries={shortSellingSeriesResult?.data ?? []}
              newsItems={newsItems}
              newsLoading={newsLoading}
              newsError={newsError}
              relatedStocks={relatedStocks}
              relatedLoading={relatedLoading}
              relatedError={relatedError}
              shortSellingMetrics={shortSellingMetrics}
              investorFlow={investorFlow}
              investorFlowLoading={investorFlowLoading}
              investorFlowError={investorFlowError}
              onSelectRelatedStock={handleSearch}
            />
          </div>
        </main>
          );
        })()}

        {/* 분석 옵션 라디오버튼 */}
        <div className="w-full bg-white rounded-2xl border border-stone-300 px-4 sm:px-6 py-4 flex flex-col gap-4">
          <h3 className="text-sm sm:text-base font-semibold text-zinc-800">분석 옵션</h3>

          <div className="flex flex-col sm:flex-row gap-4 sm:gap-8">
            {/* 주기 선택 */}
            <div className="flex flex-col gap-2">
              <span className="text-xs sm:text-sm font-medium text-zinc-500">데이터 주기</span>
              <div className="flex gap-3">
                {([["ONE_D", "1일"], ["ONE_W", "1주"]] as const).map(([value, label]) => (
                  <label
                    key={value}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border cursor-pointer transition-colors text-sm ${
                      selectedFreq === value
                        ? "border-sky-500 bg-sky-50 text-sky-700 font-medium"
                        : "border-zinc-200 bg-zinc-50 text-zinc-600 hover:bg-zinc-100"
                    }`}
                  >
                    <input
                      type="radio"
                      name="freq"
                      value={value}
                      checked={selectedFreq === value}
                      onChange={() => setSelectedFreq(value)}
                      className="accent-sky-600 w-3.5 h-3.5"
                    />
                    {label}
                  </label>
                ))}
              </div>
            </div>

            {/* 기간 선택 */}
            <div className="flex flex-col gap-2">
              <span className="text-xs sm:text-sm font-medium text-zinc-500">분석 기간</span>
              <div className="flex gap-2 flex-wrap">
                {([
                  [60, "3개월"],
                  [120, "6개월"],
                  [180, "9개월"],
                  [252, "1년"],
                ] as const).map(([value, label]) => (
                  <label
                    key={value}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border cursor-pointer transition-colors text-sm ${
                      selectedWindow === value
                        ? "border-sky-500 bg-sky-50 text-sky-700 font-medium"
                        : "border-zinc-200 bg-zinc-50 text-zinc-600 hover:bg-zinc-100"
                    }`}
                  >
                    <input
                      type="radio"
                      name="window"
                      value={value}
                      checked={selectedWindow === value}
                      onChange={() => setSelectedWindow(value)}
                      className="accent-sky-600 w-3.5 h-3.5"
                    />
                    {label}
                  </label>
                ))}
              </div>
            </div>
          </div>
        </div>

        <AnalysisResultPanel
          result={
            analysisResult
              ? {
                  explain: feature2Explain,
                  warnings: analysisResult?.meta?.warnings ?? null,
                  metrics: {
                    stock: analysisData?.metrics?.stock ?? null,
                    peerCluster: analysisData?.metrics?.peerCluster ?? null,
                    shortSelling: analysisData?.metrics?.shortSelling ?? null,
                    shortSellingTrendSummary: analysisData?.metrics?.shortSellingTrendSummary ?? null,
                    shortSellingSeries: shortSellingSeriesResult?.data ?? null,
                    baseRate: analysisData?.metrics?.baseRate ?? null,
                    baseRateTrendSummary: analysisData?.metrics?.baseRateTrendSummary ?? null,
                    baseRateSeries: baseRateSeriesResult?.data ?? null,
                    newsSentimentSummary: analysisData?.metrics?.newsSentimentSummary ?? null,
                    newsList: analysisData?.metrics?.newsList ?? null,
                  },
                  meta: analysisResult?.meta,
                }
              : null
          }
          loading={loading}
          err={err}
          showAnalyzeButton={showAnalyzeButton}
          onAnalyze={handleAnalyzeClick}
          onDownload={() => {}}
          onZoom={() => {}}
          llmVendor={llmVendor}
          onLlmVendorChange={setLlmVendor}
          displayText={displayText}
          layout="full"
        />
        </>)}

        {isOpen && panelPos && (
          <div
            ref={dropdownRef}
            className="fixed w-[260px] sm:w-[280px] lg:w-[380px] max-h-[400px] bg-white border border-stone-300 rounded-sm shadow-md overflow-y-auto overflow-x-hidden z-50"
            style={{ top: panelPos.top, left: panelPos.left }}
          >
            <div className="pr-3">
              {featuredStocks.map((stock, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setIsOpen(false);
                    handleSearch(stock.symbol);
                  }}
                  className="w-full text-left"
                >
                  <StockCard
                    name={stock.name}
                    price={stock.price}
                    volume={stock.volume}
                    change={stock.change}
                    changeRate={stock.changeRate}
                    getColorClass={getColorClass}
                  />
                </button>
              ))}
            </div>
          </div>
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
