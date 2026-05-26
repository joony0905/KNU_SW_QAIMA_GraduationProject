// frontend/src/pages/StocksMockPage.tsx
import { isLoggedIn } from "../utils/auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import TradingViewWidget from "../components/TradingViewWidget";
import AnalysisResultPanel from "../components/AnalysisResultPanel";
import { PdfExportContext } from "../contexts/PdfExportContext";
import qaimaLogo from "../assets/qaima-final.png";
import type { AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import { useRef, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Star, Sun, Moon, LineChart, MapPin } from "lucide-react";
import InvestLevelBadge from "../components/InvestLevelBadge";
import StockSearchBar from "../components/StockSearchBar";
import FeatureIntro from "../components/FeatureIntro";
import { type IndicatorSection } from "../mocks/financialIndicators";
import type { FinancialDto } from "../types/financial";
import { buildSectionsFromDto } from "../mappers/financialMapper";
import { fetchFinancials, fetchFinancialsByYear, fetchMarketSnapshot } from "../api/financial";
import type { MarketSnapshotDto } from "../types/financial";
import FinancialDetailModal from "../components/FinancialDetailModal";
import { fetchAnalysis } from "../api/analysis";
import { getStockByCode } from "../api/stock";
import {
  fetchWatchlist,
  addWatchlistItem,
  deleteWatchlistItem,
} from "../api/watchlist";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import jsPDF from "jspdf";
import html2canvas from "html2canvas-pro";
import { clientLog } from "../utils/clientLog";
import type { AnalysisResponse } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";
import DictTerm from "../components/DictTerm";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { refreshTokenBalance } from "../api/billingStore";
import { useDictionary } from "../components/DictContext";
import { useTheme } from "../hooks/useTheme";
import useReportUserName from "../hooks/useReportUserName";
import {
  formatKstDate,
  formatKstOffsetDateTime,
  shiftKstDays,
  shiftKstYears,
} from "../utils/kst";
import { getApiErrorMessage } from "../utils/errorMessage";

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
  bb20_2:
    | {
        t: string;
        mid: number | null;
        upper: number | null;
        lower: number | null;
      }[]
    | null;
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

type CandleLoadOptions = {
  fromDate?: Date;
  toDate?: Date;
};

type TimelinePeriod = "A" | "H" | "Q";

const TIMELINE_PERIOD_CONFIG: Record<TimelinePeriod, { years: number }> = {
  A: { years: 5 },
  H: { years: 4 },
  Q: { years: 3 },
};

const pickTimelinePeriod = (fromIso?: string, toIso?: string): TimelinePeriod => {
  if (!fromIso || !toIso) return "A";
  const from = new Date(fromIso);
  const to = new Date(toIso);
  const diffMs = Math.max(0, to.getTime() - from.getTime());
  const diffDays = diffMs / (1000 * 60 * 60 * 24);
  if (diffDays <= 730) return "Q";
  if (diffDays <= 1825) return "H";
  return "A";
};

const timelineLabel = (dto: FinancialDto): string => {
  const yy = String(dto.year).slice(2);
  if (dto.periodType === "Q") return `${yy}.Q${dto.periodNo ?? dto.quarter ?? ""}`;
  if (dto.periodType === "H") return `${yy}.H${dto.periodNo ?? dto.half ?? ""}`;
  return `${yy}년`;
};

const buildFinancialTimeline = (
  items: FinancialDto[],
  period: TimelinePeriod,
  fromIso?: string,
  toIso?: string,
): FinancialTimelineSection | null => {
  if (!items.length) return null;

  const from = fromIso ? new Date(fromIso).getTime() : null;
  const to = toIso ? new Date(toIso).getTime() : null;

  const filtered = items
    .filter((item) => item.periodType === period)
    .filter((item) => {
      const reportTs = Date.parse(item.reportDate);
      if (Number.isNaN(reportTs)) return true;
      if (from != null && reportTs < from) return false;
      if (to != null && reportTs > to) return false;
      return true;
    });

  const source = (filtered.length > 0 ? filtered : items.filter((item) => item.periodType === period))
    .slice()
    .sort((a, b) => Date.parse(a.reportDate) - Date.parse(b.reportDate))
    .slice(-8);

  if (!source.length) return null;

  return {
    period,
    points: source.map((item) => ({
      label: timelineLabel(item),
      revenue: item.revenue ?? null,
      operatingIncome: item.operatingIncome ?? null,
      netIncome: item.netIncome ?? null,
      operatingMargin:
        item.operatingMargin ??
        (item.operatingIncome != null && item.revenue ? (item.operatingIncome / item.revenue) * 100 : null),
    })),
  };
};

const buildPriceFlowSummary = (
  data: Candle[],
  fromIso?: string,
  toIso?: string,
): PriceFlowSummary | null => {
  if (!data.length) return null;
  const ordered = data.slice().sort((a, b) => a.t - b.t);
  const first = ordered[0];
  const last = ordered[ordered.length - 1];
  const high = Math.max(...ordered.map((item) => item.h));
  const low = Math.min(...ordered.map((item) => item.l));
  const avgVolume = ordered.reduce((sum, item) => sum + item.v, 0) / ordered.length;
  const startClose = first.c;
  const endClose = last.c;
  const returnPct = startClose ? ((endClose - startClose) / startClose) * 100 : null;

  return {
    from: fromIso ?? new Date(first.t * 1000).toISOString(),
    to: toIso ?? new Date(last.t * 1000).toISOString(),
    startClose,
    endClose,
    returnPct,
    high,
    low,
    avgVolume,
  };
};

export default function StocksMockPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { t, i18n } = useTranslation("stocksPage");
  const { investLevel } = useDictionary();
  const reportUserName = useReportUserName();
  const currentTime = useKSTTime();

  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [analysisResult, setAnalysisResult] = useState<ApiResponse<AnalysisResponse> | null>(
    null,
  );

  const analysisData = analysisResult?.data ?? null;

  // 차트용 indicators state (analysisResult와 분리)
  const [indicatorData, setIndicatorData] = useState<IndicatorData | null>(
    null,
  );
  const [showChartIndicators, setShowChartIndicators] = useState(true);
  const [showChartMarkers, setShowChartMarkers] = useState(false);

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
  const llmVendor = "GPT-5 mini";

  const [loading, setLoading] = useState(false);
  const [analysisLoadingStage, setAnalysisLoadingStage] = useState<string>("");
  const [err, setErr] = useState("");

  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  // 표시부 미연결(스캐폴딩) — write 경로/타입 유지, 안 읽히는 바인딩만 생략
  const [, setFinancial] = useState<FinancialDto | null>(null);
  const [, setSnapshot] = useState<MarketSnapshotDto | null>(null);
  const [financialTimeline, setFinancialTimeline] = useState<FinancialTimelineSection | null>(null);
  const [priceFlowSummary, setPriceFlowSummary] = useState<PriceFlowSummary | null>(null);
  const [sections, setSections] = useState<IndicatorSection[]>([]);
  const [isAnalysisModalOpen, setIsAnalysisModalOpen] = useState(false);
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [, setAnalysisZoom] = useState(1); // 1 = 100% (표시부 미연결)

  const [isFinModalOpen, setIsFinModalOpen] = useState(false);
  // 기간선택 UI 미연결(스캐폴딩) — 값은 조회에 쓰이고 setter 만 생략
  const [selectedYear] = useState<number>(2024);
  const [selectedPeriodType] = useState<"A" | "Q" | "H">("A");
  const [selectedPeriodNo] = useState<number | undefined>(undefined);
  const [, setTrendData] = useState<FinancialDto[]>([]);

  const isLoadingMoreRef = useRef(false);
  const requestedRangesRef = useRef<Set<string>>(new Set());
  const pdfRef = useRef<HTMLDivElement | null>(null);
  // PDF 캡처 중 true → 스크롤 등장 컴포넌트가 즉시 최종 상태로 렌더
  const [pdfExporting, setPdfExporting] = useState(false);

  const chartRangeRef = useRef<{
    stockCode: string;
    freq: "ONE_D";
    fromIso: string;
    toIso: string;
    absoluteMinFromIso: string; // 항상 1년 하한 (줌아웃 확장 허용)
    mode: LoadMode;
  } | null>(null);

  const getColorClassByNumber = (n: number | null) => {
    if (n === null || !Number.isFinite(n)) return "text-flat";
    if (n > 0) return "text-rise";
    if (n < 0) return "text-fall";
    return "text-flat";
  };

  // 초기값은 종목정보만, 가격/등락은 candles로만
  const [mainStock, setMainStock] = useState<MainStockState>({
    name: "삼성전자",
    symbol: "005930",
    price: null,
    change: null,
    changeRate: null,
  });
  const reportMeta = analysisData
    ? {
        featureType: "FEATURE1" as const,
        subjectLabel: `${mainStock.name || analysisData.metrics?.stockCode || "분석종목"} (${mainStock.symbol || analysisData.metrics?.stockCode || "-"})`,
        generatedAt: analysisResult?.meta?.timestamp ?? null,
        analysisModel: llmVendor,
        investLevel,
        userName: reportUserName,
        analysisWindow: analysisFrom || analysisTo ? `${analysisFrom || "-"} ~ ${analysisTo || "-"}` : null,
        dataAsOf: analysisData.metrics?.asOf ?? null,
      }
    : null;

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
  const loadCandles = async (
    stockCode: string,
    mode: LoadMode,
    options?: CandleLoadOptions,
  ): Promise<Candle[]> => {
    setChartLoading(true);
    setChartError(null);

    const toDate = options?.toDate ?? new Date();
    const days = mode === "ANALYZE" ? MAX_HISTORY_DAYS : INITIAL_HISTORY_DAYS;
    const fromDate = options?.fromDate ?? shiftKstDays(toDate, -(days - 1));
    const fromIso = formatKstOffsetDateTime(fromDate);
    const toIso = formatKstOffsetDateTime(toDate);

    try {
      const usedFreq = "ONE_D" as const;

      // 분석 주기는 현재 ONE_D 고정 유지
      setAnalysisFreq(usedFreq);

      const response = await fetchCandles(
        stockCode,
        usedFreq,
        fromIso,
        toIso,
      );

      const data = response.data;

      if (data.length === 0) {
        setChartError(t("chart.noData"));
      }

      setCandles(data);

      // 줌아웃 확장 하한은 항상 1년
      const absoluteMin = shiftKstDays(toDate, -MAX_HISTORY_DAYS);

      chartRangeRef.current = {
        stockCode,
        freq: usedFreq,
        fromIso,
        toIso,
        absoluteMinFromIso: formatKstOffsetDateTime(absoluteMin),
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
      return data;
    } catch (e: any) {
      clientLog.error("Chart data fetch failed", {
        message: e?.message,
        status: e?.response?.status,
        data: e?.response?.data,
        url: e?.config?.baseURL
          ? `${e.config.baseURL}${e.config.url}`
          : e?.config?.url,
        params: e?.config?.params,
      });
      setChartError(t("chart.loadFailed"));
      setCandles([]);

      setMainStock((prevState) => ({
        ...prevState,
        symbol: stockCode,
        price: null,
        change: null,
        changeRate: null,
      }));
      return [];
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
        LOAD_MORE_LIMIT,
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
      clientLog.error("Additional candle load failed", e);
    } finally {
      isLoadingMoreRef.current = false;
    }
  };

  const handleSearch = async (value: string) => {
    clientLog.warn("Stock search submitted", value);

    const q = value.trim();
    if (!q) {
      setErr(t("errors.needStockInput"));
      setHasSelectedStock(false);
      return;
    }

    setErr("");
    setHasSelectedStock(true);

    setAnalysisResult(null);
    setFinancialTimeline(null);
    setPriceFlowSummary(null);
    setIndicatorData(null);
    setShowChartIndicators(true);
    setShowChartMarkers(false);
    setShowAnalyzeButton(true);

    // 기본 분석 기간: 최근 6개월
    const defaultTo = new Date();
    const defaultFrom = shiftKstDays(defaultTo, -180);
    setAnalysisFrom(formatKstDate(defaultFrom));
    setAnalysisTo(formatKstDate(defaultTo));

    setMainStock((prev) => ({
      ...prev,
      price: null,
      change: null,
      changeRate: null,
    }));

    let resolvedCode: string | null = null;
    let resolvedStockId: number | null = null;

    try {
      const stockInfo = await getStockByCode(q);
      resolvedCode = stockInfo.stockCode;
      resolvedStockId = stockInfo.stockId ?? null;

      setMainStock((prev) => ({
        ...prev,
        name: stockInfo.companyName,
        symbol: stockInfo.stockCode,
        price: stockInfo.price ?? null, // number 그대로
        change: null,
        changeRate: stockInfo.changeRate ?? null, // number 그대로
      }));
    } catch (e) {
      clientLog.warn("Stock lookup failed; fallback path used", e);

      if (/^\d{6}$/.test(q)) {
        resolvedCode = q;

        setMainStock((prev) => ({
          ...prev,
          symbol: q,
          price: null,
          change: null,
          changeRate: null,
        }));
      } else {
        setErr(
          t("errors.invalidStock"),
        );
        setHasSelectedStock(false);
        return;
      }
    }

    setCurrentStockId(resolvedStockId);
    void syncWatchlistMembership(resolvedStockId);

    const code = resolvedCode;

    try {
      const [singleData, trend, snap] = await Promise.all([
        fetchFinancialsByYear(code, selectedYear, selectedPeriodType, selectedPeriodNo),
        fetchFinancials(code, 5, "A"),
        fetchMarketSnapshot(code).catch(() => null),
      ]);
      setSnapshot(snap);
      if (Array.isArray(singleData) && singleData.length > 0) {
        setFinancial(singleData[0]);
        setSections(buildSectionsFromDto(singleData[0], snap));
      } else if (singleData && !Array.isArray(singleData)) {
        const dto = singleData as unknown as FinancialDto;
        setFinancial(dto);
        setSections(buildSectionsFromDto(dto, snap));
      } else {
        // financial 데이터 없어도 snapshot만으로 투자지표 표시
        setSections(buildSectionsFromDto(null, snap));
      }
      setTrendData(Array.isArray(trend) ? trend : []);
    } catch (e) {
      clientLog.error("Financial statement fetch failed", e);
    }

    // 초기 차트는 30일만 로드하고, 분석기간 입력값은 유지
    await loadCandles(code, "INITIAL");
  };

  // 메인페이지에서 ?q=종목 으로 진입 시, 검색창에 입력한 것과 동일하게 자동 검색 (분석은 사용자가 직접 실행)
  const didAutoSearchRef = useRef(false);
  useEffect(() => {
    if (didAutoSearchRef.current) return;
    const q = searchParams.get("q");
    if (q && q.trim()) {
      didAutoSearchRef.current = true;
      void handleSearch(q);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const handleAnalyzeClick = async () => {
    if (!isLoggedIn()) {
      // 검색한 종목을 ?q= 로 실어 두면 로그인 복귀 후 자동검색으로 동일 화면이
      // 그대로 복원된다(빈 화면 재시작 방지). 스크롤 위치는 복원하지 않는다
      // (복원하면 "맨 위→점프" 가 보여서, 그냥 맨 위에서 시작).
      const symbol = mainStock?.symbol?.trim();
      sessionStorage.setItem(
        "qaima_redirect",
        symbol ? `/feature/1?q=${encodeURIComponent(symbol)}` : "/feature/1",
      );
      navigate("/login");
      return;
    }
    setLoading(true);
    setAnalysisLoadingStage(t("loadingStage.preparing"));
    setErr("");
    setAnalysisResult(null);
    setFinancialTimeline(null);
    setPriceFlowSummary(null);
    setIndicatorData(null);
    setShowChartIndicators(true);
    setShowChartMarkers(false);

    if (!mainStock.symbol) {
      setErr(t("errors.needStockBeforeAnalyze"));
      setAnalysisLoadingStage("");
      setLoading(false);
      return;
    }

    try {
      setShowAnalyzeButton(false); // ✅ 결과가 생기면 버튼 숨김

      const requestedFromDate = analysisFrom
        ? new Date(`${analysisFrom}T00:00:00+09:00`)
        : undefined;
      const requestedToDate = analysisTo
        ? new Date(`${analysisTo}T23:59:59+09:00`)
        : undefined;

      // 사용자가 고른 기간으로 차트와 분석 범위를 맞춘다.
      setAnalysisLoadingStage(t("loadingStage.priceData"));
      const analysisCandles = await loadCandles(mainStock.symbol, "ANALYZE", {
        fromDate: requestedFromDate,
        toDate: requestedToDate,
      });

      const range = chartRangeRef.current;
      if (!range) {
        throw new Error("Chart range is not ready");
      }

      // 사용자 선택 기간이 있으면 우선 사용, 없으면 차트 범위 사용
      const fromDate = analysisFrom || range.fromIso;
      const toDate = analysisTo || range.toIso;

      setAnalysisLoadingStage(t("loadingStage.llmGenerating"));
      const result = await fetchAnalysis({
        stockCode: mainStock.symbol,
        freq: analysisFreq,
        from: fromDate,
        to: toDate,
        marketDivCode,
        includeExplain,
        llmVendor,
        investLevel,
        languageCode: i18n.language.startsWith("en") ? "en" : "ko",
      });

      setAnalysisResult(result);
      setPriceFlowSummary(buildPriceFlowSummary(analysisCandles, fromDate, toDate));

      const timelinePeriod = pickTimelinePeriod(fromDate, toDate);
      setAnalysisLoadingStage(t("loadingStage.timeline"));
      const timelineFinancials = await fetchFinancials(
        mainStock.symbol,
        TIMELINE_PERIOD_CONFIG[timelinePeriod].years,
        timelinePeriod,
      ).catch(() => []);
      setFinancialTimeline(
        buildFinancialTimeline(timelineFinancials, timelinePeriod, fromDate, toDate),
      );

      const ind = result?.data?.metrics?.indicators;
      setAnalysisLoadingStage(t("loadingStage.indicators"));

      setIndicatorData({
        ema: ind?.ema ?? null,
        bb20_2: ind?.bb20_2 ?? null,
        stoch14_3_3: ind?.stoch14_3_3 ?? null,
        warnings: ind?.warnings ?? [],
      });
      setShowChartIndicators(true);
      setShowChartMarkers(false);
    } catch (e) {
      setErr(getApiErrorMessage(e, t("errors.analyzeFailed")));
    } finally {
      // 성공/실패 무관하게 서버 잔액과 동기화 (백엔드가 실패 시 환불 처리하므로
      // 환불된 잔액이 UI 에 즉시 반영되도록).
      refreshTokenBalance().catch(() => {});
      setAnalysisLoadingStage("");
      setLoading(false);
    }
  };

  const loadImageElement = (src: string): Promise<HTMLImageElement> =>
    new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = src;
    });

  const handleDownloadClick = async () => {
    if (!analysisResult || !pdfRef.current) return;

    // 스크롤로 아직 등장하지 않은 섹션도 빠짐없이 캡처되도록 강제 렌더 ON
    setPdfExporting(true);
    try {
      // React 커밋 + 등장 트랜지션(~0.5s) 정착 대기 후 캡처
      await new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      );
      await new Promise((resolve) => setTimeout(resolve, 700));

      const [fullCanvas, logoImg] = await Promise.all([
        html2canvas(pdfRef.current, {
          scale: 2,
          useCORS: true,
          backgroundColor: "#ffffff",
          windowWidth: pdfRef.current.scrollWidth,
          windowHeight: pdfRef.current.scrollHeight,
          // data-pdf-exclude 요소(다운로드/확대 버튼)는 캡처에서 제외
          ignoreElements: (element) =>
            element instanceof HTMLElement &&
            element.hasAttribute("data-pdf-exclude"),
        }),
        loadImageElement(qaimaLogo).catch(() => null),
      ]);

      const doc = new jsPDF("p", "mm", "a4");
      const pdfWidth = doc.internal.pageSize.getWidth();
      const pdfHeight = doc.internal.pageSize.getHeight();
      const margin = 10;
      const usableWidth = pdfWidth - margin * 2;
      const usableHeight = pdfHeight - margin * 2;

      // 캔버스 픽셀당 PDF mm 환산
      const pxPerMm = fullCanvas.width / usableWidth;
      const pageSlicePx = Math.floor(usableHeight * pxPerMm);

      let yPx = 0;
      let pageIndex = 0;

      while (yPx < fullCanvas.height) {
        const sliceHeightPx = Math.min(pageSlicePx, fullCanvas.height - yPx);

        // 페이지 1장에 들어갈 분량만 별도 캔버스에 잘라낸다 (페이지 경계 중복 방지)
        const slice = document.createElement("canvas");
        slice.width = fullCanvas.width;
        slice.height = sliceHeightPx;
        const ctx = slice.getContext("2d");
        if (!ctx) throw new Error("Canvas context unavailable");
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, slice.width, slice.height);
        ctx.drawImage(fullCanvas, 0, -yPx);

        const sliceImg = slice.toDataURL("image/png");
        const sliceImgHeightMm = sliceHeightPx / pxPerMm;

        if (pageIndex > 0) doc.addPage();
        doc.addImage(sliceImg, "PNG", margin, margin, usableWidth, sliceImgHeightMm);

        yPx += sliceHeightPx;
        pageIndex++;
      }

      // 모든 페이지 중앙에 로고 워터마크(반투명) 삽입
      if (logoImg && logoImg.naturalWidth > 0) {
        const docGState = doc as unknown as {
          GState: new (opts: { opacity: number }) => unknown;
          setGState: (g: unknown) => void;
        };
        const wmWidth = pdfWidth * 0.5;
        const wmHeight = wmWidth * (logoImg.naturalHeight / logoImg.naturalWidth);
        const wmX = (pdfWidth - wmWidth) / 2;
        const wmY = (pdfHeight - wmHeight) / 2;
        const pageCount = doc.getNumberOfPages();
        for (let p = 1; p <= pageCount; p++) {
          doc.setPage(p);
          doc.saveGraphicsState();
          docGState.setGState(new docGState.GState({ opacity: 0.08 }));
          doc.addImage(logoImg, "PNG", wmX, wmY, wmWidth, wmHeight);
          doc.restoreGraphicsState();
        }
      }

      const fileName = `${mainStock.symbol}_analysis.pdf`;
      doc.save(fileName);
    } catch (e) {
      clientLog.error("PDF generation failed", e);
    } finally {
      // 캡처 종료 — 화면을 원래 스크롤 등장 동작으로 복원
      setPdfExporting(false);
    }
  };

  /*
  useEffect(() => {
    void loadCandles(mainStock.symbol);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  */
  const [isInterested, setIsInterested] = useState(false);
  const [currentStockId, setCurrentStockId] = useState<number | null>(null);
  const [watchlistItemId, setWatchlistItemId] = useState<number | null>(null);


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
      // 워치리스트 미존재/권한 등은 조용히 무시 (별 비활성 상태 유지)
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

  // 2) mainStock 표시/색상 계산은 친구 코드 기반으로 개선
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

  const { theme, toggle } = useTheme();

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      <div className="qaima-stagger max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="flex items-end justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              {t("header.eyebrow")}
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
            <FeatureIntro variant="deep" />
          </div>
        )}

        {hasSelectedStock && (
          <>
            <div className="border-t border-line-strong" />
          <main className="w-full flex flex-col gap-4 sm:gap-5">
            <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1.2fr)] gap-4 lg:gap-6 items-start">
              <section className="bg-surface rounded-2xl border border-line shadow-card p-5 flex flex-col gap-4 xl:h-[580px] min-h-0">
                {/* 종목 헤더 — 카드 안 */}
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
                          className="transition-colors text-warn"
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

                {indicatorData && (
                  <div className="flex flex-wrap items-center justify-end gap-1.5">
                    <button
                      type="button"
                      onClick={() => setShowChartIndicators((prev) => !prev)}
                      aria-pressed={showChartIndicators}
                      className={`inline-flex h-8 items-center gap-1.5 rounded-lg border px-2.5 text-xs font-semibold transition-colors ${
                        showChartIndicators
                          ? "border-accent bg-accent-soft text-accent-ink"
                          : "border-line bg-surface text-ink-3 hover:bg-bg-sunk"
                      }`}
                    >
                      <LineChart size={14} aria-hidden="true" />
                      {t("chart.indicators")}
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowChartMarkers((prev) => !prev)}
                      aria-pressed={showChartMarkers}
                      className={`inline-flex h-8 items-center gap-1.5 rounded-lg border px-2.5 text-xs font-semibold transition-colors ${
                        showChartMarkers
                          ? "border-accent bg-accent-soft text-accent-ink"
                          : "border-line bg-surface text-ink-3 hover:bg-bg-sunk"
                      }`}
                    >
                      <MapPin size={14} aria-hidden="true" />
                      {t("chart.markers")}
                    </button>
                  </div>
                )}

                {/* 차트 영역 — sunken 제거, 같은 흰 배경 위에 차트 */}
                <div className="flex-1 min-h-0 w-full flex items-stretch">
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
                          {chartError}
                        </p>
                      </div>
                    )}

                    {!chartLoading && !chartError && (
                      <div className="flex-1 min-h-0 w-full">
                        {/* TradingViewWidget 부모 높이를 100% 사용 */}
                        <TradingViewWidget
                          key={showChartIndicators ? "chart-with-indicators" : "chart-price-only"}
                          candles={candles}
                          indicators={indicatorData}
                          showSubPanes={Boolean(indicatorData && showChartIndicators)}
                          showIndicators={showChartIndicators}
                          showMarkers={showChartMarkers}
                          markerMode="triple" // "sto_ema" | "bb_sto" | "both"
                          onRequestMoreHistory={handleRequestMoreHistory}
                        />
                      </div>
                    )}
                </div>
              </section>

              <aside className="bg-surface rounded-2xl border border-line shadow-card p-5 flex flex-col gap-4 xl:h-[580px] min-h-0">
                <div className="flex items-center justify-between">
                  <h2 className="text-lg sm:text-xl md:text-2xl font-semibold text-ink tracking-tight">
                    {t("indicators.title")}
                  </h2>
                  <button
                    onClick={() => setIsFinModalOpen(true)}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-accent-soft text-accent-ink hover:bg-accent/15 transition-colors"
                  >
                    {t("indicators.financialStatement")}
                  </button>
                </div>

                <div className="h-px bg-line" />

                <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-4">
                  {sections.length > 0 ? (
                    sections.map((section) => (
                      <IndicatorSectionBlock
                        key={section.sectionTitle}
                        section={section}
                      />
                    ))
                  ) : (
                    <p className="text-sm text-ink-3">
                      {t("indicators.noFinancialData")}
                    </p>
                  )}
                </div>
              </aside>
            </div>
            {/* ========== 분석 기간 선택 ========== */}
            <div className="w-full bg-surface rounded-xl border border-line px-4 sm:px-6 py-3 flex flex-wrap items-center gap-x-4 gap-y-3 shadow-card">
              <h3 className="text-sm sm:text-base font-semibold text-ink shrink-0">{t("analysisPeriod.title")}</h3>

              {/* 프리셋 칩 */}
              <div className="flex flex-wrap gap-1.5">
                {([
                  [t("analysisPeriod.preset3Months"), 90],
                  [t("analysisPeriod.preset6Months"), 180],
                  [t("analysisPeriod.preset1Year"), 365],
                  [t("analysisPeriod.preset3Years"), 1095],
                ] as const).map(([label, days]) => {
                  const to = new Date();
                  const from = shiftKstDays(to, -days);
                  const fromStr = formatKstDate(from);
                  const toStr = formatKstDate(to);
                  const isActive = analysisFrom === fromStr && analysisTo === toStr;

                  return (
                    <button
                      key={label}
                      onClick={() => {
                        setAnalysisFrom(fromStr);
                        setAnalysisTo(toStr);
                      }}
                      className={`px-3.5 py-1.5 rounded-full text-sm transition-colors ${
                        isActive
                          ? "bg-accent text-white font-semibold"
                          : "bg-bg-sunk text-ink-3 hover:bg-surface-2 font-medium"
                      }`}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>

              {/* 직접 날짜 입력 — 오른쪽 끝 정렬 */}
              <div className="flex items-center gap-2 text-sm ml-auto">
                <input
                  type="date"
                  value={analysisFrom}
                  min={formatKstDate(shiftKstYears(new Date(), -3))}
                  max={analysisTo || formatKstDate(new Date())}
                  onChange={(e) => setAnalysisFrom(e.target.value)}
                  className="px-2 py-1.5 border border-line rounded-lg text-ink bg-surface focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent"
                />
                <span className="text-ink-4">~</span>
                <input
                  type="date"
                  value={analysisTo}
                  min={analysisFrom || formatKstDate(shiftKstYears(new Date(), -3))}
                  max={formatKstDate(new Date())}
                  onChange={(e) => setAnalysisTo(e.target.value)}
                  className="px-2 py-1.5 border border-line rounded-lg text-ink bg-surface focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent"
                />
              </div>
            </div>

            {/* ========== 분석 실행 그라디언트 카드 ========== */}
            {showAnalyzeButton && (
              <section className="rounded-xl border border-line shadow-card p-5 sm:p-6
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
                    className="px-5 py-2.5 rounded-lg bg-accent text-white font-semibold text-sm
                               hover:opacity-90 disabled:opacity-50 transition-opacity tracking-tight"
                  >
                    {t("runCard.viewButton")}
                    </button>
                  </div>
                </div>
              </section>
            )}

            {/* ========== 하단 분석 결과 영역 ========== */}
            {!analysisData && !loading && !err && (
              <div className="w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center py-24 bg-bg-sunk border-line text-ink-4">
                <p className="text-base font-medium">{t("emptyResult.title")}</p>
                <p className="text-sm mt-1">{t("emptyResult.subtitle")}</p>
              </div>
            )}

            {(loading || !!err || !!analysisData) && <div ref={pdfRef}>
              <PdfExportContext.Provider value={pdfExporting}>
              <AnalysisResultPanel
                result={analysisData ? {
                  ...analysisData,
                } as AnalysisPanelResult : null}
                loading={loading}
                loadingStage={analysisLoadingStage}
                err={err}
                showAnalyzeButton={false}
                onAnalyze={handleAnalyzeClick}
                onDownload={handleDownloadClick}
                onZoom={() => {
                  setAnalysisZoom(1);
                  setIsAnalysisModalOpen(true);
                }}
                displayText={analysisData?.explain?.text ?? ""}
                financialTimeline={financialTimeline}
                priceFlowSummary={priceFlowSummary}
                layout="full"
                reportMeta={reportMeta}
              />
              </PdfExportContext.Provider>
            </div>}
          </main>
          </>
        )}

        {isAnalysisModalOpen && analysisResult && createPortal(
          <div
            className="fixed inset-0 z-[100] bg-ink/50 flex items-center justify-center p-4"
            onClick={() => setIsAnalysisModalOpen(false)}
          >
            <div
              className="bg-surface rounded-xl w-full max-w-5xl max-h-[90vh] flex flex-col shadow-pop"
              onClick={(e) => e.stopPropagation()}
            >
              {/* 상단 헤더 */}
              <div className="flex items-center justify-between px-5 py-3 border-b border-line">
                <h2 className="text-base sm:text-lg font-semibold text-ink">
                  {mainStock.name} ({mainStock.symbol}) {t("modal.titleSuffix")}
                </h2>
                <button
                  onClick={() => setIsAnalysisModalOpen(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-bg-sunk text-ink-3 hover:text-ink text-xl transition-colors"
                >
                  ✕
                </button>
              </div>

              {/* 내용 영역 — AnalysisResultPanel과 동일한 디자인 */}
              <div className="flex-1 overflow-y-auto p-5 sm:p-8">
                <AnalysisResultPanel
                  result={analysisData ? {
                    ...analysisData,
                  } as AnalysisPanelResult : null}
                  loading={false}
                  loadingStage=""
                  err=""
                  showAnalyzeButton={false}
                  onAnalyze={handleAnalyzeClick}
                  onDownload={handleDownloadClick}
                  onZoom={() => {}}
                  displayText={analysisData?.explain?.text ?? ""}
                  financialTimeline={financialTimeline}
                  priceFlowSummary={priceFlowSummary}
                  layout="full"
                  reportMeta={reportMeta}
                />
              </div>
            </div>
          </div>,
          document.body,
        )}

        <FinancialDetailModal
          isOpen={isFinModalOpen}
          onClose={() => setIsFinModalOpen(false)}
          ticker={mainStock.symbol}
          companyName={mainStock.name}
        />

        {/* 토스트 메시지 */}
        {toast.visible && createPortal(
          <div
            className="fixed bottom-8 left-1/2 -translate-x-1/2 z-[200]
                       bg-ink/90 text-bg px-4 py-2 rounded-lg
                       text-sm sm:text-base shadow-pop pointer-events-none"
          >
            {toast.message}
          </div>,
          document.body,
        )}
      </div>
    </div>
  );
}

/** 공통 셀 — sunken 제거로 셀마다 border 로 구분 */
function Cell({
  title,
  subtitle,
  value,
}: {
  title: string;
  subtitle: string;
  value: string;
}) {
  const isEmpty = value === "-" || value === "" || value == null;
  return (
    <div className="bg-surface border border-line rounded-lg px-3 py-2.5 flex flex-col gap-1 min-w-0">
      <div className="flex justify-between items-baseline gap-2 min-w-0">
        <span className="text-[12.5px] font-semibold text-ink whitespace-nowrap tracking-tight">
          <DictTerm term={title}>{title}</DictTerm>
        </span>
        <span
          className={`text-[13px] font-mono tabular whitespace-nowrap ${
            isEmpty ? "text-ink-4 font-normal" : "text-ink font-bold"
          }`}
        >
          {isEmpty ? "—" : value}
        </span>
      </div>
      <div className="text-[11px] text-ink-3 truncate">
        <DictTerm term={subtitle}>{subtitle}</DictTerm>
      </div>
    </div>
  );
}

type IndicatorSectionBlockProps = {
  section: IndicatorSection;
};

function IndicatorSectionBlock({ section }: IndicatorSectionBlockProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex justify-between items-baseline">
        <span className="text-sm font-semibold text-ink tracking-tight">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </span>
        <span className="text-xs text-ink-4">{section.rows.length}</span>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {section.rows.map((cell, idx) => (
          <Cell
            key={`${cell.title}-${idx}`}
            title={cell.title}
            subtitle={cell.subtitle}
            value={cell.value}
          />
        ))}
      </div>
    </div>
  );
}
