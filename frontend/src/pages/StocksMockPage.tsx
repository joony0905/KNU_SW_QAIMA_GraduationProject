// frontend/src/pages/StocksMockPage.tsx
import { isLoggedIn } from "../utils/auth";
import { useNavigate } from "react-router-dom";
import TradingViewWidget from "../components/TradingViewWidget";
import AnalysisResultPanel, { LLM_VENDOR_OPTIONS } from "../components/AnalysisResultPanel";
import type { AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import { useRef, useEffect, useState } from "react";
import { Star, Sun, Moon, ChevronDown, ChevronUp } from "lucide-react";
import StockSearchBar from "../components/StockSearchBar";
import { type IndicatorSection } from "../mocks/financialIndicators";
import type { FinancialDto } from "../types/financial";
import { buildSectionsFromDto } from "../mappers/financialMapper";
import { fetchFinancials, fetchFinancialsByYear, fetchMarketSnapshot } from "../api/financial";
import type { MarketSnapshotDto } from "../types/financial";
import FinancialDetailModal from "../components/FinancialDetailModal";
import { fetchAnalysis } from "../api/analysis";
import { getStockByCode } from "../api/stock";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import jsPDF from "jspdf";
import html2canvas from "html2canvas-pro";
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";
import type { AnalysisResponse } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";
import DictTerm from "../components/DictTerm";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { refreshTokenBalance } from "../api/billingStore";
import { useTheme } from "../hooks/useTheme";
import {
  formatKstDate,
  formatKstDateTimeDisplay,
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
  const [llmVendor, setLlmVendor] = useState<string>("Gemini 2.5 Flash");

  const [loading, setLoading] = useState(false);
  const [analysisLoadingStage, setAnalysisLoadingStage] = useState<string>("");
  const [err, setErr] = useState("");

  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  const [financial, setFinancial] = useState<FinancialDto | null>(null);
  const [snapshot, setSnapshot] = useState<MarketSnapshotDto | null>(null);
  const [financialTimeline, setFinancialTimeline] = useState<FinancialTimelineSection | null>(null);
  const [priceFlowSummary, setPriceFlowSummary] = useState<PriceFlowSummary | null>(null);
  const [sections, setSections] = useState<IndicatorSection[]>([]);
  const [isAnalysisModalOpen, setIsAnalysisModalOpen] = useState(false);
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [analysisZoom, setAnalysisZoom] = useState(1); // 1 = 100%

  const [isFinModalOpen, setIsFinModalOpen] = useState(false);
  const [selectedYear, setSelectedYear] = useState<number>(2024);
  const [selectedPeriodType, setSelectedPeriodType] = useState<"A" | "Q" | "H">("A");
  const [selectedPeriodNo, setSelectedPeriodNo] = useState<number | undefined>(undefined);
  const [trendData, setTrendData] = useState<FinancialDto[]>([]);

  const isLoadingMoreRef = useRef(false);
  const requestedRangesRef = useRef<Set<string>>(new Set());
  const pdfRef = useRef<HTMLDivElement | null>(null);

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
        setChartError("차트 데이터가 없습니다.");
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
    setFinancialTimeline(null);
    setPriceFlowSummary(null);
    setIndicatorData(null);
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

    try {
      const stockInfo = await getStockByCode(q);
      resolvedCode = stockInfo.stockCode;

      setMainStock((prev) => ({
        ...prev,
        name: stockInfo.companyName,
        symbol: stockInfo.stockCode,
        price: stockInfo.price ?? null, // number 그대로
        change: null,
        changeRate: stockInfo.changeRate ?? null, // number 그대로
      }));
    } catch (e) {
      console.error("종목 정보 조회 실패(임시 무시):", e);

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
          "유효하지 않은 종목입니다. 6자리 종목코드를 입력해주세요. (예: 005930)",
        );
        setHasSelectedStock(false);
        return;
      }
    }

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
      console.error("재무제표 조회 실패:", e);
    }

    // 초기 차트는 30일만 로드하고, 분석기간 입력값은 유지
    await loadCandles(code, "INITIAL");
  };

  const handleAnalyzeClick = async () => {
    if (!isLoggedIn()) {
      sessionStorage.setItem("qaima_redirect", window.location.pathname + window.location.search);
      navigate("/login");
      return;
    }
    setLoading(true);
    setAnalysisLoadingStage("분석 준비 중");
    setErr("");
    setAnalysisResult(null);
    setFinancialTimeline(null);
    setPriceFlowSummary(null);
    setIndicatorData(null);

    if (!mainStock.symbol) {
      setErr("먼저 종목을 검색한 뒤 분석을 실행해주세요.");
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
      setAnalysisLoadingStage("가격 데이터를 불러오는 중");
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

      setAnalysisLoadingStage("LLM 분석 결과를 생성하는 중");
      const result = await fetchAnalysis({
        stockCode: mainStock.symbol,
        freq: analysisFreq,
        from: fromDate,
        to: toDate,
        marketDivCode,
        includeExplain,
        llmVendor,
      });

      setAnalysisResult(result);
      refreshTokenBalance().catch(() => {});
      setPriceFlowSummary(buildPriceFlowSummary(analysisCandles, fromDate, toDate));

      const timelinePeriod = pickTimelinePeriod(fromDate, toDate);
      setAnalysisLoadingStage("재무 시계열을 정리하는 중");
      const timelineFinancials = await fetchFinancials(
        mainStock.symbol,
        TIMELINE_PERIOD_CONFIG[timelinePeriod].years,
        timelinePeriod,
      ).catch(() => []);
      setFinancialTimeline(
        buildFinancialTimeline(timelineFinancials, timelinePeriod, fromDate, toDate),
      );

      const ind = result?.data?.metrics?.indicators;
      setAnalysisLoadingStage("투자 보조지표를 반영하는 중");

      setIndicatorData({
        ema: ind?.ema ?? null,
        bb20_2: ind?.bb20_2 ?? null,
        stoch14_3_3: ind?.stoch14_3_3 ?? null,
        warnings: ind?.warnings ?? [],
      });
    } catch (e) {
      setErr(getApiErrorMessage(e, "분석 결과를 불러오지 못했습니다."));
    } finally {
      setAnalysisLoadingStage("");
      setLoading(false);
    }
  };

  const handleDownloadClick = async () => {
    if (!analysisResult || !pdfRef.current) return;

    try {
      const fullCanvas = await html2canvas(pdfRef.current, {
        scale: 2,
        useCORS: true,
        backgroundColor: "#ffffff",
        windowWidth: pdfRef.current.scrollWidth,
        windowHeight: pdfRef.current.scrollHeight,
      });

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

      const fileName = `${mainStock.symbol}_analysis.pdf`;
      doc.save(fileName);
    } catch (e) {
      console.error("PDF 생성 실패:", e);
    }
  };

  const handleFullscreenClick = () => {
    const elem = document.documentElement;
    if (!document.fullscreenElement) {
      elem.requestFullscreen().catch((err) => {
        console.error(`Error attempting to enable fullscreen: ${err.message}`);
      });
    } else {
      document.exitFullscreen();
    }
  };

  /*
  useEffect(() => {
    void loadCandles(mainStock.symbol);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  */
  const formatNumber = (value?: number | null) => {
    if (value === null || value === undefined) {
      return "-";
    }
    return value.toLocaleString();
  };

  const formatDate = (value?: string | null) => {
    return formatKstDateTimeDisplay(value);
  };

  const [isInterested, setIsInterested] = useState(false);


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

  const [isModelOpen, setIsModelOpen] = useState(false);
  const modelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutsideModel = (e: MouseEvent) => {
      if (!modelRef.current) return;
      if (modelRef.current.contains(e.target as Node)) return;
      setIsModelOpen(false);
    };

    document.addEventListener("click", handleClickOutsideModel);
    return () => document.removeEventListener("click", handleClickOutsideModel);
  }, []);

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
    <div className="min-h-screen bg-bg ml-[84px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="flex items-end justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Equities · Deep Analysis
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              심층분석
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? "라이트 모드" : "다크 모드"}
              className="w-9 h-9 grid place-items-center rounded-xl bg-surface
                         border border-line text-ink-2 shadow-card
                         hover:bg-bg-sunk transition-colors"
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <TokenBalanceBadge />
          </div>
        </header>

        <StockSearchBar onSearch={handleSearch} />

        {hasSelectedStock && (
          <>
            <div className="border-t border-line-strong" />
          <main className="w-full flex flex-col gap-4 sm:gap-5">
            <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.7fr)_minmax(0,1.2fr)] gap-4 lg:gap-6 items-start">
              <div className="flex flex-col gap-3 sm:gap-4 xl:h-[580px]">
                {/* 종목 헤더 — 카드 밖, 페이지 위에 직접 표시 */}
                <div className="flex items-start justify-between gap-4 px-1">
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
                      <span className="text-sm text-ink-3">원</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-sm font-medium font-mono tabular">
                      <span className="text-ink-3 text-xs">전일대비</span>
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

                {/* 차트만 흰 카드로 감쌈 */}
                <section className="w-full flex-1 bg-surface border border-line shadow-card overflow-hidden min-h-0 flex flex-col">
                  <div className="flex-1 min-h-0 w-full flex items-stretch">
                    {chartLoading && (
                      <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                        <p className="text-sm sm:text-base text-ink-3">
                          차트를 불러오는 중입니다...
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
                          candles={candles}
                          indicators={indicatorData}
                          showSubPanes={Boolean(indicatorData)}
                          markerMode="triple" // "sto_ema" | "bb_sto" | "both"
                          onRequestMoreHistory={handleRequestMoreHistory}
                        />
                      </div>
                    )}
                  </div>
                </section>
              </div>

              <div className="w-full xl:h-[580px] flex flex-col gap-3 sm:gap-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-lg sm:text-xl md:text-2xl font-semibold text-ink tracking-tight">
                    투자지표
                  </h2>
                  <button
                    onClick={() => setIsFinModalOpen(true)}
                    className="px-4 py-1.5 rounded-lg text-sm font-medium bg-accent-soft text-accent border border-accent/30 hover:bg-accent/15 hover:border-accent/50 transition-colors"
                  >
                    재무제표
                  </button>
                </div>

                <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-5">
                    {sections.length > 0 ? (
                      sections.map((section) => (
                        <IndicatorSectionBlock
                          key={section.sectionTitle}
                          section={section}
                          layout={
                            section.sectionTitle === "밸류에이션"
                              ? "2-2-2"
                              : section.sectionTitle === "수익성"
                                ? "2-2"
                                : section.sectionTitle === "재무안정성"
                                  ? "2-2-1"
                                  : "2"
                          }
                        />
                      ))
                    ) : (
                      <p className="text-sm text-ink-3">
                        해당 조건의 재무제표 데이터가 없습니다.
                      </p>
                    )}
                </div>
              </div>
            </div>
            {/* ========== 분석 기간 선택 ========== */}
            <div className="w-full bg-surface rounded-xl border border-line px-4 sm:px-6 py-3 flex flex-wrap items-center gap-x-4 gap-y-3 shadow-card">
              <h3 className="text-sm sm:text-base font-semibold text-ink shrink-0">분석 기간</h3>

              {/* 프리셋 칩 */}
              <div className="flex flex-wrap gap-1.5">
                {([
                  ["3개월", 90],
                  ["6개월", 180],
                  ["1년", 365],
                  ["3년", 1095],
                  ["5년", 1825],
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
                  min={formatKstDate(shiftKstYears(new Date(), -5))}
                  max={analysisTo || formatKstDate(new Date())}
                  onChange={(e) => setAnalysisFrom(e.target.value)}
                  className="px-2 py-1.5 border border-line rounded-lg text-ink bg-surface focus:outline-none focus:ring-2 focus:ring-accent/20 focus:border-accent"
                />
                <span className="text-ink-4">~</span>
                <input
                  type="date"
                  value={analysisTo}
                  min={analysisFrom || formatKstDate(shiftKstYears(new Date(), -5))}
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
                    ✨ AI 심층분석
                  </div>
                  <h3 className="text-lg font-bold text-ink tracking-tight">
                    선택한 기간을 한 번에 분석해 드릴게요
                  </h3>
                  <p className="text-sm text-ink-3 mt-1">
                    가격 흐름 · 재무 시계열 · 보조지표를 종합한 리포트
                  </p>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0">
                  <div ref={modelRef} className="relative">
                    <button
                      onClick={() => setIsModelOpen((prev) => !prev)}
                      className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-surface border border-line text-sm"
                    >
                      <span className="text-ink-3 text-[11px]">모델</span>
                      <span className="font-semibold text-ink">{llmVendor}</span>
                      {isModelOpen
                        ? <ChevronUp size={14} className="text-ink-3 pointer-events-none" />
                        : <ChevronDown size={14} className="text-ink-3 pointer-events-none" />}
                    </button>
                    {isModelOpen && (
                      <div className="absolute right-0 bottom-full mb-1 w-full bg-surface border border-line rounded-lg shadow-pop z-50 max-h-60 overflow-y-auto">
                        {LLM_VENDOR_OPTIONS.map((vendor) => (
                          <button
                            key={vendor}
                            onClick={() => { setLlmVendor(vendor); setIsModelOpen(false); }}
                            className={`w-full text-left px-3.5 py-2.5 text-sm first:rounded-t-lg last:rounded-b-lg ${
                              vendor === llmVendor
                                ? "bg-accent-soft text-accent font-semibold"
                                : "text-ink hover:bg-bg-sunk"
                            }`}
                          >
                            {vendor}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  <button
                    onClick={handleAnalyzeClick}
                    disabled={loading}
                    className="px-5 py-2.5 rounded-lg bg-ink text-bg font-semibold text-sm
                               hover:opacity-90 disabled:opacity-50 transition-opacity tracking-tight"
                  >
                    분석 결과 보기 →
                  </button>
                </div>
              </section>
            )}

            {/* ========== 하단 분석 결과 영역 ========== */}
            {!analysisData && !loading && !err && (
              <div className="w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center py-24 bg-bg-sunk border-line text-ink-4">
                <p className="text-base font-medium">분석 결과가 여기에 표시됩니다</p>
                <p className="text-sm mt-1">분석을 실행해주세요</p>
              </div>
            )}

            {(loading || !!err || !!analysisData) && <div ref={pdfRef}>
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
                llmVendor={llmVendor}
                onLlmVendorChange={setLlmVendor}
                displayText={analysisData?.explain?.text ?? ""}
                financialTimeline={financialTimeline}
                priceFlowSummary={priceFlowSummary}
                layout="full"
              />
            </div>}
          </main>
          </>
        )}

        {isAnalysisModalOpen && analysisResult && (
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
                  {mainStock.name} ({mainStock.symbol}) 분석 결과
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
                  llmVendor={llmVendor}
                  onLlmVendorChange={setLlmVendor}
                  displayText={analysisData?.explain?.text ?? ""}
                  financialTimeline={financialTimeline}
                  priceFlowSummary={priceFlowSummary}
                  layout="full"
                />
              </div>
            </div>
          </div>
        )}

        <FinancialDetailModal
          isOpen={isFinModalOpen}
          onClose={() => setIsFinModalOpen(false)}
          ticker={mainStock.symbol}
          companyName={mainStock.name}
        />

        {/* 토스트 메시지 */}
        {toast.visible && (
          <div
            className="fixed bottom-8 left-1/2 -translate-x-1/2
                       bg-ink/80 text-bg px-4 py-2 rounded-lg
                       text-sm sm:text-base"
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
  const isEmpty = value === "-" || value === "" || value == null;
  return (
    <div className={`px-3 py-2 bg-surface flex flex-col ${className}`}>
      <div className="flex justify-between items-center gap-2">
        <span className="text-ink text-sm sm:text-base font-semibold whitespace-nowrap">
          <DictTerm term={title}>{title}</DictTerm>
        </span>
        <span
          className={`text-sm sm:text-base whitespace-nowrap flex-shrink-0 font-mono tabular ${
            isEmpty ? "text-ink-4 font-normal" : "text-ink font-semibold"
          }`}
        >
          {isEmpty ? "—" : value}
        </span>
      </div>

      <div className="h-[3px] sm:h-[4px]" />

      <span className="text-ink-3 text-[11px] sm:text-xs font-normal leading-tight truncate">
        <DictTerm term={subtitle}>{subtitle}</DictTerm>
      </span>

      <div className="h-[3px] sm:h-[4px]" />
    </div>
  );
}

type IndicatorSectionBlockProps = {
  section: IndicatorSection;
  layout: "2-2-2" | "2-2" | "2-2-1" | "2";
};

function IndicatorSectionBlock({
  section,
  layout,
}: IndicatorSectionBlockProps) {
  const rows = section.rows;

  if (layout === "2-2-2") {
    const chunks = [rows.slice(0, 2), rows.slice(2, 4), rows.slice(4, 6)];

    return (
      <div className="flex flex-col gap-2.5">
        <div className="text-ink text-base sm:text-lg font-semibold">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </div>
        <div className="border-2 border-line-strong rounded-xl overflow-hidden divide-y divide-line-strong">
          {chunks.map((chunk, rowIdx) => (
            <div key={rowIdx} className="grid grid-cols-2 divide-x divide-line-strong">
              {chunk.map((cell, idx) => (
                <Cell
                  key={`${cell.title}-${idx}`}
                  title={cell.title}
                  subtitle={cell.subtitle}
                  value={cell.value}
                />
              ))}
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (layout === "2-2-1") {
    const row1 = rows.slice(0, 2);
    const row2 = rows.slice(2, 4);
    const last = rows[4];

    return (
      <div className="flex flex-col gap-2.5">
        <div className="text-ink text-base sm:text-lg font-semibold">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </div>
        <div className="border-2 border-line-strong rounded-xl overflow-hidden divide-y divide-line-strong">
          <div className="grid grid-cols-2 divide-x divide-line-strong">
            {row1.map((cell, idx) => (
              <Cell
                key={`${cell.title}-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
              />
            ))}
          </div>
          <div className="grid grid-cols-2 divide-x divide-line-strong">
            {row2.map((cell, idx) => (
              <Cell
                key={`${cell.title}-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
              />
            ))}
          </div>
          {last && (
            <div className="grid grid-cols-1">
              <Cell
                title={last.title}
                subtitle={last.subtitle}
                value={last.value}
              />
            </div>
          )}
        </div>
      </div>
    );
  }

  if (layout === "2-2") {
    return (
      <div className="flex flex-col gap-2.5">
        <div className="text-ink text-base sm:text-lg font-semibold">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 border-2 border-line-strong rounded-xl overflow-hidden">
          {rows.map((cell, idx) => (
            <Cell
              key={`${cell.title}-${idx}`}
              title={cell.title}
              subtitle={cell.subtitle}
              value={cell.value}
              className={`${idx < 2 ? "border-b border-line-strong" : ""} ${
                idx % 2 === 0 ? "sm:border-r sm:border-line-strong" : ""
              }`}
            />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2.5">
      <div className="text-ink text-base sm:text-lg font-semibold">
        {section.sectionTitle}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 border-2 border-line-strong rounded-xl overflow-hidden">
        {rows.map((cell, idx) => (
          <Cell
            key={`${cell.title}-${idx}`}
            title={cell.title}
            subtitle={cell.subtitle}
            value={cell.value}
            className={idx % 2 === 0 ? "sm:border-r sm:border-line-strong" : ""}
          />
        ))}
      </div>
    </div>
  );
}
