// src/pages/PortfolioMockPage.tsx

import { useEffect, useRef, useState } from "react";
import { Trash2, Plus, Info } from "lucide-react";
import StockSearchCell from "../components/StockSearchCell";
import { fetchPortfolioAnalysis } from "../api/portfolio";

type HoldingRow = {
  id: number;
  name: string;
  stockCode?: string;
  quantity: number;
  avgPrice: number;
};

const EXCHANGE_RATE = 1465.5;

type AnalysisOption = {
  key: string;
  label: string;
  descriptions: string[];
};

const EXTRA_OPTIONS: AnalysisOption[] = [
  {
    key: "fundamentals",
    label: "종목 체력 반영",
    descriptions: [
      "재무와 밸류에이션을 기반으로 보유 종목의 특성을 함께 반영합니다.",
      "종목의 기본적인 체력과 상태를 고려한 해석을 제공합니다.",
    ],
  },
  {
    key: "industry",
    label: "산업 집중도 반영",
    descriptions: [
      "포트폴리오가 특정 산업에 얼마나 집중되어 있는지 확인합니다.",
      "산업별 비중을 통해 쏠림 여부를 파악할 수 있습니다.",
    ],
  },
  {
    key: "correlation",
    label: "종목 분산 구조 반영",
    descriptions: [
      "실제 가격 움직임 기준으로 비슷하게 움직이는 종목이 얼마나 겹치는지 보여줍니다.",
      "겉보기 분산과 실제 분산의 차이를 확인할 수 있습니다.",
    ],
  },
  {
    key: "technical",
    label: "기술적 지표 반영",
    descriptions: [
      "가격 추세와 모멘텀 등 기술적 흐름을 함께 반영합니다.",
      "최근 시장 흐름을 기반으로 포트폴리오 상태를 보완적으로 해석합니다.",
    ],
  },
  {
    key: "news",
    label: "뉴스 흐름 반영",
    descriptions: [
      "최근 뉴스의 전반적인 흐름을 반영하여 포트폴리오를 해석합니다.",
      "개별 종목에 영향을 줄 수 있는 주요 이슈를 함께 고려합니다.",
    ],
  },
];

