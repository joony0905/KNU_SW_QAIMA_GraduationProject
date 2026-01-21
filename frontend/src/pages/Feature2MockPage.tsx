// Feature2MockPage.tsx
import { useState, useRef, useEffect } from "react";
import StockInputBox from "../components/StockInputBox";
import StockCard from "../components/StockCard";

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

type NewsItem = {
  id: string;
  title: string;
  summary: string;
  source: string;
  timeAgo: string;
  thumbnailUrl?: string;
};

type RelatedStock = {
  id: string;
  name: string;
  price: string;
  volume: string;
  diff: string;
  rate: string;
  direction: "up" | "down";
};

const dummyNews: NewsItem[] = [
  {
    id: "1",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "3시간 전",
  },
  {
    id: "2",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "5시간 전",
  },
  {
    id: "3",
    title: "Title",
    summary: "blablablabla~~~~~~~~~~~~~~~ blablablabla...",
    source: "출간사 이름",
    timeAgo: "어제",
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
    id: "samsung",
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
    direction: "up",
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

  const [mainStock] = useState({
    name: "삼성전자",
    symbol: "005930",
  });

  const featuredListWrapperRef = useRef<HTMLDivElement | null>(null);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  const [isOpen, setIsOpen] = useState(false);
  const [panelPos, setPanelPos] = useState<{
    top: number;
    left: number;
  } | null>(null);

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

  const handleSearch = (value: string) => {
    console.log("Feature2 search:", value);
  };

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

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            외부요인
          </h1>
        </header>

        {/* 상단 검색 / 특징주 */}
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

        {/* 메인 2열 레이아웃 */}
        <main className="w-full mt-6 flex flex-col xl:flex-row justify-center items-start gap-6">
          {/* ============ 좌측: 종목 차트 + 산업 지수 ============ */}
          <div className="flex-1 flex flex-col gap-5">
            {/* [좌측 상단] 종목 차트 카드 */}
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-2.5 overflow-hidden">
              <h2 className="text-black text-lg sm:text-2xl font-medium">
                {mainStock.name}({mainStock.symbol})
              </h2>
              {/* 실제 차트 자리 (지금은 placeholder) */}
              <div className="w-full h-64 sm:h-80 bg-white rounded-xl flex items-center justify-center text-sm text-gray-500">
                종목 차트 영역
              </div>
              {/* 예: 나중에 <TradingViewWidget candles={...} />로 교체 */}
            </section>

            {/* [좌측 하단] 산업 지수 차트 카드 */}
            <section className="w-full bg-zinc-100 rounded-2xl p-4 sm:p-5 flex flex-col gap-2.5 overflow-hidden">
              <h2 className="text-black text-lg sm:text-2xl font-medium">
                {mainStock.name}({mainStock.symbol}) 관련 산업 지수
              </h2>
              {/* 산업군 차트 자리 */}
              <div className="w-full h-64 sm:h-80 bg-white rounded-xl flex items-center justify-center text-sm text-gray-500">
                산업군 차트 영역
              </div>
            </section>
          </div>

          {/* ============ 우측: 감성 지수 + 뉴스 + 관련 종목 ============ */}
          <div className="w-full xl:w-[380px] 2xl:w-[420px] flex flex-col items-stretch gap-5">
            {/* [우측 최상단] 부정/긍정 지수 영역 */}
            <section className="w-full flex justify-center items-center gap-6 sm:gap-10">
              {/* 부정 지수 */}
              <div className="flex flex-col items-center gap-1 w-40">
                <p className="text-center text-sm sm:text-base font-medium text-black">
                  부정 지수 -0.28
                </p>
                <p className="text-center text-xs sm:text-sm text-black">
                  원가 부담 확대 우려
                </p>
              </div>

              {/* 가운데 구분선 */}
              <div className="hidden sm:block w-px h-12 bg-zinc-600" />

              {/* 긍정 지수 */}
              <div className="flex flex-col items-center gap-1 w-44">
                <p className="text-center text-sm sm:text-base font-medium text-black">
                  긍정 지수 1.24
                </p>
                <p className="text-center text-xs sm:text-sm text-black">
                  반도체 수요 회복 기대
                </p>
              </div>
            </section>

            {/* [우측 중단] 관련 뉴스 카드 */}
            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              {/* 왼쪽: 뉴스 리스트 */}
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  삼성전자 관련 뉴스
                </h3>

                {/* 뉴스 스크롤 영역 (API 연동 시 items.map 으로 대체) */}
                <div className="flex flex-col max-h-72 overflow-y-auto">
                  {dummyNews.map((item, idx) => (
                    <article
                      key={item.id}
                      className={`flex items-center gap-4 px-2.5 py-2 bg-white border-t ${
                        idx === dummyNews.length - 1 ? "border-b" : ""
                      } border-zinc-300`}
                    >
                      {/* 썸네일: Builder 비율에 가깝게 조금 작게 */}
                      <div className="w-20 h-16 bg-zinc-300 rounded-2xl flex-shrink-0" />

                      {/* 텍스트 영역 */}
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
                    </article>
                  ))}
                </div>
              </div>
            </section>

            {/* [우측 하단] 관련 산업 유사 종목 리스트 */}
            <section className="w-full bg-white rounded-2xl border-[3px] border-stone-300 px-3 py-3">
              <div className="flex flex-col gap-3">
                <h3 className="text-black text-base sm:text-lg font-medium">
                  삼성전자 관련 산업 유사 종목
                </h3>

                {/* 뉴스 섹션과 동일한 max-h / 스크롤 / 여백 구조 */}
                <div className="flex flex-col max-h-72 overflow-y-auto">
                  {dummyRelatedStocks.map((row, idx) => (
                    <div
                      key={row.id}
                      className={`flex items-center justify-between gap-4 px-2.5 py-2 bg-white border-t ${
                        idx === dummyRelatedStocks.length - 1 ? "border-b" : ""
                      } border-zinc-300`}
                    >
                      {/* 왼쪽: 종목명 + 현재가/거래량 + 막대 */}
                      <div className="flex items-center gap-4">
                        {/* 종목명 */}
                        <div className="w-28 sm:w-32 text-xs sm:text-sm font-medium text-black">
                          {row.name}
                        </div>

                        {/* 현재가 / 거래량 */}
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

                        {/* 빨간/파란 막대 (뉴스 썸네일 자리에 해당하는 작은 블록) */}
                        <div
                          className={`w-3 h-3 rounded-sm ${
                            row.direction === "up"
                              ? "bg-red-600"
                              : "bg-blue-700 rotate-180"
                          }`}
                        />
                      </div>

                      {/* 오른쪽: 전일대비 / 등락률 */}
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
                </div>
              </div>
            </section>
          </div>
        </main>

        {/* 분석 결과 보기 영역 */}
        <section className="w-full bg-zinc-100 rounded-2xl py-8 sm:py-10 flex flex-col items-center justify-center gap-4 mt-6">
          <button className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium">
            분석 결과 보기
          </button>
        </section>

        {/* 펼쳐진 특징주 리스트 패널 */}
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
