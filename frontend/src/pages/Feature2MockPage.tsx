// Feature2MockPage.tsx
import { useState, useRef, useEffect, useMemo } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Star, Sun, Moon } from "lucide-react";
import InvestLevelBadge from "../components/InvestLevelBadge";
import { useTheme } from "../hooks/useTheme";
import StockSearchBar from "../components/StockSearchBar";
import FeatureIntro from "../components/FeatureIntro";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import TradingViewWidget from "../components/TradingViewWidget";
import Feature2ExternalFactorPanel from "../components/Feature2ExternalFactorPanel";
import { clientLog } from "../utils/clientLog";
import { PdfExportContext } from "../contexts/PdfExportContext";
import qaimaLogo from "../assets/qaima-final.png";

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
import {
  fetchWatchlist,
  addWatchlistItem,
  deleteWatchlistItem,
} from "../api/watchlist";
import { isLoggedIn } from "../utils/auth";
import { getApiErrorMessage } from "../utils/errorMessage";
import DictTerm from "../components/DictTerm";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { formatKstOffsetDateTime, shiftKstDays, shiftKstMonths } from "../utils/kst";
import { refreshTokenBalance } from "../api/billingStore";
import { useDictionary } from "../components/DictContext";
import { downloadElementAsPdf, waitForPdfCaptureReady } from "../utils/reportPdf";
import useReportUserName from "../hooks/useReportUserName";