import type { PortfolioAnalyzeResponse } from "../api/portfolio";

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

  // 분석 옵션 상태
  const [extraOptions, setExtraOptions] = useState<Record<string, boolean>>(
    Object.fromEntries(EXTRA_OPTIONS.map((o) => [o.key, false])),
  );
  const [analysisResult, setAnalysisResult] = useState<PortfolioAnalyzeResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const toggleExtraOption = (key: string) => {
    setExtraOptions((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleAnalyzeClick = async () => {
    const validRows = rows.filter((r) => r.name.trim() !== "");
    if (validRows.length === 0) {
      setErr("최소 1개 종목을 입력해주세요.");
      return;
    }
    setLoading(true);
    setErr("");
    setAnalysisResult(null);
    try {
      // TODO: 백엔드 연결 후 아래 목업을 실제 API 호출로 교체
      // const selectedOptions = Object.entries(extraOptions)
      //   .filter(([, v]) => v)
      //   .map(([k]) => k);
      // const result = await fetchPortfolioAnalysis({
      //   holdings: validRows.map((r) => ({
      //     stockCode: r.stockCode ?? "",
      //     quantity: r.quantity,
      //     avgPrice: r.avgPrice,
      //   })),
      //   options: selectedOptions,
      // });

      // 목업 데이터 (로딩 시뮬레이션)
      await new Promise((r) => setTimeout(r, 1000));
      const result: PortfolioAnalyzeResponse = {
        riskLevel: "Mid",
        volatility: 18.7,
        diversification: "보통",
        covarianceScore: 0.42,
        efficiency: "Efficient",
        gamma: 0.815,
      };

      setAnalysisResult(result);
    } catch {
      setErr("분석에 실패했습니다. 잠시 후 다시 시도해주세요.");
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

  const handleStockSelect = (id: number, name: string, stockCode?: string) => {
    setRows((prev) =>
      prev.map((row) =>
        row.id === id ? { ...row, name, stockCode: stockCode ?? row.stockCode } : row,
      ),
    );
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

            {/* 5. 포트폴리오 매니저 카드 */}
            <div className="flex flex-col items-center gap-4">
              <div className="w-full max-w-4xl bg-white rounded-xl shadow-sm">
                {/* 카드 타이틀 */}
                <div className="px-6 pt-5 pb-3">
                  <h2 className="text-lg font-bold text-gray-800">Portfolio Manager</h2>
                  <p className="text-sm text-gray-400 mt-0.5">보유 종목을 추가하거나 수정하세요</p>
                </div>

                {/* 테이블 */}
                <div className="px-4 pb-4">
                  {/* 헤더 행 */}
                  <div className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] bg-gray-50/80 rounded-xl">
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-gray-400">
                        종목 이름
                      </span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-gray-400">
                        보유 주식 수
                      </span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-gray-400">
                        매수 평균단가
                      </span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-gray-400">
                        삭제
                      </span>
                    </div>
                  </div>

                  {/* 데이터 행들 */}
                  <div>
                    {rows.map((row) => (
                      <div
                        key={row.id}
                        className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] border-b border-gray-100 last:border-b-0 transition-colors hover:bg-blue-50/30"
                      >
                        {/* 종목 이름 */}
                        <div className="px-4 py-3 flex items-center justify-center">
                          <StockSearchCell
                            value={row.name}
                            onSelect={(name, stockCode) =>
                              handleStockSelect(row.id, name, stockCode)
                            }
                          />
                        </div>

                        {/* 보유 주식 수 */}
                        <div className="px-4 py-3 flex items-center justify-center">
                          <input
                            className="w-full text-center text-sm text-gray-700 bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none focus:bg-[#3F51B5]/5 transition-colors"
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
                        <div className="px-4 py-3 flex items-center justify-center">
                          <input
                            className="w-full text-center text-sm text-gray-700 bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none focus:bg-[#3F51B5]/5 transition-colors"
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
                        <div className="px-4 py-3 flex items-center justify-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveRow(row.id)}
                            className="p-1.5 rounded-lg text-gray-300 hover:text-red-500 hover:bg-red-50 transition-all duration-200"
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* 행 추가 플로팅 버튼 */}
              <button
                type="button"
                onClick={handleAddRow}
                className="w-11 h-11 rounded-full bg-[#3F51B5] flex items-center justify-center shadow-lg hover:scale-105 hover:shadow-xl transition-all duration-200"
              >
                <Plus size={20} className="text-white" />
              </button>
            </div>

          </section>

          {/* 오른쪽: 분석 옵션 + 결과 영역 */}
          <section className="w-full lg:flex-1 mt-10 lg:mt-0 flex flex-col gap-6">
            {/* 2.2 분석 옵션 영역 */}
            <div className="w-full bg-white rounded-xl shadow-sm p-5 flex flex-col gap-4">
              <h2 className="text-base font-bold text-gray-800">분석 옵션</h2>

              {/* 기본 분석 (항상 체크, 비활성화) */}
              <div className="flex items-start gap-3 px-3 py-3 bg-[#3F51B5]/5 rounded-lg border border-[#3F51B5]/20">
                <input
                  type="checkbox"
                  checked
                  disabled
                  className="mt-0.5 w-4 h-4 accent-[#3F51B5] cursor-not-allowed"
                />
                <div className="flex-1">
                  <span className="text-sm font-semibold text-[#3F51B5]">
                    포트폴리오 기본 분석
                  </span>
                  <span className="ml-2 text-xs text-gray-400">(변경 불가)</span>
                  <div className="mt-1 text-xs text-gray-500 leading-relaxed">
                    <p>포트폴리오의 변동성, 분산, 효율성을 기본적으로 분석합니다.</p>
                    <p>모든 분석의 기준이 되는 핵심 계산이 포함됩니다.</p>
                  </div>
                </div>
              </div>

              {/* 추가 분석 옵션들 */}
              <div className="flex flex-col gap-1">
                <p className="text-xs text-gray-400 mb-1">추가 분석 (선택사항)</p>
                {EXTRA_OPTIONS.map((opt) => (
                  <label
                    key={opt.key}
                    className="group flex items-start gap-3 px-3 py-2.5 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
                  >
                    <input
                      type="checkbox"
                      checked={extraOptions[opt.key]}
                      onChange={() => toggleExtraOption(opt.key)}
                      className="mt-0.5 w-4 h-4 accent-[#3F51B5] cursor-pointer"
                    />
                    <div className="flex-1 flex items-start gap-1.5">
                      <span className="text-sm font-medium text-gray-700">
                        {opt.label}
                      </span>
                      {/* 툴팁 아이콘 + hover 설명 */}
                      <div className="relative">
                        <Info size={14} className="text-gray-300 group-hover:text-gray-400 mt-0.5 transition-colors" />
                        <div className="absolute left-5 top-0 w-64 p-2.5 bg-gray-800 text-white text-xs rounded-lg shadow-lg opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-30 leading-relaxed">
                          {opt.descriptions.map((d, i) => (
                            <p key={i} className={i > 0 ? "mt-1" : ""}>{d}</p>
                          ))}
                        </div>
                      </div>
                    </div>
                  </label>
                ))}
              </div>

              {/* 2.3 실행 버튼 */}
              <div className="w-full flex justify-center pt-2">
                <button
                  type="button"
                  onClick={handleAnalyzeClick}
                  disabled={loading}
                  className="px-10 py-3 bg-[#3F51B5] text-white font-semibold text-base rounded-xl shadow-md hover:bg-[#354499] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                >
                  {loading ? "분석 중..." : "분석결과보기"}
                </button>
              </div>

              {err && (
                <p className="text-sm text-red-500 text-center">{err}</p>
              )}
            </div>
            {/* 결과가 없을 때 안내 */}
            {!analysisResult && !loading && (
              <div className="w-full bg-neutral-50 rounded-2xl border-2 border-dashed border-gray-200 flex flex-col items-center justify-center py-40 text-gray-400">
                <p className="text-base font-medium">분석 결과가 여기에 표시됩니다</p>
                <p className="text-sm mt-1">좌측에서 포트폴리오를 입력한 뒤 분석을 실행하세요</p>
              </div>
            )}

            {/* 로딩 */}
            {loading && (
              <div className="w-full bg-neutral-50 rounded-2xl border-2 border-gray-200 flex items-center justify-center py-40">
                <p className="text-base text-gray-500 animate-pulse">분석 중입니다...</p>
              </div>
            )}

            {/* 3.1 핵심 요약 카드 */}
            {analysisResult && (
              <div className="flex flex-col gap-5">
                <h2 className="text-lg font-bold text-gray-800">핵심 요약</h2>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  {/* 카드 1: 위험 수준 */}
                  <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col gap-3">
                    <p className="text-xs font-bold uppercase tracking-wider text-gray-400">
                      위험 수준
                    </p>
                    <div className="flex items-end gap-2">
                      <span className="text-3xl font-bold text-gray-800">
                        {analysisResult.volatility.toFixed(1)}
                        <span className="text-base font-normal text-gray-400 ml-0.5">%</span>
                      </span>
                    </div>
                    <p className="text-sm text-gray-500">포트폴리오 변동성</p>
                    <span
                      className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                        analysisResult.riskLevel === "Low"
                          ? "bg-emerald-50 text-emerald-600"
                          : analysisResult.riskLevel === "Mid"
                            ? "bg-amber-50 text-amber-600"
                            : "bg-red-50 text-red-600"
                      }`}
                    >
                      {analysisResult.riskLevel}
                    </span>
                  </div>

                  {/* 카드 2: 분산 수준 */}
                  <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col gap-3">
                    <p className="text-xs font-bold uppercase tracking-wider text-gray-400">
                      변동성 기반 분산 수준
                    </p>
                    <div className="flex items-end gap-2">
                      <span className="text-3xl font-bold text-gray-800">
                        {analysisResult.covarianceScore.toFixed(2)}
                      </span>
                    </div>
                    <p className="text-sm text-gray-500">
                      공분산 기준으로 포트폴리오가 얼마나 분산되어 있는지 나타냅니다
                    </p>
                    <span
                      className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                        analysisResult.diversification === "분산됨"
                          ? "bg-emerald-50 text-emerald-600"
                          : analysisResult.diversification === "보통"
                            ? "bg-amber-50 text-amber-600"
                            : "bg-red-50 text-red-600"
                      }`}
                    >
                      {analysisResult.diversification}
                    </span>
                  </div>

                  {/* 카드 3: 효율성 */}
                  <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col gap-3">
                    <p className="text-xs font-bold uppercase tracking-wider text-gray-400">
                      효율성
                    </p>
                    <div className="flex items-end gap-2">
                      <span className="text-3xl font-bold text-gray-800">
                        {analysisResult.gamma.toFixed(3)}
                      </span>
                    </div>
                    <p className="text-sm text-gray-500">감마 기준 효율 상태</p>
                    <span
                      className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                        analysisResult.efficiency === "Efficient"
                          ? "bg-emerald-50 text-emerald-600"
                          : "bg-red-50 text-red-600"
                      }`}
                    >
                      {analysisResult.efficiency}
                    </span>
                  </div>
                </div>
              </div>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
