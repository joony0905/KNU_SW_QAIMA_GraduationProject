// frontend/src/pages/StocksMockPage.tsx
import TradingViewWidget from "../components/TradingViewWidget";
import { useRef, useEffect, useState } from "react";
import api from "../lib/apiClient";
import StockCard from "../components/StockCard";
import StockInputBox from "../components/StockInputBox";
import { Star } from "lucide-react";

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

  const handleSearch = (value: string) => {
    console.log("검색 실행:", value);
    // TODO: 선택된 종목으로 API 호출 연결
  };

  const [analysisResult, setAnalysisResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const [activeTab, setActiveTab] = useState<"재무제표" | "공매도">("재무제표");

  const handleAnalyzeClick = async () => {
    setLoading(true);
    setErr("");
    setAnalysisResult(null);

    try {
      const res = await api.get("/api/v1/stocks/marketstack/ticker/AAPL");
      setAnalysisResult(res);
    } catch (e: any) {
      setErr(e?.message || "요청 중 오류가 발생했습니다.");
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

  // 워치리스트용 타입/데이터
  interface WatchStock {
    name: string;
    price: string;
    volume: string;
    change: string;
    changeRate: string;
  }

  const [watchStocks, setWatchStocks] = useState<WatchStock[]>([
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
    name: "애플",
    symbol: "AAPL",
    price: "267.99",
    change: "+3.1",
    changeRate: "+3.27%",
  });

  const watchlistWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  // ★ 별 토글 상태 (상단 메인 종목용)
  const [isInterested, setIsInterested] = useState(false);

  // 워치리스트 패널 위치
  const [panelPos, setPanelPos] = useState<{ top: number; left: number } | null>(
    null
  );

  // 바깥 클릭 시 워치리스트 닫힘
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        watchlistWrapperRef.current?.contains(e.target as Node) ||
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
      // TODO: 실제 관심종목 API 연동
      // if (isInterested) {
      //   await api.delete(`/api/v1/interests/${mainStock.symbol}`);
      // } else {
      //   await api.post(`/api/v1/interests`, { symbol: mainStock.symbol });
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

  // 토픽 선택 상태
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
    return () =>
      document.removeEventListener("click", handleClickOutsideTopic);
  }, []);

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            심층분석
          </h1>
        </header>

        {/* 종목 검색 / 내 관심 영역 */}
        <section className="w-full flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          {/* 왼쪽: 국내 / 종목 입력 */}
          <div className="w-full lg:max-w-md bg-white rounded-[10px] outline outline-1 outline-stone-300 px-2 py-1 sm:px-2 sm:py-1 flex flex-col gap-2">
            <StockInputBox
              placeholder="종목을 입력해주세요"
              onSearch={handleSearch}
            />
          </div>

          {/* 오른쪽: 내 관심 / 워치리스트 */}
          <div
            ref={watchlistWrapperRef}
            className="w-full lg:flex-1 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-end"
          >
            {/* 내 관심 드롭다운 */}
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
                        // TODO: topic별 우측 워치리스트 API 호출
                      }}
                      className={`
                        w-full text-left px-3 py-2 text-sm sm:text-base
                        hover:bg-zinc-100
                        ${
                          topic === item
                            ? "bg-zinc-100 font-semibold"
                            : ""
                        }
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
              {/* 대표 카드: 항상 watchStocks[0] */}
              <div
                ref={cardRef}
                className="inline-block w-[260px] sm:w-[280px] lg:w-[380px]"
              >
                {watchStocks[0] && (
                  <StockCard
                    name={watchStocks[0].name}
                    price={watchStocks[0].price}
                    volume={watchStocks[0].volume}
                    change={watchStocks[0].change}
                    changeRate={watchStocks[0].changeRate}
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

        {/* 펼쳐진 워치리스트 패널: 대표 포함 통짜 리스트 */}
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
              {watchStocks.map((stock, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    // TODO: 여기서 이 종목을 검색결과로 띄우는 함수만 호출
                    // 예: handleSearch(stock.symbol);

                    setMainStock({
                      name: stock.name,
                      symbol: "AAPL", // 나중에 실제 symbol 필드로 교체
                      price: stock.price,
                      change: stock.change,
                      changeRate: stock.changeRate,
                    });

                    setIsOpen(false);
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
                    <div className="flex items-center gap-1.5 text-red-600 text-sm md:text-base font-medium">
                      <span>{mainStock.change}</span>
                      <span>({mainStock.changeRate})</span>
                      <div className="w-3 h-3 bg-red-600 rounded-sm" />
                    </div>
                  </div>
                </div>

                {/* 차트 영역 */}
                <div className="w-full flex-1 bg-white rounded-xl overflow-hidden">
                  <TradingViewWidget />
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
                  <>
                    {/* 수익성 */}
                    <div className="flex flex-col gap-2.5">
                      <div className="text-black text-base sm:text-lg md:text-xl font-normal">
                        수익성
                      </div>
                      <div className="border border-stone-300 rounded-2xl overflow-hidden">
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
                          <Cell
                            title="EPS"
                            subtitle="주당순이익"
                            value="12.5%"
                            className="border-r border-b"
                          />
                          <Cell
                            title="ROE"
                            subtitle="자기자본이익률"
                            value="12.5%"
                            className="border-r border-b"
                          />
                          <Cell
                            title="ROA"
                            subtitle="총자산이익률"
                            value="12.5%"
                            className="border-b"
                          />
                        </div>
                        <div className="grid grid-cols-2">
                          <Cell
                            title="Operating Margin"
                            subtitle="영업이익률"
                            value="12.5%"
                            className="border-r"
                          />
                          <Cell
                            title="Net Margin"
                            subtitle="순이익률"
                            value="12.5%"
                          />
                        </div>
                      </div>
                    </div>

                    {/* 가치(밸류에이션) */}
                    <div className="flex flex-col gap-2.5">
                      <div className="text-black text-base sm:text-lg md:text-xl font-normal">
                        가치(밸류에이션)
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 border border-stone-300 rounded-2xl overflow-hidden">
                        <Cell
                          title="PER"
                          subtitle="주가수익비율"
                          value="20배"
                          className="border-r border-b"
                        />
                        <Cell
                          title="PBR"
                          subtitle="주가순자산비율"
                          value="2배"
                          className="border-b"
                        />
                        <Cell
                          title="PSR"
                          subtitle="주가매출비율"
                          value="2배"
                          className="border-r"
                        />
                        <Cell
                          title="BPS"
                          subtitle="주당순자산가치"
                          value="12.5%"
                        />
                      </div>
                    </div>

                    {/* 재무안정성 */}
                    <div className="flex flex-col gap-2.5">
                      <div className="text-black text-base sm:text-lg md:text-xl font-normal">
                        재무안정성
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 border border-stone-300 rounded-2xl overflow-hidden">
                        <Cell
                          title="Debt Ratio"
                          subtitle="부채비율"
                          value="35%"
                          className="border-r"
                        />
                        <Cell
                          title="Interest Coverage Ratio"
                          subtitle="이자보상비율"
                          value="12.5%"
                        />
                      </div>
                    </div>

                    {/* 유동성 */}
                    <div className="flex flex-col gap-2.5">
                      <div className="text-black text-base sm:text-lg md:text-xl font-normal">
                        유동성
                      </div>
                      <div className="grid grid-cols-1 sm:grid-cols-2 border border-stone-300 rounded-2xl overflow-hidden">
                        <Cell
                          title="Current Ratio"
                          subtitle="유동비율"
                          value="12.5%"
                          className="border-r"
                        />
                        <Cell
                          title="Quick Ratio"
                          subtitle="당좌비율"
                          value="12.5%"
                        />
                      </div>
                    </div>
                  </>
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
