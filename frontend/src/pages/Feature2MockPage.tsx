// Feature2MockPage.tsx
import { useState, useRef, useEffect, useMemo } from "react";
import StockInputBox from "../components/StockInputBox";
import StockCard from "../components/StockCard";
import { fetchCandles } from "../api/charts";
import type { Candle } from "../types/candle";
import TradingViewWidget from "../components/TradingViewWidget";
import clsx from "clsx";
import AnalysisResultPanel from "../components/AnalysisResultPanel";
import { fetchFeature2Analysis, fetchFeature2NewsList } from "../api/feature2";
import RelativeLineWidget from "../components/RelativeLineWidget";
import type { Feature2AnalyzeResponse, NewsListItem } from "../types/feature2";
import type { ApiResponse } from "../types/common/api";

const getColorClass = (rate: string) => {
  if (rate.startsWith("+")) return "text-red-600";
  if (rate.startsWith("-")) return "text-blue-600";
  return "text-black";
};

interface FeaturedStock {
  name: string;
  symbol: string;
  price: string;
  volume: string;
  change: string;
  changeRate: string;
}

type NewsCardItem = {
  id: string;
  title: string;
  summary: string;
  source: string;
  timeAgo: string;
  url: string;
  thumbnailUrl?: string;
};

type RelatedStock = {
  id: string;
  name: string;
  price: string;
  volume: string;
  diff: string;
  rate: string;
  direction: "up" | "down" | "flat";
};

const dummyNews: NewsCardItem[] = [
  {
    id: "1",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "3시간 전",
    url: "#",
  },
  {
    id: "2",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "5시간 전",
    url: "#",
  },
  {
    id: "3",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "어제",
    url: "#",
  },
];

const dummyRelatedStocks: RelatedStock[] = [
  {
    id: "skhynix",
    name: "SK하이닉스",
    price: "612,000",
    volume: "9,922,488",
    diff: "6,000",
    rate: "+0.99%",
    direction: "up",
  },
  {
    id: "samsung",
    name: "삼성전자",
    price: "104,100",
    volume: "45,942,879",
    diff: "3,500",
    rate: "+3.48%",
    direction: "up",
  },
  {
    id: "samsung-2",
    name: "삼성전자",
    price: "104,100",
    volume: "45,942,879",
    diff: "3,500",
    rate: "+3.48%",
    direction: "up",
  },
  {
    id: "kodex",
    name: "KODEX 레버리지",
    price: "44,430",
    volume: "35,605,843",
    diff: "830",
    rate: "+1.90%",
    direction: "up",
  },
  {
    id: "ecopro",
    name: "에코프로",
    price: "94,100",
    volume: "12,469,599",
    diff: "6,200",
    rate: "+7.05%",
    direction: "flat",
  },
  {
    id: "inverse",
    name: "KODEX 200선물인버스2X",
    price: "692",
    volume: "1,630,500,839",
    diff: "14",
    rate: "-0.99%",
    direction: "down",
  },
];

