// frontend/src/pages/StocksMockPage.tsx
import { isLoggedIn } from "../utils/auth";
import { useNavigate } from "react-router-dom";
import TradingViewWidget from "../components/TradingViewWidget";
import AnalysisResultPanel from "../components/AnalysisResultPanel";
import type { AnalysisPanelResult } from "../types/analysisPanel";
import { useMemo, useRef, useEffect, useState } from "react";
import StockCard from "../components/StockCard";
import StockInputBox from "../components/StockInputBox";
import { Star } from "lucide-react";
import { type IndicatorSection } from "../mocks/financialIndicators";
import type { FinancialDto } from "../types/financial";
import { buildSectionsFromDto } from "../mappers/financialMapper";
import { fetchFinancials, fetchFinancialsByYear, fetchMarketSnapshot } from "../api/financial";
import type { MarketSnapshotDto } from "../types/financial";
import { fetchAnalysis } from "../api/analysis";
import { getStockByCode } from "../api/stock";
import { fetchCandles, fetchCandlesBefore } from "../api/charts";
import type { Candle } from "../types/candle";
import jsPDF from "jspdf";
import downloadIcon from "../assets/download_button.png";
import zoomIcon from "../assets/zoom_button.png";
import type { AnalysisResponse, ParsedExplainText } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";

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

type ExplainParseResult = {
  parsed: ParsedExplainText | null;
  parseFailed: boolean;
};

const decodeQuotedItems = (chunk: string): string[] => {
  const items: string[] = [];
  const regex = /"((?:\\.|[^"\\])*)"/g;
  let match: RegExpExecArray | null = null;

  while ((match = regex.exec(chunk)) !== null) {
    try {
      items.push(JSON.parse(`"${match[1]}"`));
    } catch {
      items.push(match[1]);
    }
  }

  return items;
};

