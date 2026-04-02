// Feature2MockPage.tsx
import { useState, useRef, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import StockInputBox from "../components/StockInputBox";
import StockCard from "../components/StockCard";
import { fetchCandles } from "../api/charts";
import type { Candle } from "../types/candle";
import TradingViewWidget from "../components/TradingViewWidget";
import clsx from "clsx";
import AnalysisResultPanel from "../components/AnalysisResultPanel";
import { fetchFeature2Analysis } from "../api/feature2";
import RelativeLineWidget from "../components/RelativeLineWidget";
import type { Feature2AnalyzeResponse, PeerItem } from "../types/feature2";
import type { ApiResponse } from "../types/common/api";
import { fetchNewsByStock } from "../api/news";
import type { NewsItemDto } from "../types/news";
import { getStockByCode } from "../api/stock";
import { isLoggedIn } from "../utils/auth";

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

const relationLabel: Record<string, string> = {
  LEADER: "선행",
  FOLLOWER: "후행",
  COINCIDENT: "동행",
  UNKNOWN: "-",
};

const formatTimeAgo = (isoStr: string): string => {
  const diff = Date.now() - new Date(isoStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}분 전`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;
  return new Date(isoStr).toLocaleDateString("ko-KR");
};

export default function Feature2MockPage() {
  const navigate = useNavigate();
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

    // 종목명 조회
    try {
      const stockInfo = await getStockByCode(q);
      if (stockInfo) {
        setMainStock({ name: stockInfo.companyName || q, symbol: q });
      }
    } catch {
      setMainStock({ name: q, symbol: q });
    }

    // 뉴스 조회
    setNewsLoading(true);
    setNewsError(null);
    setNewsItems([]);
    try {
      const news = await fetchNewsByStock(q);
      setNewsItems(news);
    } catch {
      setNewsError("뉴스를 불러오지 못했습니다.");
    } finally {
      setNewsLoading(false);
    }

    await loadCandles(q);
  };

  const [newsItems, setNewsItems] = useState<NewsItemDto[]>([]);
  const [newsLoading, setNewsLoading] = useState(false);
  const [newsError, setNewsError] = useState<string | null>(null);

  const [hoveredDayKey, setHoveredDayKey] = useState<string | null>(null);
  const [relatedStocks, setRelatedStocks] = useState<PeerItem[]>([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [relatedError, setRelatedError] = useState<string | null>(null);

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
      const result = await fetchFeature2Analysis(mainStock.symbol);
      setAnalysisResult(result);

      // 분석 응답의 뉴스 데이터로 갱신
      const analyzeNews = result?.data?.metrics?.newsList;
      if (analyzeNews && analyzeNews.length > 0) {
        setNewsItems(analyzeNews);
      }

      // 유사 종목 데이터 갱신
      const peers = result?.data?.metrics?.peerCluster?.peers;
      if (peers && peers.length > 0) {
        setRelatedStocks(peers);
      }
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

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[60px]">
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
            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex items-center justify-between">
                <h3 className="text-black text-base sm:text-lg font-medium">기준금리</h3>
                {(() => {
                  const br = analysisData?.metrics?.baseRate;
                  if (!br) {
                    return (
                      <span className="text-sm text-gray-400">데이터 없음</span>
                    );
                  }
                  return (
                    <div className="flex items-baseline gap-2">
                      <span className="text-lg sm:text-xl font-bold text-black">
                        {br.value}{br.unit}
                      </span>
                      <span className="text-xs sm:text-sm text-gray-500">
                        {br.date} 기준
                      </span>
                    </div>
                  );
                })()}
              </div>
            </section>

            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  {mainStock.name} 관련 뉴스
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
                  <div className="flex flex-col min-h-[230px] max-h-[250px] overflow-y-auto">
                    {newsItems.map((item, idx) => (
                      <a
                        key={item.newsId}
                        href={item.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-4 px-2.5 py-2 bg-white border-t ${
                          idx === newsItems.length - 1 ? "border-b" : ""
                        } border-zinc-300 hover:bg-zinc-50 transition-colors`}
                      >
                        <div className="flex-1 flex flex-col gap-2">
                          <div className="flex flex-col">
                            <h4 className="text-black text-sm sm:text-base font-semibold line-clamp-1">
                              {item.title}
                            </h4>
                            <p className="text-black text-xs sm:text-sm leading-snug line-clamp-2">
                              {item.summary}
                            </p>
                          </div>
                          <p className="text-black text-[11px] sm:text-xs font-medium">
                            {formatTimeAgo(item.publishedAt)} • {item.publisher}
                          </p>
                        </div>
                      </a>
                    ))}

                    {newsItems.length === 0 && (
                      <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
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
                  {mainStock.name} 관련 산업 유사 종목
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
                  <div className="flex flex-col min-h-[230px] max-h-[250px] overflow-y-auto">
                    {relatedStocks.map((row, idx) => (
                      <div
                        key={row.stockCode}
                        className={`flex items-center justify-between gap-3 px-2.5 py-2.5 bg-white border-t ${
                          idx === relatedStocks.length - 1 ? "border-b" : ""
                        } border-zinc-300`}
                      >
                        <div className="flex-1 min-w-0">
                          <p className="text-xs sm:text-sm font-medium text-black truncate">
                            {row.companyName}
                          </p>
                          <p className="text-[10px] sm:text-xs text-gray-500">
                            {row.stockCode}
                          </p>
                        </div>

                        <div className="flex items-center gap-3 flex-shrink-0">
                          <div className="flex flex-col items-center gap-0.5 w-14">
                            <span className="text-[10px] text-gray-400">상관계수</span>
                            <span className={clsx(
                              "text-xs sm:text-sm font-semibold",
                              row.corr >= 0.7 ? "text-red-600" : row.corr >= 0.4 ? "text-orange-500" : "text-gray-700",
                            )}>
                              {row.corr.toFixed(2)}
                            </span>
                          </div>

                          <div className="flex flex-col items-center gap-0.5 w-12">
                            <span className="text-[10px] text-gray-400">관계</span>
                            <span className={clsx(
                              "text-[11px] sm:text-xs font-medium",
                              row.relation === "LEADER" ? "text-blue-600" : row.relation === "FOLLOWER" ? "text-purple-600" : "text-gray-600",
                            )}>
                              {relationLabel[row.relation] ?? "-"}
                            </span>
                          </div>

                          <div className="flex flex-col items-center gap-0.5 w-12">
                            <span className="text-[10px] text-gray-400">점수</span>
                            <span className="text-xs sm:text-sm font-semibold text-black">
                              {row.score.toFixed(2)}
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}

                    {relatedStocks.length === 0 && (
                      <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
                        표시할 유사 종목이 없습니다.
                      </div>
                    )}
                  </div>
                )}
              </div>
            </section>

            {/* [우측 최하단] 공매도 카드 */}
            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  {mainStock.name} 공매도 현황
                </h3>

                {(() => {
                  const ss = analysisData?.metrics?.shortSelling;
                  if (!ss) {
                    return (
                      <div className="min-h-[230px] flex items-center justify-center text-sm text-gray-500">
                        공매도 데이터가 없습니다.
                      </div>
                    );
                  }

                  const fmt = (v: number) => v.toLocaleString("ko-KR");
                  const pct = (v: number) => `${v.toFixed(2)}%`;

                  const rows: { label: string; value: string }[] = [
                    { label: "기준일", value: ss.reportDate },
                    { label: "공매도 거래량", value: fmt(ss.shortVolumeTotal) },
                    { label: "총 거래량", value: fmt(ss.totalVolume) },
                    { label: "공매도 거래량 비율", value: pct(ss.shortVolumeRatio) },
                    { label: "공매도 거래대금", value: `${fmt(ss.shortAmountTotal)}원` },
                    { label: "총 거래대금", value: `${fmt(ss.totalAmount)}원` },
                    { label: "공매도 거래대금 비율", value: pct(ss.shortAmountRatio) },
                  ];

                  return (
                    <div className="flex flex-col overflow-y-auto">
                      {rows.map((row, idx) => (
                        <div
                          key={row.label}
                          className={`flex items-center justify-between px-2.5 py-2 border-t ${
                            idx === rows.length - 1 ? "border-b" : ""
                          } border-zinc-300`}
                        >
                          <span className="text-xs sm:text-sm text-gray-600">{row.label}</span>
                          <span className="text-xs sm:text-sm font-semibold text-black">{row.value}</span>
                        </div>
                      ))}
                    </div>
                  );
                })()}
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