export default function Feature2MockPage() {
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);

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
        toDate.toISOString(),
      );

      if (response.data.length === 0) {
        setChartError("차트 데이터가 없습니다.");
      }

      setCandles(response.data);
    } catch (e: any) {
      console.error("차트 데이터 조회 실패:", e);
      setChartError("차트를 불러오지 못했습니다.");
      setCandles([]);
    } finally {
      setChartLoading(false);
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
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [displayText, setDisplayText] = useState("");

  const [mainStock, setMainStock] = useState({
    name: "삼성전자",
    symbol: "005930",
  });

  const industrySeries = useMemo(() => {
  const raw = analysisData?.metrics?.industryIndex?.series;

  console.log("🔥 [RAW industryIndex.series]", raw);

  if (!raw || !Array.isArray(raw)) return [];

  const mapped = raw
    .filter(
      (p: any) =>
        p &&
        typeof p.t === "string" &&
        Number.isFinite(Number(p.value)),
    )
    .map((p: any) => ({
      t: p.t,
      value: Number(p.value),
    }));

  console.log("🔥 [MAPPED industrySeries]", mapped);

  return mapped;
}, [analysisData]);

  const handleSearch = async (value: string) => {
    const q = value.trim();
    if (!q) return;

    setMainStock((prev) => ({ ...prev, symbol: q }));
    await loadCandles(q);
  };

  const [newsItems, setNewsItems] = useState<NewsCardItem[]>(dummyNews);
  const [newsLoading, setNewsLoading] = useState(false);
  const [newsError, setNewsError] = useState<string | null>(null);

  const [hoveredDayKey, setHoveredDayKey] = useState<string | null>(null);
  const [relatedStocks] = useState<RelatedStock[]>(dummyRelatedStocks);
  const [relatedLoading] = useState(false);
  const [relatedError] = useState<string | null>(null);

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
    if (!analysisData?.explain) {
      setDisplayText("");
      return;
    }

    const fullText = analysisData.explain;
    setDisplayText("");
    let index = 0;
    const speed = 20;

    const timer = setInterval(() => {
      index += 1;
      setDisplayText((prev) => prev + fullText.charAt(index - 1));
      if (index >= fullText.length) clearInterval(timer);
    }, speed);

    return () => clearInterval(timer);
  }, [analysisData]);

  useEffect(() => {
    if (loading) {
      setIndustryChartLoading(true);
      setIndustryChartError(null);
      return;
    }

    setIndustryChartLoading(false);

    if (!analysisData) {
      setIndustryChartError(null);
      return;
    }

    const industryIndex = analysisData?.metrics?.industryIndex;
    const warnings: string[] = analysisResult?.meta?.warnings ?? [];

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
  }, [analysisResult, loading]);

  const handleAnalyzeClick = async () => {
    setLoading(true);
    setErr("");
    setAnalysisResult(null);
    setDisplayText("");

    try {
      setShowAnalyzeButton(false);
      const result = await fetchFeature2Analysis(mainStock.symbol);
      setAnalysisResult(result);
      console.log("🔥 analysisResult raw =", result);
      console.log("🔥 result keys =", Object.keys(result ?? {}));
      console.log("🔥 result.data =", result?.data);
      console.log("🔥 result.metrics =", result?.data?.metrics);
    } catch {
      setErr("분석 결과를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!mainStock) return;
    loadCandles(mainStock.symbol);
  }, [mainStock]);

  useEffect(() => {
    const loadNewsList = async () => {
      if (!mainStock.symbol) return;

      setNewsLoading(true);
      setNewsError(null);
      try {
        const response = await fetchFeature2NewsList(mainStock.symbol);
        const items = (response.data ?? []).map(mapNewsItemToCard);
        setNewsItems(items);
      } catch (e) {
        console.error("뉴스 리스트 조회 실패:", e);
        setNewsItems([]);
        setNewsError("뉴스를 불러오지 못했습니다.");
      } finally {
        setNewsLoading(false);
      }
    };

    loadNewsList();
  }, [mainStock.symbol]);

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            외부요인
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

        <main className="w-full mt-6 flex flex-col xl:flex-row justify-center items-start gap-6">
          <div className="flex-1 flex flex-col gap-5">
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-3">
              <div className="flex items-baseline gap-1">
                <h2 className="text-lg sm:text-2xl font-medium text-black">
                  {mainStock.name}
                </h2>
                <span className="text-sm sm:text-base text-black">
                  ({mainStock.symbol})
                </span>
              </div>

              <div className="w-full h-64 sm:h-80 bg-white rounded-xl overflow-hidden">
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
                  hoveredDayKey={hoveredDayKey}
                  onHoverDayKeyChange={setHoveredDayKey}
                  />
                )}
              </div>
            </section>

            {/* [좌측 하단] 산업 지수 차트 카드 */}
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-2.5 overflow-hidden">
              <h2 className="text-black text-lg sm:text-2xl font-medium">
                {mainStock.name}({mainStock.symbol}) 관련 산업 지수
              </h2>

              <div className="w-full h-64 sm:h-80 bg-white rounded-xl overflow-hidden">
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
              height={320}
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

          <div className="w-full xl:w-[380px] 2xl:w-[420px] flex flex-col items-stretch gap-5">
            <section className="w-full flex justify-center items-center gap-6 sm:gap-10">
              <div className="flex flex-col items-center gap-1 w-40">
                <p className="text-center text-sm sm:text-base font-medium text-black">
                  부정 지수 -0.28
                </p>
                <p className="text-center text-xs sm:text-sm text-black">
                  원가 부담 확대 우려
                </p>
              </div>

              <div className="hidden sm:block w-px h-12 bg-zinc-600" />

              <div className="flex flex-col items-center gap-1 w-44">
                <p className="text-center text-sm sm:text-base font-medium text-black">
                  긍정 지수 1.24
                </p>
                <p className="text-center text-xs sm:text-sm text-black">
                  반도체 수요 회복 기대
                </p>
              </div>
            </section>

            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  삼성전자 관련 뉴스
                </h3>

                {newsLoading && (
                  <div className="h-24 flex items-center justify-center text-sm text-gray-500">
                    뉴스를 불러오는 중입니다…
                  </div>
                )}

                {newsError && !newsLoading && (
                  <div className="h-24 flex items-center justify-center text-sm text-red-500">
                    {newsError ?? "뉴스를 불러오지 못했습니다."}
                  </div>
                )}

                {!newsLoading && !newsError && (
                  <div className="flex flex-col max-h-72 overflow-y-auto">
                    {newsItems.map((item, idx) => (
                      <a
                        key={item.id}
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        className={`flex items-center gap-4 px-2.5 py-2 bg-white border-t ${
                          idx === newsItems.length - 1 ? "border-b" : ""
                        } border-zinc-300 hover:bg-zinc-50 transition-colors`}
                      >
                        <div className="w-20 h-16 bg-zinc-300 rounded-2xl flex-shrink-0" />
                        <div className="flex-1 flex flex-col gap-2">
                          <div className="flex flex-col">
                            <h4 className="text-black text-sm sm:text-base font-semibold">
                              {item.title}
                            </h4>
                            <p className="text-black text-xs sm:text-sm leading-snug">
                              {item.summary}
                            </p>
                          </div>
                          <p className="text-black text-[11px] sm:text-xs font-medium">
                            {item.timeAgo} • {item.source}
                          </p>
                        </div>
                      </a>
                    ))}

                    {newsItems.length === 0 && (
                      <div className="py-6 text-center text-sm text-gray-500">
                        표시할 뉴스가 없습니다.
                      </div>
                    )}
                  </div>
                )}
              </div>
            </section>

            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  삼성전자 관련 산업 유사 종목
                </h3>

                {relatedLoading && (
                  <div className="h-24 flex items-center justify-center text-sm text-gray-500">
                    유사 종목을 불러오는 중입니다…
                  </div>
                )}

                {relatedError && !relatedLoading && (
                  <div className="h-24 flex items-center justify-center text-sm text-red-500">
                    {relatedError ?? "유사 종목을 불러오지 못했습니다."}
                  </div>
                )}

                {!relatedLoading && !relatedError && (
                  <div className="flex flex-col max-h-72 overflow-y-auto">
                    {relatedStocks.map((row, idx) => (
                      <div
                        key={row.id}
                        className={`flex items-center justify-between gap-4 px-2.5 py-2 bg-white border-t ${
                          idx === relatedStocks.length - 1 ? "border-b" : ""
                        } border-zinc-300`}
                      >
                        <div className="flex items-center gap-4">
                          <div className="w-28 sm:w-32 text-xs sm:text-sm font-medium text-black">
                            {row.name}
                          </div>

                          <div className="w-24 sm:w-28 flex flex-col items-end">
                            <div
                              className={`text-right text-sm sm:text-base font-semibold ${
                                row.direction === "up"
                                  ? "text-red-600"
                                  : "text-blue-700"
                              }`}
                            >
                              {row.price}
                            </div>
                            <div className="text-right text-[10px] sm:text-xs text-black">
                              {row.volume}
                            </div>
                          </div>

                          <div className="w-4 flex items-center justify-center">
                            {row.direction === "flat" ? (
                              <span className="text-2xl font-extrabold leading-none text-black">
                                -
                              </span>
                            ) : (
                              <span
                                className={clsx(
                                  "text-xs sm:text-sm font-bold leading-none",
                                  row.direction === "up"
                                    ? "text-red-600"
                                    : "text-blue-700",
                                )}
                              >
                                {row.direction === "up" ? "▲" : "▼"}
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="w-16 sm:w-20 flex flex-col items-end gap-0.5">
                          <div
                            className={`text-xs sm:text-sm font-semibold ${
                              row.direction === "up"
                                ? "text-red-600"
                                : "text-blue-700"
                            }`}
                          >
                            {row.diff}
                          </div>
                          <div
                            className={`text-[10px] sm:text-xs ${
                              row.direction === "up"
                                ? "text-red-600"
                                : "text-blue-700"
                            }`}
                          >
                            {row.rate}
                          </div>
                        </div>
                      </div>
                    ))}

                    {relatedStocks.length === 0 && (
                      <div className="py-6 text-center text-sm text-gray-500">
                        표시할 유사 종목이 없습니다.
                      </div>
                    )}
                  </div>
                )}
              </div>
            </section>
          </div>
        </main>

        <AnalysisResultPanel
          result={
            analysisResult
              ? {
                  explain: { text: analysisData?.explain },
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
          displayText={displayText}
          layout="full"
        />

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
      </div>
    </div>
  );
}

function mapNewsItemToCard(item: NewsListItem): NewsCardItem {
  return {
    id: String(item.newsId),
    title: item.title,
    summary: item.summary ?? "",
    source: item.publisher,
    timeAgo: formatPublishedAt(item.publishedAt),
    url: item.url,
  };
}

function formatPublishedAt(publishedAt: string): string {
  const target = new Date(publishedAt);
  if (Number.isNaN(target.getTime())) {
    return publishedAt;
  }

  const diffMs = Date.now() - target.getTime();
  const diffMinutes = Math.max(0, Math.floor(diffMs / 60000));

  if (diffMinutes < 1) return "방금 전";
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}일 전`;

  return target.toLocaleDateString("ko-KR");
}
