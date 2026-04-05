// frontend/src/pages/StocksMockPage.tsx
import { isLoggedIn } from "../utils/auth";
import { useNavigate } from "react-router-dom";
import TradingViewWidget from "../components/TradingViewWidget";
import AnalysisResultPanel from "../components/AnalysisResultPanel";
import type { AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import { useRef, useEffect, useState } from "react";
import StockCard from "../components/StockCard";
import StockInputBox from "../components/StockInputBox";
import { Star } from "lucide-react";
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
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";
import type { AnalysisResponse } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";
import DictTerm from "../components/DictTerm";
import {
  formatKstDate,
  formatKstDateTimeDisplay,
  formatKstOffsetDateTime,
  shiftKstDays,
  shiftKstYears,
} from "../utils/kst";

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

  const [isOpen, setIsOpen] = useState(false);

  const [isFinModalOpen, setIsFinModalOpen] = useState(false);
  const [selectedYear, setSelectedYear] = useState<number>(2024);
  const [selectedPeriodType, setSelectedPeriodType] = useState<"A" | "Q" | "H">("A");
  const [selectedPeriodNo, setSelectedPeriodNo] = useState<number | undefined>(undefined);
  const [trendData, setTrendData] = useState<FinancialDto[]>([]);

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
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
      setAnalysisLoadingStage("");
      setLoading(false);
    }
  };

  const handleDownloadClick = () => {
    if (!analysisResult) return;

    const doc = new jsPDF();

    const title = `${mainStock.name} (${mainStock.symbol}) 심층 분석`;
    const summary = analysisData?.summary ?? "";
    const rating = analysisData?.rating ?? "";
    const highlights = (analysisData?.highlights ?? []).join("\n- ");
    const risks = (analysisData?.risks ?? []).join("\n- ");

    let y = 20;

    doc.setFontSize(16);
    doc.text(title, 10, y);
    y += 10;

    doc.setFontSize(12);
    doc.text(`투자의견: ${rating}`, 10, y);
    y += 10;

    if (summary) {
      doc.setFontSize(12);
      doc.text("요약", 10, y);
      y += 7;
      doc.setFontSize(11);
      const summaryLines = doc.splitTextToSize(summary, 180);
      doc.text(summaryLines, 10, y);
      y += summaryLines.length * 6 + 5;
    }

    if (highlights) {
      doc.setFontSize(12);
      doc.text("핵심 포인트", 10, y);
      y += 7;
      doc.setFontSize(11);
      const lines = doc.splitTextToSize(`- ${highlights}`, 180);
      doc.text(lines, 10, y);
      y += lines.length * 6 + 5;
    }

    if (risks) {
      doc.setFontSize(12);
      doc.text("리스크", 10, y);
      y += 7;
      doc.setFontSize(11);
      const lines = doc.splitTextToSize(`- ${risks}`, 180);
      doc.text(lines, 10, y);
    }

    const fileName = `${mainStock.symbol}_analysis.pdf`;
    doc.save(fileName);
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

  const featuredListWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  const featuredStocks = [
    {
      name: "삼성전자",
      symbol: "005930",
      price: "72,800",
      volume: "14,293,826",
      change: "+800",
      changeRate: "+1.11%",
    },
  ];

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

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[60px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            심층분석
          </h1>
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
              <div
                ref={cardRef}
                className="inline-block w-[260px] sm:w-[280px] lg:w-[380px]"
              >
                <StockCard
                  name={featuredStocks[0].name}
                  price={featuredStocks[0].price}
                  volume={featuredStocks[0].volume}
                  change={featuredStocks[0].change}
                  changeRate={featuredStocks[0].changeRate}
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

                  <div className="w-full flex-1 bg-white rounded-xl overflow-hidden min-h-0 flex flex-col">
                    {/* 차트영역은 stretch + min-h-0 */}
                    <div className="flex-1 min-h-0 w-full flex items-stretch">
                      {chartLoading && (
                        <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                          <p className="text-sm sm:text-base text-gray-600">
                            차트를 불러오는 중입니다...
                          </p>
                        </div>
                      )}

                      {chartError && !chartLoading && (
                        <div className="flex-1 min-h-0 w-full flex items-center justify-center">
                          <p className="text-sm sm:text-base text-red-600">
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
                  </div>
                </section>
              </div>

              <div className="w-full h-[520px] px-4 sm:px-6 py-5 bg-zinc-100 rounded-2xl flex flex-col">
                <div className="flex items-center justify-between mb-3">
                  <h2 className="text-[20px] font-semibold">
                    투자지표
                  </h2>
                  <button
                    onClick={() => setIsFinModalOpen(true)}
                    className="px-4 py-1.5 rounded-lg text-sm font-medium bg-zinc-800 text-white hover:bg-zinc-700 transition-colors"
                  >
                    재무제표
                  </button>
                </div>

                <div className="flex flex-col flex-1 min-h-0">

                  <div className="flex-1 overflow-y-auto flex flex-col gap-5 pr-2 pt-2">
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
                      <p className="text-sm text-gray-500">
                        해당 조건의 재무제표 데이터가 없습니다.
                      </p>
                    )}
                  </div>
                </div>
              </div>
            </div>
            {/* ========== 분석 기간 선택 ========== */}
            <div className="w-full bg-white rounded-2xl border border-stone-300 px-4 sm:px-6 py-4 flex flex-col gap-3">
              <h3 className="text-sm sm:text-base font-semibold text-zinc-800">분석 기간</h3>
              <div className="flex flex-col sm:flex-row gap-4 sm:gap-6">
                {/* 프리셋 버튼 */}
                <div className="flex flex-wrap gap-2">
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
                        className={`px-3 py-1.5 rounded-lg border text-sm transition-colors ${
                          isActive
                            ? "border-sky-500 bg-sky-50 text-sky-700 font-medium"
                            : "border-zinc-200 bg-zinc-50 text-zinc-600 hover:bg-zinc-100"
                        }`}
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>

                {/* 직접 날짜 입력 */}
                <div className="flex items-center gap-2 text-sm">
                  <input
                    type="date"
                    value={analysisFrom}
                    min={formatKstDate(shiftKstYears(new Date(), -5))}
                    max={analysisTo || formatKstDate(new Date())}
                    onChange={(e) => setAnalysisFrom(e.target.value)}
                    className="px-2 py-1.5 border border-zinc-300 rounded-lg text-zinc-700 focus:outline-none focus:ring-1 focus:ring-sky-400"
                  />
                  <span className="text-zinc-400">~</span>
                  <input
                    type="date"
                    value={analysisTo}
                    min={analysisFrom || formatKstDate(shiftKstYears(new Date(), -5))}
                    max={formatKstDate(new Date())}
                    onChange={(e) => setAnalysisTo(e.target.value)}
                    className="px-2 py-1.5 border border-zinc-300 rounded-lg text-zinc-700 focus:outline-none focus:ring-1 focus:ring-sky-400"
                  />
                </div>
              </div>
            </div>

            {/* ========== 하단 분석 결과 영역 ========== */}
            <AnalysisResultPanel
              result={analysisData ? {
                ...analysisData,
              } as AnalysisPanelResult : null}
              loading={loading}
              loadingStage={analysisLoadingStage}
              err={err}
              showAnalyzeButton={showAnalyzeButton}
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
          </main>
        )}

        {isAnalysisModalOpen && analysisResult && (
          <div
            className="fixed inset-0 z-[100] bg-black/50 flex items-center justify-center p-4"
            onClick={() => setIsAnalysisModalOpen(false)}
          >
            <div
              className="bg-white rounded-2xl w-full max-w-5xl max-h-[90vh] flex flex-col shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              {/* 상단 헤더 */}
              <div className="flex items-center justify-between px-5 py-3 border-b border-zinc-200">
                <h2 className="text-base sm:text-lg font-semibold text-zinc-900">
                  {mainStock.name} ({mainStock.symbol}) 분석 결과
                </h2>
                <button
                  onClick={() => setIsAnalysisModalOpen(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-zinc-100 text-zinc-500 hover:text-zinc-800 text-xl transition-colors"
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
          <DictTerm term={title}>{title}</DictTerm>
        </span>
        <span className="text-black text-sm sm:text-base font-semibold whitespace-nowrap">
          {value}
        </span>
      </div>

      <div className="h-[3px] sm:h-[4px]" />

      <span className="text-black text-[11px] sm:text-xs font-normal leading-tight">
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
        <div className="text-black text-base sm:text-lg md:text-xl font-normal">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </div>
        <div className="border border-stone-300 rounded-2xl overflow-hidden">
          {chunks.map((chunk, rowIdx) => (
            <div key={rowIdx} className="grid grid-cols-2">
              {chunk.map((cell, idx) => (
                <Cell
                  key={`${cell.title}-${idx}`}
                  title={cell.title}
                  subtitle={cell.subtitle}
                  value={cell.value}
                  className={`${rowIdx < chunks.length - 1 ? "border-b" : ""} ${idx === 0 ? "border-r" : ""}`}
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
        <div className="text-black text-base sm:text-lg md:text-xl font-normal">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
        </div>
        <div className="border border-stone-300 rounded-2xl overflow-hidden">
          <div className="grid grid-cols-2">
            {row1.map((cell, idx) => (
              <Cell
                key={`${cell.title}-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
                className={`border-b ${idx === 0 ? "border-r" : ""}`}
              />
            ))}
          </div>
          <div className="grid grid-cols-2">
            {row2.map((cell, idx) => (
              <Cell
                key={`${cell.title}-${idx}`}
                title={cell.title}
                subtitle={cell.subtitle}
                value={cell.value}
                className={`border-b ${idx === 0 ? "border-r" : ""}`}
              />
            ))}
          </div>
          {last && (
            <div className="grid grid-cols-1">
              <Cell
                title={last.title}
                subtitle={last.subtitle}
                value={last.value}
                className=""
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
        <div className="text-black text-base sm:text-lg md:text-xl font-normal">
          <DictTerm term={section.sectionTitle}>{section.sectionTitle}</DictTerm>
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
