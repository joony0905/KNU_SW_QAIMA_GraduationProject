// frontend/src/pages/StocksMockPage.tsx
import TradingViewWidget from "../components/TradingViewWidget";
import { useEffect, useState } from "react";

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
    const interval = setInterval(update, 1000 * 30); // 30초마다 업뎃

    return () => clearInterval(interval);
  }, []);

  return time;
}

export default function StocksMockPage() {
  const currentTime = useKSTTime();
  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      {/* 해상도별 최대 폭 조절 */}
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">

        {/* ================= 헤더 ================= */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            심층분석
          </h1>
        </header>

        {/* ========== 종목 검색 / 내 관심 영역 ========== */}
        <section className="w-full flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          {/* 왼쪽: 국내 / 종목 입력 */}
          <div className="w-full lg:max-w-md bg-white rounded-lg border border-stone-300 px-3 py-2 sm:px-4 sm:py-3 flex flex-col gap-2">
            <div className="flex gap-3 items-center">
              <button className="w-20 sm:w-24 h-8 sm:h-9 border border-black rounded-md flex items-center justify-center text-sm sm:text-base font-medium">
                국내
              </button>
              <div className="flex-1 flex items-center justify-between gap-2">
                <span className="text-zinc-500 text-xs sm:text-sm md:text-base font-medium">
                  종목을 입력해주세요
                </span>
                <div className="w-7 h-7 sm:w-8 sm:h-8 bg-zinc-200 rounded-full" />
              </div>
            </div>
          </div>

          {/* 오른쪽: 내 관심 / 워치리스트 카드 */}
          <div className="w-full lg:flex-1 flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-end">
            <button className="w-24 sm:w-28 h-8 sm:h-9 border border-black rounded-md flex items-center justify-center text-sm sm:text-base font-medium bg-white">
              내 관심
            </button>

            <div className="flex items-center gap-2 sm:gap-3">
              {/* 워치리스트 카드 */}
              <div className="min-w-[210px] sm:min-w-[230px] flex items-center gap-2 sm:gap-3 px-3 py-2 border-2 border-stone-300 bg-white">
                <div className="flex items-center gap-2">
                  <div className="text-black text-xs sm:text-sm font-medium">
                    SK하이닉스
                  </div>
                  <div className="flex flex-col items-end">
                    <div className="text-red-600 text-xs sm:text-sm font-semibold">
                      612,000
                    </div>
                    <div className="text-black text-[10px] sm:text-xs font-medium">
                      9,922,488
                    </div>
                  </div>
                </div>
                <div className="w-2.5 h-2.5 bg-red-600 rounded-sm" />
                <div className="flex flex-col items-end gap-0.5">
                  <div className="text-red-600 text-xs sm:text-sm font-semibold">
                    6,000
                  </div>
                  <div className="text-red-600 text-[10px] sm:text-xs font-medium">
                    +0.99%
                  </div>
                </div>
              </div>

              {/* + 버튼 */}
              <button className="w-7 h-7 sm:w-8 sm:h-8 bg-zinc-300 rounded-full flex items-center justify-center text-lg sm:text-xl">
                +
              </button>
            </div>
          </div>
        </section>

        {/* ========== 메인 2열 레이아웃 ========== */}
        <main className="w-full flex flex-col gap-4 sm:gap-5">
          {/* 큰 화면에서는 2열, 작은 화면에서는 1열 */}
          <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1.2fr)] gap-4 lg:gap-6 items-start">

            {/* ---------- 왼쪽: 차트 + 요약 ---------- */}
            <div className="flex flex-col gap-3 sm:gap-4">
              {/* 종목 상세 + 차트 카드 */}
              
              <section className="w-full bg-zinc-100 rounded-2xl p-3 sm:p-4 md:p-5 flex flex-col gap-3 xl:h-[520px]">
                {/* 종목 헤더 */}
                <div className="flex flex-col gap-1.5">
                  <div className="flex flex-wrap items-end gap-1.5">
                    <h2 className="text-lg sm:text-xl md:text-2xl font-medium text-black">
                      애플
                    </h2>
                    <span className="text-sm sm:text-base md:text-lg text-black">
                      (AAPL)
                    </span>
                  </div>
                
                  <span className="text-[10px] sm:text-xs font-medium text-black">
                  {currentTime} KST
                  </span>
                  
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-2xl md:text-3xl font-medium text-black">
                      267.99
                    </span>
                    <div className="flex items-center gap-1.5 text-red-600 text-sm md:text-base font-medium">
                      <span>+3.1</span>
                      <span>(+3.27%)</span>
                      <div className="w-3 h-3 bg-red-600 rounded-sm" />
                    </div>
                  </div>
                </div>

                {/* 차트 영역: 해상도에 따라 높이 변화 */}
                <div className="w-full flex-1 bg-white rounded-xl overflow-hidden">
                  <TradingViewWidget />
                </div>
              </section>
            </div>

            {/* ---------- 오른쪽: 재무제표 카드 ---------- */}
            <aside className="w-full bg-zinc-100 rounded-2xl px-4 sm:px-5 py-4 sm:py-5 flex flex-col gap-3 sm:gap-4 xl:h-[520px] overflow-y-auto">
              {/* 수익성 */}
              <section className="flex flex-col gap-2 sm:gap-3">
                <h3 className="text-base sm:text-lg md:text-xl font-normal text-black">
                  수익성
                </h3>
                <div className="w-full rounded-2xl overflow-hidden border border-stone-300">
                  <div className="grid grid-cols-3 divide-x divide-stone-300 border-b border-stone-300">
                    <Cell title="EPS" subtitle="주당순이익" value="12.5%" />
                    <Cell title="ROE" subtitle="자기자본이익률" value="12.5%" />
                    <Cell title="ROA" subtitle="총자산이익률" value="12.5%" />
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2">
                    <Cell
                      title="Operating Margin"
                      subtitle="영업이익률"
                      value="12.5%"
                    />
                    <Cell title="Net Margin" subtitle="순이익률" value="12.5%" />
                  </div>
                </div>
              </section>

              {/* 가치(밸류에이션) */}
              <section className="flex flex-col gap-2 sm:gap-3">
                <h3 className="text-base sm:text-lg md:text-xl font-normal text-black">
                  가치(밸류에이션)
                </h3>
                <div className="w-full rounded-2xl overflow-hidden border border-stone-300 grid grid-cols-2">
                  <Cell title="PER" subtitle="주가수익비율" value="20배" />
                  <Cell title="PBR" subtitle="주가순자산비율" value="2배" />
                  <Cell title="PSR" subtitle="주가매출비율" value="2배" />
                  <Cell title="BPS" subtitle="주당순자산가치" value="12.5%" />
                </div>
              </section>

              {/* 재무안정성 */}
              <section className="flex flex-col gap-2 sm:gap-3">
                <h3 className="text-base sm:text-lg md:text-xl font-normal text-black">
                  재무안정성
                </h3>
                <div className="w-full rounded-2xl overflow-hidden border border-stone-300 grid grid-cols-1 sm:grid-cols-2">
                  <Cell title="Debt Ratio" subtitle="부채비율" value="35%" />
                  <Cell
                    title="Interest Coverage Ratio"
                    subtitle="이자보상비율"
                    value="12.5%"
                  />
                </div>
              </section>

              {/* 유동성 */}
              <section className="flex flex-col gap-2 sm:gap-3">
                <h3 className="text-base sm:text-lg md:text-xl font-normal text-black">
                  유동성
                </h3>
                <div className="w-full rounded-2xl overflow-hidden border border-stone-300 grid grid-cols-1 sm:grid-cols-2">
                  <Cell title="Current Ratio" subtitle="유동비율" value="12.5%" />
                  <Cell title="Quick Ratio" subtitle="당좌비율" value="12.5%" />
                </div>
              </section>
            </aside>
          </div>

          {/* ========== 하단 분석 결과 영역 ========== */}
          <section className="w-full bg-zinc-100 rounded-2xl py-8 sm:py-10 flex items-center justify-center mt-2">
            <button className="px-6 sm:px-8 py-2.5 bg-sky-800 rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium">
              분석 결과 보기
            </button>
          </section>
        </main>
      </div>
    </div>
  );
}

/** 재무제표 공통 셀 */
function Cell({
  title,
  subtitle,
  value,
}: {
  title: string;
  subtitle: string;
  value: string;
}) {
  return (
    <div className="p-2 sm:p-2.5 bg-white flex items-start justify-between gap-2">
      <div className="flex flex-col gap-0.5">
        <span className="text-black text-xs sm:text-sm md:text-base font-medium">
          {title}
        </span>
        <span className="text-black text-[10px] sm:text-[11px] md:text-xs">
          {subtitle}
        </span>
      </div>
      <span className="text-black text-xs sm:text-sm md:text-base font-medium whitespace-nowrap">
        {value}
      </span>
    </div>
  );
}