const getColorClassByNumber = (n: number | null) => {
  if (n === null || !Number.isFinite(n)) return "text-flat";
  if (n > 0) return "text-rise";
  if (n < 0) return "text-fall";
  return "text-flat";
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

const ANALYSIS_WINDOW_MONTHS: Record<60 | 120 | 180 | 252, number> = {
  60: 3,
  120: 6,
  180: 9,
  252: 12,
};

const buildAnalysisDateRange = (window: 60 | 120 | 180 | 252) => {
  const to = new Date();
  const from = shiftKstMonths(to, -ANALYSIS_WINDOW_MONTHS[window]);
  return {
    from: formatKstOffsetDateTime(from),
    to: formatKstOffsetDateTime(to),
  };
};

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

// 미사용 - 팀원이 추가했으나 현재 코드에서 참조되지 않아 주석 처리
// interface FeaturedStock {
//   name: string;
//   symbol: string;
//   price: string;
//   volume: string;
//   change: string;
//   changeRate: string;
// }

type Feature2PanelExplain = NonNullable<AnalysisPanelResult["explain"]>;

const parseFeature2Explain = (explain?: Feature2AnalyzeResponse["explain"] | null): Feature2PanelExplain | null => {
  if (!explain) return null;
  const sections = explain.sections ?? {};
  const overall = explain.overall ?? {};
  return {
    provider: explain.provider ?? null,
    model: explain.model ?? null,
    text: overall.summary || explain.text || null,
    sections: {
      peerCluster: sections.peerCluster ?? null,
      macroEnvironment: sections.macroEnvironment ?? null,
      investorFlow: sections.investorFlow ?? null,
      crossSignal: sections.crossSignal ?? null,
      newsSentiment: sections.newsSentiment ?? null,
      shortSelling: sections.shortSelling ?? null,
    },
    overall,
  };
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
  const { theme, toggle } = useTheme();
  const { t } = useTranslation("feature2Page");
  const { investLevel } = useDictionary();
  const reportUserName = useReportUserName();
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
      setChartError(t("chart.noData"));
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
      const toDate = new Date(toIso);
      const absoluteMin = shiftKstDays(
        Number.isNaN(toDate.getTime()) ? new Date() : toDate,
        -MAX_HISTORY_DAYS,
      );

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
        formatKstOffsetDateTime(absoluteMin),
        allowedDayKeys,
      );
    } catch (e: unknown) {
      clientLog.error("Chart data fetch failed", e);
      setChartError(t("chart.loadFailed"));
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
      clientLog.error("Additional candle load failed", e);
    } finally {
      isLoadingMoreRef.current = false;
    }
  };

  const [industryChartLoading, setIndustryChartLoading] = useState(false);
  const [industryChartError, setIndustryChartError] = useState<string | null>(null);

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
  const [pdfExporting, setPdfExporting] = useState(false);
  const [isAnalysisModalOpen, setIsAnalysisModalOpen] = useState(false);
  const pdfRef = useRef<HTMLDivElement | null>(null);
  const llmVendor = "GPT-5 mini";
  const [selectedFreq, setSelectedFreq] = useState<"ONE_D" | "ONE_W">("ONE_D");
  const [selectedWindow, setSelectedWindow] = useState<60 | 120 | 180 | 252>(120);
  const feature2Explain = useMemo(
    () => parseFeature2Explain(analysisData?.explain),
    [analysisData?.explain],
  );
  const analysisPanelResult = useMemo<AnalysisPanelResult | null>(() => {
    if (!analysisResult) return null;

    return {
      explain: feature2Explain,
      warnings: analysisResult.meta?.warnings ?? null,
      metrics: {
        stock: analysisData?.metrics?.stock ?? null,
        peerCluster: analysisData?.metrics?.peerCluster ?? null,
        shortSelling: analysisData?.metrics?.shortSelling ?? null,
        shortSellingTrendSummary: analysisData?.metrics?.shortSellingTrendSummary ?? null,
        shortSellingSeries: shortSellingSeriesResult?.data ?? null,
        baseRate: analysisData?.metrics?.baseRate ?? null,
        macroRates: analysisData?.metrics?.macroRates ?? macroRatesResult?.data ?? null,
        macroRatesSeries: analysisData?.metrics?.macroRatesSeries ?? macroRatesSeriesResult?.data ?? null,
        investorFlow: analysisData?.metrics?.investorFlow ?? investorFlowResult?.data ?? null,
        baseRateTrendSummary: analysisData?.metrics?.baseRateTrendSummary ?? null,
        baseRateSeries: baseRateSeriesResult?.data ?? null,
        newsSentimentSummary: analysisData?.metrics?.newsSentimentSummary ?? null,
        newsList: analysisData?.metrics?.newsList ?? null,
      },
      meta: analysisResult.meta,
    };
  }, [
    analysisResult,
    feature2Explain,
    analysisData?.metrics,
    shortSellingSeriesResult?.data,
    macroRatesResult?.data,
    macroRatesSeriesResult?.data,
    investorFlowResult?.data,
    baseRateSeriesResult?.data,
  ]);

  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  const [mainStock, setMainStock] = useState<MainStockState>({
    name: "",
    symbol: "",
    price: null,
    change: null,
    changeRate: null,
  });
  const reportMeta = analysisPanelResult
    ? {
        featureType: "FEATURE2" as const,
        subjectLabel: `${mainStock.name || analysisPanelResult.metrics?.stock?.companyName || t("errors.subjectFallback")} (${mainStock.symbol || analysisPanelResult.metrics?.stock?.stockCode || "-"})`,
        generatedAt: analysisResult?.meta?.timestamp ?? null,
        analysisModel: llmVendor,
        investLevel,
        userName: reportUserName,
        analysisWindow: t("analysisWindow.label", { n: selectedWindow }),
        dataAsOf:
          analysisPanelResult.metrics?.newsSentimentSummary?.summaryDate
          ?? analysisPanelResult.metrics?.shortSelling?.reportDate
          ?? null,
      }
    : null;

  const currentTime = useKSTTime();

  const [isInterested, setIsInterested] = useState(false);
  const [currentStockId, setCurrentStockId] = useState<number | null>(null);
  const [watchlistItemId, setWatchlistItemId] = useState<number | null>(null);
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
        setMacroRatesError(t("macroError.missing"));
      }
    } catch (error) {
      if (requestId != null && searchRequestIdRef.current !== requestId) return;
      clientLog.error("Macro rates fetch failed", error);
      setMacroRatesResult(null);
      setMacroRatesSeriesResult(null);
      setMacroRatesError(t("macroError.fetchFailed"));
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


  // 검색한 종목이 관심종목(워치리스트)에 들어있는지 동기화
  const syncWatchlistMembership = async (stockId: number | null) => {
    setIsInterested(false);
    setWatchlistItemId(null);
    if (!stockId) return;
    // 비로그인 시 워치리스트 조회(인증 필요)를 호출하지 않는다.
    // 호출하면 401 → apiClient 인터셉터가 강제로 /login 으로 이동시켜
    // "종목 검색만 해도 로그인 창으로 튕기는" 문제가 발생한다. (분석은 별도로 로그인 요구)
    if (!isLoggedIn()) return;
    try {
      const items = await fetchWatchlist();
      const hit = items.find((it) => it.stockId === stockId);
      if (hit) {
        setIsInterested(true);
        setWatchlistItemId(hit.watchlistItemId);
      }
    } catch {
      // 워치리스트 미존재/권한 등은 조용히 무시
    }
  };

  const toggleInterest = async () => {
    // 관심종목은 로그인 사용자 기준. 비로그인 시 강제 리다이렉트 대신 안내.
    if (!isLoggedIn()) {
      setToast({ message: t("watchlist.loginRequired"), visible: true });
      return;
    }
    // 이미 등록됨 → 삭제
    if (isInterested && watchlistItemId != null) {
      try {
        await deleteWatchlistItem(watchlistItemId);
        setIsInterested(false);
        setWatchlistItemId(null);
        setToast({ message: t("watchlist.removed"), visible: true });
      } catch (e) {
        setToast({
          message: getApiErrorMessage(e, t("watchlist.removeFailed")),
          visible: true,
        });
      }
      return;
    }

    // 미등록 → 추가
    if (!currentStockId) {
      setToast({
        message: t("watchlist.stockNotLoaded"),
        visible: true,
      });
      return;
    }
    try {
      const created = await addWatchlistItem({
        stockId: currentStockId,
      });
      setIsInterested(true);
      setWatchlistItemId(created.watchlistItemId);
      setToast({ message: t("watchlist.added"), visible: true });
    } catch (e) {
      setToast({
        message: getApiErrorMessage(e, t("watchlist.addFailed")),
        visible: true,
      });
    }
  };

  const industrySeries = useMemo(() => {
  const raw = (analysisData?.metrics?.industryIndex ?? industryIndexResult?.data ?? null)?.series;

  clientLog.warn("Raw industry index series mapped", raw);

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

  clientLog.warn("Mapped industry series", mapped);

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
    let resolvedStockId: number | null = null;

    // 종목명 조회
    try {
      const stockInfo = await getStockByCode(q);
      if (stockInfo) {
        resolvedStockCode = stockInfo.stockCode || q;
        resolvedStockId = stockInfo.stockId ?? null;
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

    setCurrentStockId(resolvedStockId);
    void syncWatchlistMembership(resolvedStockId);

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
        setNewsError(t("news.loadFailed"));
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
        setRelatedError(t("related.loadFailed"));
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
        setInvestorFlowError(t("investorFlow.loadFailed"));
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

  /* 미사용 — 팀원이 추가했으나 참조하는 ref(featuredListWrapperRef/dropdownRef/topicRef)와 상태(setIsOpen/setIsTopicOpen)가 정의되지 않은 잔재
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
  */

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
        setIndustryChartError(t("industry.noData"));
      } else {
        setIndustryChartError(t("industry.loadFailed"));
      }
      return;
    }

    if (!industryIndex.series || industryIndex.series.length === 0) {
      setIndustryChartError(t("industry.noData"));
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
      const analysisDateRange = buildAnalysisDateRange(selectedWindow);
      const [result, shortSellingSeries, baseRateSeries] = await Promise.all([
        fetchFeature2Analysis(
          mainStock.symbol,
          selectedFreq,
          selectedWindow,
          30,
          undefined,
          llmVendor,
          analysisDateRange.from,
          analysisDateRange.to,
          investLevel,
        ),
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

    } catch (e) {
      setErr(getApiErrorMessage(e, t("errors.analyzeFailed")));
    } finally {
      // 성공/실패 무관하게 서버 잔액과 동기화 (백엔드가 실패 시 환불 처리하므로
      // 환불된 잔액이 UI 에 즉시 반영되도록).
      refreshTokenBalance().catch(() => {});
      setLoading(false);
    }
  };

  const handleDownloadClick = async () => {
    if (!analysisResult || !pdfRef.current) return;
    const reportElement =
      pdfRef.current.querySelector<HTMLElement>(".qaima-report-enter") ?? pdfRef.current;

    setPdfExporting(true);
    try {
      await waitForPdfCaptureReady();
      await downloadElementAsPdf(
        reportElement,
        `${mainStock.symbol || "feature2"}_external_analysis.pdf`,
        qaimaLogo,
      );
    } catch (e) {
      clientLog.error("Feature2 PDF generation failed", e);
    } finally {
      setPdfExporting(false);
    }
  };

  useEffect(() => {
    if (!mainStock.symbol) return;
    loadCandles(mainStock.symbol);
    // loadCandles가 mainStock 자체를 업데이트하므로 symbol만 의존성으로 둔다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainStock.symbol]);

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      <div className="qaima-stagger max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="flex items-center justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Equities · External Factors
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              {t("header.title")}
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? t("header.lightMode") : t("header.darkMode")}
              className="w-9 h-9 grid place-items-center rounded-xl bg-surface
                         border border-line text-ink-2 shadow-card
                         hover:bg-bg-sunk transition-colors"
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <TokenBalanceBadge />
          </div>
        </header>

        <div className="relative z-30">
          <StockSearchBar onSearch={handleSearch} />
        </div>

        {!hasSelectedStock && (
          <div className="relative z-0 mt-10 sm:mt-20">
            <FeatureIntro variant="external" />
          </div>
        )}

        {hasSelectedStock && (<>
          <div className="border-t border-line-strong" />
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
        <main className="w-full flex flex-col xl:flex-row justify-center xl:items-stretch gap-6">
          <div className="flex-1 flex flex-col gap-5">
            <section className="w-full bg-surface rounded-2xl border border-line shadow-card p-5 flex flex-col gap-4">
              <div className="flex items-start justify-between gap-4">
                <div className="flex flex-col gap-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <h2 className="text-lg sm:text-xl md:text-2xl font-semibold text-ink tracking-tight">
                      {mainStock.name}
                    </h2>
                    <span className="text-sm sm:text-base md:text-lg text-ink-3 font-mono">
                      {mainStock.symbol}
                    </span>
                    <button
                      onClick={toggleInterest}
                      className="ml-1 inline-block"
                    >
                      <Star
                        size={20}
                        className={`transition-colors text-warn`}
                        fill={isInterested ? "currentColor" : "none"}
                      />
                    </button>
                  </div>
                  <span className="text-[10px] sm:text-xs font-medium text-ink-3">
                    {currentTime} KST
                  </span>
                </div>

                <div className="flex flex-col items-end gap-0.5 flex-shrink-0">
                  <div className="flex items-baseline gap-1">
                    <span className="text-2xl md:text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {displayPrice}
                    </span>
                    <span className="text-sm text-ink-3">{t("price.won")}</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-sm font-medium font-mono tabular">
                    <span className="text-ink-3 text-xs">{t("price.prevDayDiff")}</span>
                    <span className={mainColorClass}>{displayChange}</span>
                    <span className={mainColorClass}>({displayRate})</span>
                    {mainNumericChange > 0 && (
                      <div
                        className={`${mainColorClass} w-0 h-0
                        border-l-[5px] border-r-[5px]
                        border-b-[8px] border-transparent
                        border-b-current`}
                      />
                    )}
                    {mainNumericChange < 0 && (
                      <div
                        className={`${mainColorClass} w-0 h-0
                        border-l-[5px] border-r-[5px]
                        border-t-[8px] border-transparent
                        border-t-current`}
                      />
                    )}
                    {mainNumericChange === 0 && (
                      <span className={`${mainColorClass} text-lg font-extrabold leading-none`}>-</span>
                    )}
                  </div>
                </div>
              </div>

              {/* 헤더와 차트 사이 divider */}
              <div className="h-px bg-line" />

              {/* 차트 영역 — sunken 제거, 카드 안 surface 위에 차트 */}
              <div className="w-full h-96 sm:h-[480px] flex items-stretch overflow-hidden">
                {chartLoading && (
                  <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                    <p className="text-sm sm:text-base text-ink-3">
                      {t("chart.loading")}
                    </p>
                  </div>
                )}

                {chartError && !chartLoading && (
                  <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                    <p className="text-sm sm:text-base text-danger">
                      {chartError ?? t("chart.loadFailed")}
                    </p>
                  </div>
                )}

                {!chartLoading && !chartError && (
                  <div className="flex-1 min-h-0 w-full">
                    <TradingViewWidget
                      candles={candles}
                      showSubPanes={false}
                      onRequestMoreHistory={handleRequestMoreHistory}
                      hoveredDayKey={hoveredDayKey}
                      onHoverDayKeyChange={setHoveredDayKey}
                    />
                  </div>
                )}
              </div>
            </section>

            {/* [좌측 하단] 산업 지수 차트 카드 */}
            <section className="w-full bg-surface rounded-2xl border border-line shadow-card p-5 flex flex-col gap-4">
              <h2 className="text-ink text-lg sm:text-2xl font-semibold tracking-tight">
                {mainStock.name} {t("industry.titlePrefix")} <DictTerm term="산업 지수">산업 지수</DictTerm>
              </h2>

              {/* 헤더와 차트 사이 divider */}
              <div className="h-px bg-line" />

              <div className="w-full h-80 sm:h-[420px] flex items-stretch overflow-hidden">
              {industryChartLoading && (
                <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                  <p className="text-sm sm:text-base text-ink-3">{t("chart.loading")}</p>
                </div>
              )}

              {industryChartError && !industryChartLoading && (
                <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                  <p className="text-sm sm:text-base text-danger">
                    {industryChartError ?? t("chart.loadFailed")}
                  </p>
                </div>
              )}

              {!industryChartLoading && !industryChartError && industrySeries.length > 0 && (
              <div className="flex-1 min-h-0 w-full">
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
              </div>
            )}

            {!industryChartLoading && !industryChartError && industrySeries.length === 0 && (
              <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                <p className="text-sm sm:text-base text-ink-3">{t("industry.noData")}</p>
              </div>
            )}
            </div>
            </section>
          </div>

          <div className="w-full xl:w-[440px] 2xl:w-[480px] flex flex-col">
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
              peerCluster={analysisData?.metrics?.peerCluster ?? null}
              onSelectRelatedStock={handleSearch}
            />
          </div>
        </main>
          );
        })()}

        {/* 분석 기간 */}
        <div className="w-full bg-surface rounded-2xl border border-line px-4 sm:px-6 py-3 flex flex-wrap items-center gap-x-4 gap-y-3 shadow-card">
          <h3 className="text-sm sm:text-base font-semibold text-ink shrink-0">{t("analysisPeriod.title")}</h3>

          <div className="flex flex-wrap gap-1.5">
            {([
              [t("analysisPeriod.preset3Months"), 60],
              [t("analysisPeriod.preset6Months"), 120],
              [t("analysisPeriod.preset9Months"), 180],
              [t("analysisPeriod.preset1Year"), 252],
            ] as const).map(([label, window]) => (
              <button
                key={label}
                onClick={() => setSelectedWindow(window)}
                className={`px-3.5 py-1.5 rounded-full text-sm transition-colors ${
                  selectedWindow === window
                    ? "bg-accent text-white font-semibold"
                    : "bg-bg-sunk text-ink-3 hover:bg-surface-2 font-medium"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2 text-sm ml-auto">
            <span className="text-xs text-ink-3 shrink-0">{t("analysisPeriod.freqLabel")}</span>
            {([["ONE_D", t("analysisPeriod.freqDay")]] as const).map(([value, label]) => (
              <button
                key={value}
                onClick={() => setSelectedFreq(value)}
                className={`px-3 py-1 rounded-full text-sm transition-colors ${
                  selectedFreq === value
                    ? "bg-accent text-white font-semibold"
                    : "bg-bg-sunk text-ink-3 hover:bg-surface-2 font-medium"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* 분석 결과 보기 카드 */}
        {showAnalyzeButton && (
          <section className="rounded-2xl border border-line shadow-card p-5 sm:p-6
                              bg-gradient-to-br from-accent-soft to-surface
                              flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="text-[11px] font-semibold text-accent tracking-tight mb-1">
                {t("runCard.tag")}
              </div>
              <h3 className="text-lg font-bold text-ink tracking-tight">
                {t("runCard.title")}
              </h3>
              <p className="text-sm text-ink-3 mt-1">
                {t("runCard.subtitle")}
              </p>
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center gap-3 flex-shrink-0">
              <InvestLevelBadge />
              <div className="flex items-center gap-3">
                <button
                onClick={handleAnalyzeClick}
                disabled={loading}
                className="px-5 py-2.5 rounded-xl bg-accent text-white font-semibold text-sm
                           hover:opacity-90 disabled:opacity-50 transition-opacity tracking-tight"
              >
                {t("runCard.viewButton")}
                </button>
              </div>
            </div>
          </section>
        )}

        {!analysisResult && !loading && !err && (
          <div className="w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center py-24 bg-bg-sunk border-line text-ink-4">
            <p className="text-base font-medium">{t("emptyResult.title")}</p>
            <p className="text-sm mt-1">{t("emptyResult.subtitle")}</p>
          </div>
        )}

        {(loading || !!err || !!analysisResult) && (
          <div ref={pdfRef}>
            <PdfExportContext.Provider value={pdfExporting}>
              <AnalysisResultPanel
                result={analysisPanelResult}
                loading={loading}
                err={err}
                showAnalyzeButton={false}
                onAnalyze={handleAnalyzeClick}
                onDownload={handleDownloadClick}
                onZoom={() => setIsAnalysisModalOpen(true)}
                displayText={displayText}
                layout="full"
                reportMeta={reportMeta}
              />
            </PdfExportContext.Provider>
          </div>
        )}
        </>)}

        {isAnalysisModalOpen && analysisResult && createPortal(
          <div
            className="fixed inset-0 z-[100] bg-ink/50 flex items-center justify-center p-4"
            onClick={() => setIsAnalysisModalOpen(false)}
          >
            <div
              className="bg-surface rounded-xl w-full max-w-5xl max-h-[90vh] flex flex-col shadow-pop"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between px-5 py-3 border-b border-line">
                <h2 className="text-base sm:text-lg font-semibold text-ink">
                  {mainStock.name} ({mainStock.symbol}) {t("modal.titleSuffix")}
                </h2>
                <button
                  onClick={() => setIsAnalysisModalOpen(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-bg-sunk text-ink-3 hover:text-ink text-xl transition-colors"
                >
                  x
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-5 sm:p-8">
                <AnalysisResultPanel
                  result={analysisPanelResult}
                  loading={false}
                  err=""
                  showAnalyzeButton={false}
                  onAnalyze={handleAnalyzeClick}
                  onDownload={handleDownloadClick}
                  onZoom={() => {}}
                  displayText={displayText}
                  layout="full"
                  reportMeta={reportMeta}
                />
              </div>
            </div>
          </div>,
          document.body,
        )}

        {toast.visible && (
          <div
            className="fixed bottom-8 left-1/2 -translate-x-1/2 bg-ink/80 text-bg px-4 py-2 rounded-lg text-sm sm:text-base"
          >
            {toast.message}
          </div>
        )}
      </div>
    </div>
  );
}
