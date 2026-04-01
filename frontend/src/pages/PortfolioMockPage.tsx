// src/pages/PortfolioMockPage.tsx

import { useEffect, useRef, useState } from "react";
import AnalysisResultPanel from "../components/AnalysisResultPanel";

type HoldingRow = {
  id: number;
  name: string;
  quantity: number;
  avgPrice: number;
};

const EXCHANGE_RATE = 1465.5;

export default function PortfolioMockPage() {
  const [isOpen, setIsOpen] = useState(false);
  const [selectedMarket, setSelectedMarket] = useState<"국내" | "해외">("국내");
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  // 국내 / 해외 각각의 포트폴리오 상태
  const [domesticRows, setDomesticRows] = useState<HoldingRow[]>([
    { id: 1, name: "삼성전자", quantity: 125, avgPrice: 85000 },
    { id: 2, name: "SK하이닉스", quantity: 20, avgPrice: 500000 },
    { id: 3, name: "NAVER", quantity: 14, avgPrice: 250000 },
  ]);

  const [overseasRows, setOverseasRows] = useState<HoldingRow[]>([
    { id: 1, name: "AAPL", quantity: 10, avgPrice: 180 },
    { id: 2, name: "MSFT", quantity: 5, avgPrice: 400 },
  ]);

  // 현재 선택된 마켓에 맞는 rows / setter
  const rows = selectedMarket === "국내" ? domesticRows : overseasRows;
  const setRows = selectedMarket === "국내" ? setDomesticRows : setOverseasRows;

  // 총 금액 계산 (국내: 원, 해외: 달러 기준이라고 가정)
  const totalKRW =
    selectedMarket === "국내"
      ? rows.reduce((sum, r) => sum + r.quantity * r.avgPrice, 0)
      : rows.reduce((sum, r) => sum + r.quantity * r.avgPrice, 0) *
        EXCHANGE_RATE;

  const totalUSD =
    selectedMarket === "국내"
      ? totalKRW / EXCHANGE_RATE
      : rows.reduce((sum, r) => sum + r.quantity * r.avgPrice, 0);

  const [analysisResult] = useState<null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [showAnalyzeButton, setShowAnalyzeButton] = useState(true);
  const [displayText] = useState("");

  const handleAnalyzeClick = async () => {
    setLoading(true);
    setErr("");
    try {
      setShowAnalyzeButton(false);
      // TODO: 포트폴리오 분석 엔드포인트 확정 후 구현
      // const result = await fetchPortfolioAnalysis(rows);
      // setAnalysisResult(result);
      throw new Error("포트폴리오 분석 엔드포인트 미구현");
    } catch {
      setErr("분석 기능은 준비 중입니다.");
      setShowAnalyzeButton(true);
    } finally {
      setLoading(false);
    }
  };

  const toggleOpen = () => setIsOpen((prev) => !prev);

  const handleSelect = (value: "국내" | "해외") => {
    setSelectedMarket(value);
    setIsOpen(false);
  };

  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  // 공통: 현재 마켓의 rows 조작
  const handleAddRow = () => {
    const nextId = rows.length ? Math.max(...rows.map((r) => r.id)) + 1 : 1;
    setRows((prev) => [
      ...prev,
      { id: nextId, name: "", quantity: 0, avgPrice: 0 },
    ]);
  };

  const handleRemoveRow = (id: number) => {
    setRows((prev) => prev.filter((row) => row.id !== id));
  };

  const handleChangeRow = (
    id: number,
    field: keyof Omit<HoldingRow, "id">,
    value: string,
  ) => {
    setRows((prev) =>
      prev.map((row) =>
        row.id === id
          ? {
              ...row,
              [field]:
                field === "name"
                  ? value
                  : Number(value.replace(/[^0-9.-]/g, "")) || 0,
            }
          : row,
      ),
    );
  };

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[60px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
          <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
            포트폴리오
          </h1>
        </header>

        {/* 메인 2열 레이아웃 */}
        <main className="w-full flex flex-col lg:flex-row gap-6 items-start">
          {/* 왼쪽 영역 */}
          <section className="w-full lg:flex-1 flex flex-col gap-6 pt-4">
            {/* 1. 드롭다운 */}
            <div ref={dropdownRef} className="relative inline-block">
              <button
                type="button"
                onClick={toggleOpen}
                className="inline-flex items-center justify-between
                           px-3 h-9
                           bg-white border border-black rounded-md
                           text-sm"
              >
                <span className="font-medium text-black">{selectedMarket}</span>
                <span
                  className={`ml-2 w-0 h-0 border-l-[5px] border-l-transparent border-r-[5px] border-r-transparent ${
                    isOpen
                      ? "border-t-0 border-b-[7px] border-b-neutral-700"
                      : "border-b-0 border-t-[7px] border-t-neutral-700"
                  }`}
                />
              </button>

              {isOpen && (
                <div
                  className="absolute left-0 top-[40px]
                             bg-white border border-neutral-300 rounded-md shadow-sm
                             z-10 w-[70px]"
                >
                  <button
                    type="button"
                    onClick={() => handleSelect("국내")}
                    className="w-full px-2 py-1.5 text-center text-sm hover:bg-neutral-100"
                  >
                    국내
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSelect("해외")}
                    className="w-full px-2 py-1.5 text-center text-sm hover:bg-neutral-100"
                  >
                    해외
                  </button>
                </div>
              )}
            </div>

            {/* 2. 타이틀 */}
            <div className="w-full flex justify-center">
              <p className="text-xl font-medium text-black">
                {selectedMarket} 투자 자산 비율
              </p>
            </div>

            {/* 3. 총 금액 / 환율 */}
            <div className="w-full flex justify-end">
              <div className="flex flex-col items-end gap-1 text-black">
                <p className="font-medium text-base">
                  총 금액: {totalKRW.toLocaleString()}(원)
                </p>
                <p className="text-right leading-tight text-sm">
                  <span>
                    {totalUSD.toLocaleString(undefined, {
                      maximumFractionDigits: 2,
                    })}
                    (달러)
                  </span>
                  <br />
                  <span className="text-xs">
                    적용환율:{EXCHANGE_RATE.toLocaleString()}
                  </span>
                </p>
              </div>
            </div>

            {/* 4. 파이차트 예정 영역 */}
            <div className="w-full h-52 bg-neutral-50 border border-stone-300 rounded-xl" />

            {/* 5. 레이어: 표 + 추가 버튼 */}
            <div className="flex flex-col items-center gap-2">
              <div className="w-full max-w-[520px] border border-black rounded-md overflow-hidden bg-white text-xs">
                {/* 헤더 행 */}
                <div className="grid grid-cols-[2fr,1.2fr,1.8fr,0.6fr] bg-gray-200 border-b border-zinc-600">
                  <div className="px-2 py-1.5 flex items-center justify-center text-center font-medium text-black">
                    종목 이름
                  </div>
                  <div className="px-2 py-1.5 flex items-center justify-center text-center font-medium text-black">
                    보유 주식 수
                  </div>
                  <div className="px-2 py-1.5 flex items-center justify-center text-center font-medium text-black">
                    매수 평균단가
                  </div>
                  <div className="px-2 py-1.5 flex items-center justify-center text-center font-medium text-black">
                    삭제
                  </div>
                </div>

                {/* 데이터 행들 */}
                <div className="divide-y divide-zinc-300">
                  {rows.map((row) => (
                    <div
                      key={row.id}
                      className="grid grid-cols-[2fr,1.2fr,1.8fr,0.6fr] bg-white"
                    >
                      {/* 종목 이름 */}
                      <div className="px-2 py-1.5 flex items-center justify-center">
                        <input
                          className="w-full text-center text-xs text-black border border-zinc-300 rounded-sm px-1 py-0.5 focus:outline-none focus:ring-1 focus:ring-sky-500"
                          value={row.name}
                          onChange={(e) =>
                            handleChangeRow(row.id, "name", e.target.value)
                          }
                          placeholder="종목명"
                        />
                      </div>

                      {/* 보유 주식 수 */}
                      <div className="px-2 py-1.5 flex items-center justify-center">
                        <input
                          className="w-full text-center text-xs text-black border border-zinc-300 rounded-sm px-1 py-0.5 focus:outline-none focus:ring-1 focus:ring-sky-500"
                          value={
                            row.quantity ? row.quantity.toLocaleString() : ""
                          }
                          onChange={(e) =>
                            handleChangeRow(row.id, "quantity", e.target.value)
                          }
                          inputMode="numeric"
                          placeholder="0"
                        />
                      </div>

                      {/* 매수 평균단가 */}
                      <div className="px-2 py-1.5 flex items-center justify-center">
                        <input
                          className="w-full text-center text-xs text-black border border-zinc-300 rounded-sm px-1 py-0.5 focus:outline-none focus:ring-1 focus:ring-sky-500"
                          value={
                            row.avgPrice ? row.avgPrice.toLocaleString() : ""
                          }
                          onChange={(e) =>
                            handleChangeRow(row.id, "avgPrice", e.target.value)
                          }
                          inputMode="numeric"
                          placeholder="0"
                        />
                      </div>

                      {/* 삭제 버튼 */}
                      <div className="px-2 py-1.5 flex items-center justify-center">
                        <button
                          type="button"
                          onClick={() => handleRemoveRow(row.id)}
                          className="text-[10px] text-red-500 hover:text-red-600"
                        >
                          삭제
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 행 추가 버튼 (조금만 축소) */}
              <button
                type="button"
                onClick={handleAddRow}
                className="mt-1 w-8 h-8 rounded-3xl bg-zinc-300 flex items-center justify-center hover:bg-zinc-400 transition-colors"
              >
                <span className="text-xl leading-none text-black">+</span>
              </button>
            </div>
          </section>

          {/* 오른쪽: 분석 결과 보기 영역 */}
          <section className="w-full lg:flex-1 mt-10 lg:mt-0">
            <AnalysisResultPanel
              result={analysisResult}
              loading={loading}
              err={err}
              showAnalyzeButton={showAnalyzeButton}
              onAnalyze={handleAnalyzeClick}
              onDownload={() => {}}
              onZoom={() => {}}
              displayText={displayText}
              layout="panel"
            />
          </section>
        </main>
      </div>
    </div>
  );
}