const parseExplainText = (text?: string | null): ExplainParseResult => {
  if (!text || !text.trim()) return { parsed: null, parseFailed: false };

  const trimmed = text.trim();
  const extractSection = (source: string, startKey: string, endKeys: string[]) => {
    const start = source.indexOf(startKey);
    if (start === -1) return "";

    const from = start + startKey.length;
    const candidateEnds = endKeys
      .map((key) => source.indexOf(key, from))
      .filter((idx) => idx !== -1);
    const to = candidateEnds.length > 0 ? Math.min(...candidateEnds) : source.length;
    return source.slice(from, to);
  };

  try {
    const parsed = JSON.parse(trimmed);
    if (parsed && typeof parsed === "object") {
      return { parsed: parsed as ParsedExplainText, parseFailed: false };
    }
  } catch {
    // fall through to tolerant parsing
  }

  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start !== -1 && end !== -1 && end > start) {
    const candidate = trimmed.slice(start, end + 1);
    try {
      const parsed = JSON.parse(candidate);
      if (parsed && typeof parsed === "object") {
        return { parsed: parsed as ParsedExplainText, parseFailed: false };
      }
    } catch {
      // continue with regex-based fallback
    }
  }

  const summaryChunk = extractSection(trimmed, `"summary":[`, [`,"risks":[`, `"risks":[`, `,"conclusion":"`, `"conclusion":"`]);
  const risksChunk = extractSection(trimmed, `"risks":[`, [`,"conclusion":"`, `"conclusion":"`]);

  const summaryMatch = summaryChunk.match(/(.*)/s);
  const risksMatch = risksChunk.match(/(.*)/s);
  const conclusionMatch = trimmed.match(
    /"conclusion"\s*:\s*"((?:\\.|[^"\\])*)"/s,
  );

  const summary = summaryMatch
    ? decodeQuotedItems(summaryMatch[1]).slice(0, 3)
    : [];
  const risks = risksMatch ? decodeQuotedItems(risksMatch[1]).slice(0, 2) : [];

  let conclusion: string | undefined;
  if (conclusionMatch?.[1]) {
    try {
      conclusion = JSON.parse(`"${conclusionMatch[1]}"`);
    } catch {
      conclusion = conclusionMatch[1];
    }
  }

  if (
    summary.length > 0 ||
    risks.length > 0 ||
    (conclusion && conclusion.trim())
  ) {
    return {
      parsed: {
        summary: summary.length > 0 ? summary : undefined,
        risks: risks.length > 0 ? risks : undefined,
        conclusion,
      },
      parseFailed: false,
    };
  }

  return { parsed: null, parseFailed: true };
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

  const explainParse = useMemo(
    () => parseExplainText(analysisData?.explain?.text),
    [analysisData?.explain?.text],
  );

  useEffect(() => {
    if (analysisData?.explain?.text && explainParse.parseFailed) {
      console.warn(
        "[analysis] explain JSON parse failed",
        analysisData.explain.text,
      );
    }
  }, [analysisData?.explain?.text, explainParse.parseFailed]);

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

  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const [hasSelectedStock, setHasSelectedStock] = useState(false);
  const [financial, setFinancial] = useState<FinancialDto | null>(null);
  const [snapshot, setSnapshot] = useState<MarketSnapshotDto | null>(null);
  const [sections, setSections] = useState<IndicatorSection[]>([]);
  const [isAnalysisModalOpen, setIsAnalysisModalOpen] = useState(false);
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [analysisZoom, setAnalysisZoom] = useState(1); // 1 = 100%
  const [displayText, setDisplayText] = useState("");

  const [isOpen, setIsOpen] = useState(false);

  const [finTab, setFinTab] = useState<"single" | "trend">("single");
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
        toDate.toISOString(),
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
    setIndicatorData(null);
    setShowAnalyzeButton(true);

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
      }
      setTrendData(Array.isArray(trend) ? trend : []);
    } catch (e) {
      console.error("재무제표 조회 실패:", e);
    }

    // 초기 표시만 30일
    await loadCandles(code, "INITIAL");
  };

  const handleAnalyzeClick = async () => {
    if (!isLoggedIn()) {
      sessionStorage.setItem("qaima_redirect", window.location.pathname + window.location.search);
      navigate("/login");
      return;
    }
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
      setShowAnalyzeButton(false); // ✅ 결과가 생기면 버튼 숨김
      setDisplayText(""); // 타이핑용 초기화
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

      const ind = result?.data?.metrics?.indicators;

      setIndicatorData({
        ema: ind?.ema ?? null,
        bb20_2: ind?.bb20_2 ?? null,
        stoch14_3_3: ind?.stoch14_3_3 ?? null,
        warnings: ind?.warnings ?? [],
      });
    } catch (e) {
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
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
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
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

  // 1) 타이핑 효과는 그대로 유지
  useEffect(() => {
    if (!analysisData) {
      setDisplayText("");
      return;
    }

    const fullText = analysisData.explain?.text ?? "";

    setDisplayText("");

    let index = 0;
    const speed = 20;

    const timer = setInterval(() => {
      index += 1;
      setDisplayText((prev) => prev + fullText.charAt(index - 1));
      if (index >= fullText.length) {
        clearInterval(timer);
      }
    }, speed);

    return () => clearInterval(timer);
  }, [analysisData]);

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

              <div className="w-full h-[520px] px-4 sm:px-6 py-5 bg-zinc-100 rounded-2xl flex flex-col overflow-hidden">
                <h2 className="w-full text-[20px] font-semibold mb-3 text-center">
                  재무제표
                </h2>

                {/* 탭 */}
                <div className="flex gap-2 mb-3">
                  {(["single", "trend"] as const).map((tab) => (
                    <button
                      key={tab}
                      onClick={() => setFinTab(tab)}
                      className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                        finTab === tab
                          ? "bg-zinc-800 text-white"
                          : "bg-white text-zinc-600 hover:bg-zinc-200"
                      }`}
                    >
                      {tab === "single" ? "단일 조회" : "5개년 추이"}
                    </button>
                  ))}
                </div>

                {finTab === "single" ? (
                  <div className="flex flex-col flex-1 min-h-0">
                    {/* 드롭박스 영역 */}
                    <div className="flex flex-wrap gap-2 mb-3">
                      {/* 연도 */}
                      <select
                        value={selectedYear}
                        onChange={async (e) => {
                          const yr = Number(e.target.value);
                          setSelectedYear(yr);
                          try {
                            const data = await fetchFinancialsByYear(mainStock.symbol, yr, selectedPeriodType, selectedPeriodNo);
                            if (data.length > 0) { setFinancial(data[0]); setSections(buildSectionsFromDto(data[0], snapshot)); }
                          } catch (err) { console.error("재무제표 조회 실패:", err); }
                        }}
                        className="px-3 py-1.5 rounded-lg bg-white text-sm text-zinc-700 border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-zinc-400"
                      >
                        {Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i).map((yr) => (
                          <option key={yr} value={yr}>{yr}년</option>
                        ))}
                      </select>

                      {/* 기간 유형 */}
                      <select
                        value={selectedPeriodType}
                        onChange={async (e) => {
                          const pt = e.target.value as "A" | "Q" | "H";
                          setSelectedPeriodType(pt);
                          setSelectedPeriodNo(undefined);
                          try {
                            const data = await fetchFinancialsByYear(mainStock.symbol, selectedYear, pt, undefined);
                            if (data.length > 0) { setFinancial(data[0]); setSections(buildSectionsFromDto(data[0], snapshot)); }
                          } catch (err) { console.error("재무제표 조회 실패:", err); }
                        }}
                        className="px-3 py-1.5 rounded-lg bg-white text-sm text-zinc-700 border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-zinc-400"
                      >
                        <option value="A">연간</option>
                        <option value="Q">분기</option>
                        <option value="H">반기</option>
                      </select>

                      {/* 분기/반기 번호 (조건부) */}
                      {selectedPeriodType === "Q" && (
                        <select
                          value={selectedPeriodNo ?? ""}
                          onChange={async (e) => {
                            const no = e.target.value ? Number(e.target.value) : undefined;
                            setSelectedPeriodNo(no);
                            try {
                              const data = await fetchFinancialsByYear(mainStock.symbol, selectedYear, selectedPeriodType, no);
                              if (data.length > 0) { setFinancial(data[0]); setSections(buildSectionsFromDto(data[0], snapshot)); }
                            } catch (err) { console.error("재무제표 조회 실패:", err); }
                          }}
                          className="px-3 py-1.5 rounded-lg bg-white text-sm text-zinc-700 border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-zinc-400"
                        >
                          <option value="">전체</option>
                          <option value="1">1분기</option>
                          <option value="2">2분기</option>
                          <option value="3">3분기</option>
                          <option value="4">4분기</option>
                        </select>
                      )}
                      {selectedPeriodType === "H" && (
                        <select
                          value={selectedPeriodNo ?? ""}
                          onChange={async (e) => {
                            const no = e.target.value ? Number(e.target.value) : undefined;
                            setSelectedPeriodNo(no);
                            try {
                              const data = await fetchFinancialsByYear(mainStock.symbol, selectedYear, selectedPeriodType, no);
                              if (data.length > 0) { setFinancial(data[0]); setSections(buildSectionsFromDto(data[0], snapshot)); }
                            } catch (err) { console.error("재무제표 조회 실패:", err); }
                          }}
                          className="px-3 py-1.5 rounded-lg bg-white text-sm text-zinc-700 border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-zinc-400"
                        >
                          <option value="">전체</option>
                          <option value="1">상반기</option>
                          <option value="2">하반기</option>
                        </select>
                      )}
                    </div>

                    {/* 지표 카드 */}
                    <div className="flex-1 overflow-y-auto flex flex-col gap-5 pr-2">
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
                ) : (
                  /* 5개년 추이 탭 */
                  <div className="flex-1 overflow-auto min-h-0">
                    {trendData.length > 0 ? (
                      <table className="w-full text-sm border-collapse">
                        <thead>
                          <tr className="bg-zinc-200">
                            <th className="sticky left-0 bg-zinc-200 px-3 py-2 text-left font-semibold text-zinc-700 whitespace-nowrap">지표</th>
                            {trendData.map((d) => (
                              <th key={d.year} className="px-3 py-2 text-right font-semibold text-zinc-700 whitespace-nowrap">{d.year}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {([
                            ["ROE (%)", "roe"],
                            ["영업이익률 (%)", "operatingMargin"],
                            ["순이익률 (%)", "netMargin"],
                            ["PER", "per"],
                            ["PBR", "pbr"],
                            ["부채비율 (%)", "debtRatio"],
                            ["매출액", "revenue"],
                            ["영업이익", "operatingIncome"],
                            ["당기순이익", "netIncome"],
                            ["자산총계", "assets"],
                            ["부채총계", "liabilities"],
                            ["자본총계", "equity"],
                            ["시가총액", "marketCap"],
                          ] as [string, keyof FinancialDto][]).map(([label, key], idx) => (
                            <tr key={key} className={idx % 2 === 0 ? "bg-white" : "bg-zinc-50"}>
                              <td className="sticky left-0 px-3 py-2 font-medium text-zinc-700 whitespace-nowrap" style={{ background: "inherit" }}>{label}</td>
                              {trendData.map((d) => {
                                const v = d[key];
                                const formatted = v == null ? "-" : typeof v === "number" ? v.toLocaleString() : String(v);
                                return (
                                  <td key={d.year} className="px-3 py-2 text-right text-zinc-600 whitespace-nowrap">{formatted}</td>
                                );
                              })}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <p className="text-sm text-gray-500 text-center mt-8">5개년 추이 데이터가 없습니다.</p>
                    )}
                  </div>
                )}
              </div>
            </div>
            {/* ========== 하단 분석 결과 영역 ========== */}
            <AnalysisResultPanel
              result={analysisData ? {
                ...analysisData,
                explain: {
                  ...analysisData.explain,
                  parsed: explainParse.parsed,
                },
                meta: analysisResult?.meta,
              } as AnalysisPanelResult : null}
              loading={loading}
              err={err}
              showAnalyzeButton={showAnalyzeButton}
              onAnalyze={handleAnalyzeClick}
              onDownload={handleDownloadClick}
              onZoom={() => {
                setAnalysisZoom(1);
                setIsAnalysisModalOpen(true);
              }}
              displayText={displayText}
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
                <div className="flex flex-col gap-6">
                  {/* 설명 섹션 */}
                  <div>
                    <h3 className="text-base sm:text-lg font-semibold text-zinc-900">설명</h3>
                    {explainParse.parsed ? (
                      <div className="mt-2 text-sm sm:text-base text-zinc-700 flex flex-col gap-3">
                        <div>
                          <p className="font-medium text-zinc-900">요약</p>
                          <ul className="list-disc list-inside">
                            {(explainParse.parsed.summary ?? []).slice(0, 3).map((item, idx) => (
                              <li key={`modal-summary-${idx}`}>{item}</li>
                            ))}
                          </ul>
                        </div>
                        <div>
                          <p className="font-medium text-zinc-900">리스크</p>
                          <ul className="list-disc list-inside">
                            {(explainParse.parsed.risks ?? []).slice(0, 2).map((item, idx) => (
                              <li key={`modal-risk-${idx}`}>{item}</li>
                            ))}
                          </ul>
                        </div>
                        <div>
                          <p className="font-medium text-zinc-900">결론</p>
                          <p>{explainParse.parsed.conclusion ?? "-"}</p>
                        </div>
                      </div>
                    ) : analysisData?.explain?.text?.trim() ? (
                      <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                        {analysisData?.explain?.text}
                      </p>
                    ) : (
                      <p className="text-sm sm:text-base text-zinc-500 mt-2">
                        설명 생성이 비활성화되었거나 실패했습니다.
                      </p>
                    )}
                  </div>

                  {/* OHLCV 요약 */}
                  {analysisData?.metrics?.ohlcvSummary && (
                    <div>
                      <h3 className="text-base sm:text-lg font-semibold text-zinc-900">OHLCV 요약</h3>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm sm:text-base text-zinc-700 mt-2">
                        <div>캔들 수: {analysisData.metrics.ohlcvSummary.count.toLocaleString()}</div>
                        <div>기간: {formatDate(analysisData.metrics.ohlcvSummary.from)} ~ {formatDate(analysisData.metrics.ohlcvSummary.to)}</div>
                        <div>마지막 종가: {formatNumber(analysisData.metrics.ohlcvSummary.lastClose)}</div>
                      </div>
                    </div>
                  )}

                  {/* Indicator Summary */}
                  {analysisData?.metrics?.indicatorSummary !== undefined && (
                    <div>
                      <h3 className="text-base sm:text-lg font-semibold text-zinc-900">Indicator Summary</h3>
                      <p className="text-sm sm:text-base text-zinc-700 whitespace-pre-wrap mt-2">
                        {analysisData.metrics.indicatorSummary?.trim()
                          ? analysisData.metrics.indicatorSummary
                          : "지표 요약 데이터를 생성하지 못했습니다."}
                      </p>
                    </div>
                  )}

                  {/* 재무 요약 (5개년) */}
                  {analysisData?.metrics?.financialSummary && (
                    <div>
                      <h3 className="text-base sm:text-lg font-semibold text-zinc-900">재무 요약 (5개년)</h3>
                      {analysisData.metrics.financialSummary.years.length > 0 ? (
                        <div className="overflow-x-auto mt-2">
                          <table className="min-w-full text-xs sm:text-sm text-zinc-700 border border-zinc-200">
                            <thead className="bg-zinc-100 text-zinc-900">
                              <tr>
                                <th className="px-3 py-2 text-left border-b">구분</th>
                                {analysisData.metrics.financialSummary.years.map((year) => (
                                  <th key={year} className="px-3 py-2 text-right border-b">{year}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              <tr>
                                <td className="px-3 py-2 border-b">매출</td>
                                {analysisData.metrics.financialSummary.years.map((year) => (
                                  <td key={`modal-rev-${year}`} className="px-3 py-2 text-right border-b">
                                    {formatNumber(analysisData.metrics!.financialSummary!.revenue[String(year)])}
                                  </td>
                                ))}
                              </tr>
                              <tr>
                                <td className="px-3 py-2 border-b">영업이익</td>
                                {analysisData.metrics.financialSummary.years.map((year) => (
                                  <td key={`modal-op-${year}`} className="px-3 py-2 text-right border-b">
                                    {formatNumber(analysisData.metrics!.financialSummary!.operatingIncome[String(year)])}
                                  </td>
                                ))}
                              </tr>
                              <tr>
                                <td className="px-3 py-2 border-b">순이익</td>
                                {analysisData.metrics.financialSummary.years.map((year) => (
                                  <td key={`modal-net-${year}`} className="px-3 py-2 text-right border-b">
                                    {formatNumber(analysisData.metrics!.financialSummary!.netIncome[String(year)])}
                                  </td>
                                ))}
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <p className="text-sm sm:text-base text-zinc-500 mt-2">재무 요약 데이터를 확보하지 못했습니다.</p>
                      )}
                    </div>
                  )}

                  {/* 경고 */}
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
              </div>
            </div>
          </div>
        )}

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
          {section.sectionTitle}
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
          {section.sectionTitle}
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
