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
import { fetchCandles } from "../api/charts";
import type { Candle } from "../types/candle";

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

export default function StocksMockPage() {
  const currentTime = useKSTTime();

  const loadCandles = async (stockCode: string) => {
    setChartLoading(true);
    setChartError(null);

    const toDate = new Date();
    const fromDate = new Date();
    fromDate.setDate(toDate.getDate() - 30);

    try {
      const response = await fetchCandles(
        stockCode,
        "ONE_D",
        fromDate.toISOString(),
        toDate.toISOString()
      );
      if (response.data.length === 0) {
        setChartError("차트 데이터가 없습니다.");
      }
      setCandles(response.data);
    } catch (e) {
      console.error("차트 데이터 조회 실패:", e);
      setChartError("차트를 불러오지 못했습니다.");
      setCandles([]);
    } finally {
      setChartLoading(false);
    }
  };

  const handleSearch = async (value: string) => {
    console.log("검색 실행:", value);

    if (!value.trim()) {
      setErr("종목을 입력해주세요.");
      setHasSelectedStock(false);
      return;
    }

    setErr("");
    setHasSelectedStock(true);

    // ===== 종목 상세 정보 조회 추가 =====
    try {
      const stockInfo = await getStockByCode(value);

      // mainStock 업데이트
      setMainStock({
        name: stockInfo.companyName,
        symbol: stockInfo.stockCode,
        price: stockInfo.price?.toString() || "0",
        change: stockInfo.changeRate
          ? (stockInfo.changeRate > 0 ? "+" : "") +
            stockInfo.changeRate.toFixed(2)
          : "0",
        changeRate: stockInfo.changeRate
          ? (stockInfo.changeRate > 0 ? "+" : "") +
            stockInfo.changeRate.toFixed(2) +
            "%"
          : "0%",
      });
    } catch (e) {
      console.error("종목 정보 조회 실패:", e);
      // 실패해도 계속 진행 (재무제표는 별도)
    }
    // ====================================

    // 재무제표 조회
    try {
      const financials = await fetchFinancials(value, 5);
      if (financials.length > 0) {
        const latest = financials[0];
        setFinancial(latest);
        setSections(buildSectionsFromDto(latest));
      }
    } catch (e) {
      console.error("재무제표 조회 실패:", e);
      setErr("재무제표 데이터를 불러오지 못했습니다.");
    }
    await loadCandles(value);
  };
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [analysisResult, setAnalysisResult] = useState<any>(null);
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

  const handleAnalyzeClick = async () => {
    setLoading(true);
    setErr("");
    setAnalysisResult(null);

    try {
      // TODO: mainStock.symbol을 실제 ticker로 교체 필요
      // 임시로 "005930" 또는 검색한 종목 코드 사용
      const result = await fetchAnalysis(mainStock.symbol);
      setAnalysisResult(result.analysis); // analysis 부분만 저장
    } catch (e) {
      console.error("분석 결과 조회 실패:", e);
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const [isOpen, setIsOpen] = useState(false);

  const getColorClass = (rate: string) => {
    if (rate.startsWith("+")) return "text-red-600";
    if (rate.startsWith("-")) return "text-blue-600";
    return "text-black";
  };

  // 특징주 리스트용 타입/데이터
  interface FeaturedStock {
    name: string;
    price: string;
    volume: string;
    change: string;
    changeRate: string;
  }

  const [featuredStocks, setFeaturedStocks] = useState<FeaturedStock[]>([
    {
      name: "삼성전자",
      price: "70,000",
      volume: "12,345,678",
      change: "500",
      changeRate: "+0.72%",
    },
    {
      name: "LG에너지솔루션",
      price: "400,000",
      volume: "3,210,987",
      change: "-2,000",
      changeRate: "-0.50%",
    },
    {
      name: "카카오",
      price: "55,000",
      volume: "8,765,432",
      change: "0",
      changeRate: "0%",
    },
  ]);

  // 메인 종목 (검색된 종목)
  const [mainStock, setMainStock] = useState({
    name: "삼성전자",
    symbol: "005930",
    price: "70,000",
    change: "+500",
    changeRate: "+0.72%",
  });

  useEffect(() => {
    void loadCandles(mainStock.symbol);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const featuredListWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  // ★ 별 토글 상태 (상단 메인 종목용 - 관심 종목 등록)
  const [isInterested, setIsInterested] = useState(false);

  // 특징주 리스트 패널 위치
  const [panelPos, setPanelPos] = useState<{
    top: number;
    left: number;
  } | null>(null);

  // 바깥 클릭 시 특징주 리스트 닫힘
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

  // 토스트 메시지 상태
  const [toast, setToast] = useState<{ message: string; visible: boolean }>({
    message: "",
    visible: false,
  });

  // 토스트 자동 숨김
  useEffect(() => {
    if (!toast.visible) return;
    const t = setTimeout(() => {
      setToast((prev) => ({ ...prev, visible: false }));
    }, 1800);
    return () => clearTimeout(t);
  }, [toast.visible]);

  // 관심 종목 토글 (상단 메인 종목용)
  const toggleInterest = async () => {
    console.log("★ toggleInterest clicked, isInterested =", isInterested);
    try {
      // TODO: 실제 관심종목(워치리스트) API 연동
      // if (isInterested) {
      //   await api.delete(`/api/v1/watchlist/items/${mainStock.symbol}`);
      // } else {
      //   await api.post(`/api/v1/watchlist/items`, { stockId: mainStock.id, watchlistId: 1 });
      // }

      setIsInterested((prev) => !prev);
      setToast({
        message: isInterested
          ? "관심종목에서 삭제되었습니다."
          : "관심종목에 추가되었습니다.",
        visible: true,
      });
    } catch (err) {
      console.error("관심 종목 토글 실패:", err);
    }
  };

  // 토픽(특징주 카테고리) 선택 상태
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

  // 바깥 클릭 시 토픽 닫힘
  useEffect(() => {
    const handleClickOutsideTopic = (e: MouseEvent) => {
      if (!topicRef.current) return;
      if (topicRef.current.contains(e.target as Node)) return;
      setIsTopicOpen(false);
    };

    document.addEventListener("click", handleClickOutsideTopic);
    return () => document.removeEventListener("click", handleClickOutsideTopic);
  }, []);

  // mainStock용 색/방향 계산 (StockCard와 동일한 규칙)
  const mainColorClass = getColorClass(mainStock.changeRate);
  const mainNumericChange = Number(mainStock.change.replace(/,/g, "").trim());
  const mainDisplayRate =
    mainNumericChange === 0 ? "0.00%" : mainStock.changeRate;

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            심층분석
          </h1>
        </header>

        {/* 종목 검색 / 특징주 리스트 영역 */}
        <section className="w-full flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          {/* 왼쪽: 국내 / 종목 입력 */}
          <div className="w-full lg:max-w-md bg-white rounded-[10px] outline outline-1 outline-stone-300 px-2 py-1 sm:px-2 sm:py-1 flex flex-col gap-2">
            <StockInputBox
              placeholder="종목을 입력해주세요"
              onSearch={handleSearch}
            />
          </div>

          {/* 오른쪽: 특징주 카테고리 드롭다운 + 리스트 */}
          <div
            ref={featuredListWrapperRef}
            className="w-full lg:flex-1 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-end"
          >
            {/* 특징주 카테고리 드롭다운 */}
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
                {/* 가운데 정렬 텍스트 */}
                <span
                  className={`
                    flex-1 text-center truncate whitespace-nowrap
                    text-xs sm:text-sm
                  `}
                >
                  {topic}
                </span>

                {/* 오른쪽 삼각형 */}
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
                        // TODO: topic별 특징주 리스트 API 호출
                      }}
                      className={`
                        w-full text-left px-3 py-2 text-sm sm:text-base
                        hover:bg-zinc-100
                        ${topic === item ? "bg-zinc-100 font-semibold" : ""}
                      `}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 대표 카드 + + 버튼 */}
            <div className="flex items-center gap-2 relative">
              {/* 대표 카드: 항상 featuredStocks[0] */}
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

              {/* + 버튼: 패널 열기 */}
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

        {/* 펼쳐진 특징주 리스트 패널: 대표 포함 통짜 리스트 */}
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
              {featuredStocks.map((stock, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    // 메인 종목 정보 업데이트
                    setMainStock({
                      name: stock.name,
                      symbol: "AAPL", // 나중에 실제 symbol 필드로 교체
                      price: stock.price,
                      change: stock.change,
                      changeRate: stock.changeRate,
                    });

                    setIsOpen(false);

                    // handleSearch 호출 → 차트 + 재무제표 자동 갱신
                    handleSearch(stock.name);
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

        {/* ========== 메인 2열 레이아웃 ========== */}
        {hasSelectedStock && (
          <main className="w-full flex flex-col gap-4 sm:gap-5">
            <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1.2fr)] gap-4 lg:gap-6 items-start">
              {/* ---------- 왼쪽: 차트 + 요약 ---------- */}
              <div className="flex flex-col gap-3 sm:gap-4">
                <section className="w-full bg-zinc-100 rounded-2xl p-3 sm:p-4 md:p-5 flex flex-col gap-3 xl:h-[520px]">
                  {/* 종목 헤더 */}
                  <div className="flex flex-col gap-1.5">
                    <div className="flex flex-wrap items-end gap-1.5">
                      <h2 className="text-lg sm:text-xl md:text-2xl font-medium text-black">
                        {mainStock.name}
                      </h2>
                      <span className="text-sm sm:text-base md:text-lg text-black">
                        ({mainStock.symbol})
                      </span>
                      {/* 상단 관심 토글 버튼 */}
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
                        {mainStock.price}
                      </span>

                      <div className="flex items-center gap-1.5 text-sm md:text-base font-medium">
                        {/* 금액 + 퍼센트 색은 특징주 리스트와 동일하게 */}
                        <span className={mainColorClass}>
                          {mainStock.change}
                        </span>
                        <span className={mainColorClass}>
                          ({mainDisplayRate})
                        </span>

                        {/* 방향 표시: 상승/하락/보합 → StockCard 삼각형 그대로 */}
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

                  {/* 차트 영역 */}
                  <div className="w-full flex-1 bg-white rounded-xl overflow-hidden flex items-center justify-center">
                    {chartLoading && (
                      <p className="text-sm sm:text-base text-gray-600">
                        차트를 불러오는 중입니다...
                      </p>
                    )}

                    {chartError && !chartLoading && (
                      <p className="text-sm sm:text-base text-red-600">
                        {chartError}
                      </p>
                    )}

                    {!chartLoading && !chartError && (
                      <TradingViewWidget candles={candles} />
                    )}
                  </div>
                </section>
              </div>

              {/* ---------- 오른쪽: 재무제표 / 공매도 카드 ---------- */}
              <div className="w-full h-[520px] px-4 sm:px-6 py-8 bg-zinc-100 rounded-2xl flex flex-col items-center overflow-hidden">
                {/* 탭 버튼 */}
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

                {/* 콘텐츠 영역 */}
                <div className="w-full h-full overflow-y-auto flex flex-col gap-5 pr-2">
                  {activeTab === "재무제표" ? (
                    sections ? (
                      sections.map((section) => (
                        <IndicatorSectionBlock
                          key={section.sectionTitle}
                          section={section}
                          // layout은 섹션 제목 기준으로 지금처럼 고정하거나,
                          // section에 layout 필드를 추가해도 됨
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

            {/* ========== 하단 분석 결과 영역 ========== */}
            <section className="w-full bg-zinc-100 rounded-2xl py-8 sm:py-10 flex flex-col items-center justify-center gap-4 mt-2">
              <button
                onClick={handleAnalyzeClick}
                className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
              >
                분석 결과 보기
              </button>

              {/* 로딩 */}
              {loading && (
                <p className="text-sm sm:text-base text-gray-600">
                  분석 중입니다...
                </p>
              )}

              {/* 에러 */}
              {err && (
                <p className="text-sm sm:text-base text-red-600">{err}</p>
              )}

              {/* JSON 결과 */}
              {analysisResult && (
                <pre className="w-[90%] max-w-4xl bg-[#020617] text-white text-xs sm:text-sm p-4 sm:p-5 rounded-xl overflow-x-auto whitespace-pre-wrap">
                  {JSON.stringify(analysisResult, null, 2)}
                </pre>
              )}
            </section>
          </main>
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
      {/* 윗줄: 영어 지표명 + 수치 */}
      <div className="flex justify-between items-center">
        <span className="text-black text-sm sm:text-base font-semibold">
          {title}
        </span>
        <span className="text-black text-sm sm:text-base font-semibold whitespace-nowrap">
          {value}
        </span>
      </div>

      <div className="h-[3px] sm:h-[4px]" />

      {/* 아랫줄: 한글 설명 */}
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

function IndicatorSectionBlock({
  section,
  layout,
}: IndicatorSectionBlockProps) {
  const rows = section.rows;

  if (layout === "3-2") {
    // 위 3개, 아래 2개 (수익성)
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
    // 2x2 (가치)
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

  // layout === "2" (재무안정성 / 유동성)
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
