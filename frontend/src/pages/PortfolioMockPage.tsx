// src/pages/PortfolioMockPage.tsx

import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Trash2, Plus, Info, ClipboardList, Sun, Moon, ChevronDown, ChevronUp } from "lucide-react";
import { useTheme } from "../hooks/useTheme";
import StockSearchCell from "../components/StockSearchCell";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { fetchPortfolioAnalysis } from "../api/portfolio";
import { getApiErrorMessage } from "../utils/errorMessage";
import { LLM_VENDOR_OPTIONS } from "../components/AnalysisResultPanel";

const RISK_GAMMA_STORAGE_KEY = "qaima_risk_gamma";
const SURVEY_RESULT_STORAGE_KEY = "qaima_survey_result";

const clampRiskGamma = (v: number): number => {
  if (!Number.isFinite(v)) return 0;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return Math.round(v * 100) / 100;
};

const formatRiskGamma = (v: number): string => v.toFixed(2);

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
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedMarket, setSelectedMarket] = useState<"국내" | "해외">("국내");
  const dropdownRef = useRef<HTMLDivElement | null>(null);
  const rowsContainerRef = useRef<HTMLDivElement | null>(null);

  // 투자 성향 지수 (0.00 ~ 1.00, null 이면 미입력 상태)
  const [riskGamma, setRiskGamma] = useState<number | null>(null);
  // 숫자 입력 필드의 표시값 (타이핑 도중 소수점 입력을 허용하기 위해 문자열로 관리)
  const [riskGammaInput, setRiskGammaInput] = useState<string>("");

  // 설문에서 복귀했으면(sessionStorage) 그 값을, 없으면 마지막 저장값(localStorage)을 초기값으로 사용
  useEffect(() => {
    const fromSurvey = sessionStorage.getItem(SURVEY_RESULT_STORAGE_KEY);
    if (fromSurvey !== null) {
      const parsed = Number(fromSurvey);
      if (Number.isFinite(parsed)) {
        const clamped = clampRiskGamma(parsed);
        setRiskGamma(clamped);
        setRiskGammaInput(formatRiskGamma(clamped));
        localStorage.setItem(RISK_GAMMA_STORAGE_KEY, String(clamped));
      }
      sessionStorage.removeItem(SURVEY_RESULT_STORAGE_KEY);
      return;
    }

    const saved = localStorage.getItem(RISK_GAMMA_STORAGE_KEY);
    if (saved !== null) {
      const parsed = Number(saved);
      if (Number.isFinite(parsed)) {
        const clamped = clampRiskGamma(parsed);
        setRiskGamma(clamped);
        setRiskGammaInput(formatRiskGamma(clamped));
      }
    }
  }, []);

  const commitRiskGamma = (v: number) => {
    const clamped = clampRiskGamma(v);
    setRiskGamma(clamped);
    setRiskGammaInput(formatRiskGamma(clamped));
    localStorage.setItem(RISK_GAMMA_STORAGE_KEY, String(clamped));
  };

  const handleRiskGammaInputChange = (value: string) => {
    // 숫자와 단일 소수점만 허용 (중간 타이핑 상태 고려)
    if (value !== "" && !/^[0-9]*\.?[0-9]*$/.test(value)) {
      return;
    }
    setRiskGammaInput(value);
    if (value === "" || value === ".") {
      setRiskGamma(null);
      return;
    }
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      setRiskGamma(clampRiskGamma(parsed));
    }
  };

  const handleRiskGammaInputBlur = () => {
    if (riskGamma === null) {
      setRiskGammaInput("");
      return;
    }
    commitRiskGamma(riskGamma);
  };

  const handleGoSurvey = () => {
    navigate("/survey");
  };

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
  const [llmVendor, setLlmVendor] = useState<string>("Gemini 2.5 Flash");
  const [isModelOpen, setIsModelOpen] = useState(false);
  const modelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (!modelRef.current) return;
      if (modelRef.current.contains(e.target as Node)) return;
      setIsModelOpen(false);
    };
    document.addEventListener("click", handleClickOutside);
    return () => document.removeEventListener("click", handleClickOutside);
  }, []);

  const toggleExtraOption = (key: string) => {
    setExtraOptions((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleAnalyzeClick = async () => {
    const validRows = rows.filter((r) => r.name.trim() !== "");
    if (validRows.length === 0) {
      setErr("최소 1개 종목을 입력해주세요.");
      return;
    }
    if (riskGamma === null) {
      setErr("투자 성향 지수를 입력해주세요.");
      return;
    }
    setLoading(true);
    setErr("");
    setAnalysisResult(null);
    try {
      const selectedOptions = Object.entries(extraOptions)
        .filter(([, v]) => v)
        .map(([k]) => k);
      const result = await fetchPortfolioAnalysis({
        holdings: validRows.map((r) => ({
          stockCode: r.stockCode ?? r.name.trim(),
          quantity: r.quantity,
          avgPrice: r.avgPrice,
        })),
        options: selectedOptions,
        riskGamma,
      });

      setAnalysisResult(result);
    } catch (e) {
      setErr(getApiErrorMessage(e, "분석에 실패했습니다. 잠시 후 다시 시도해주세요."));
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
    setTimeout(() => {
      if (rowsContainerRef.current) {
        rowsContainerRef.current.scrollTop = rowsContainerRef.current.scrollHeight;
      }
    }, 0);
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
    <div className="min-h-screen bg-bg ml-[84px]">
      <div className="max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
        {/* 헤더 */}
        <header className="flex items-center justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Portfolio · Analysis
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              포트폴리오
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? "라이트 모드" : "다크 모드"}
              className="w-9 h-9 grid place-items-center rounded-xl bg-surface
                         border border-line text-ink-2 shadow-card
                         hover:bg-bg-sunk transition-colors"
            >
              {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <TokenBalanceBadge />
          </div>
        </header>

        {/* 메인 레이아웃 */}
        <main className="w-full flex flex-col gap-6">
          {/* 첫째 행: 투자 자산 비율(좌) + Portfolio Manager(우) */}
          <div className="w-full flex flex-col lg:flex-row gap-6 items-stretch pt-4">

            {/* 왼쪽: 투자 자산 비율 */}
            <section className="w-full lg:flex-1 flex flex-col">
              <div className="flex-1 flex flex-col rounded-2xl bg-surface border border-line shadow-card">
                {/* 카드 헤더: 드롭다운(좌) + 타이틀(중앙) + 총금액(우) */}
                <div className="px-6 pt-5 pb-3 flex items-center justify-between gap-4">
                  {/* 드롭다운 */}
                  <div ref={dropdownRef} className="relative flex-shrink-0">
                    <button
                      type="button"
                      onClick={toggleOpen}
                      className="inline-flex items-center justify-between px-3 h-9 rounded-lg text-sm bg-bg-sunk border border-line text-ink"
                    >
                      <span className="font-semibold text-ink tracking-tight">{selectedMarket}</span>
                      <span
                        className={`ml-2 w-0 h-0 border-l-[5px] border-l-transparent border-r-[5px] border-r-transparent ${
                          isOpen
                            ? "border-t-0 border-b-[7px] border-b-ink-3"
                            : "border-b-0 border-t-[7px] border-t-ink-3"
                        }`}
                      />
                    </button>
                    {isOpen && (
                      <div className="absolute left-0 top-[40px] rounded-lg z-10 w-[70px] bg-surface border border-line shadow-pop">
                        <button
                          type="button"
                          onClick={() => handleSelect("국내")}
                          className="w-full px-2 py-1.5 text-center text-sm rounded-t-lg text-ink hover:bg-bg-sunk"
                        >
                          국내
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSelect("해외")}
                          className="w-full px-2 py-1.5 text-center text-sm rounded-b-lg text-ink hover:bg-bg-sunk"
                        >
                          해외
                        </button>
                      </div>
                    )}
                  </div>

                  {/* 타이틀 */}
                  <p className="text-lg font-bold text-ink tracking-tight flex-1 text-center">
                    {selectedMarket} 투자 자산 비율
                  </p>

                  {/* 총 금액 / 환율 */}
                  <div className="flex-shrink-0 flex flex-col items-end gap-0.5">
                    <p className="font-medium text-sm font-mono tabular tracking-tight text-ink">
                      {totalKRW.toLocaleString()}원
                    </p>
                    <p className="text-xs text-ink-3 font-mono tabular">
                      {totalUSD.toLocaleString(undefined, { maximumFractionDigits: 2 })}달러
                    </p>
                    <p className="text-[11px] text-ink-4">
                      환율 {EXCHANGE_RATE.toLocaleString()}
                    </p>
                  </div>
                </div>

                {/* 파이차트 예정 영역 */}
                <div className="px-4 pb-4 flex-1 flex flex-col">
                  <div className="flex-1 rounded-xl bg-bg-sunk border border-line" />
                </div>
              </div>
            </section>

            {/* 오른쪽: Portfolio Manager */}
            <section className="w-full lg:flex-1 flex flex-col gap-4">
              <div className="w-full rounded-2xl bg-surface border border-line shadow-card">
                <div className="px-6 pt-5 pb-3 flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-ink tracking-tight">Portfolio Manager</h2>
                    <p className="text-sm mt-0.5 text-ink-3">보유 종목을 추가하거나 수정하세요</p>
                  </div>
                  <button
                    type="button"
                    onClick={handleAddRow}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors bg-ink text-bg hover:opacity-90"
                  >
                    <Plus size={14} />
                    종목 추가
                  </button>
                </div>

                <div className="px-4 pb-4">
                  {/* 헤더 행 */}
                  <div className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] rounded-xl bg-bg-sunk">
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">종목 이름</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">보유 주식 수</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">매수 평균단가</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">삭제</span>
                    </div>
                  </div>

                  {/* 데이터 행들 */}
                  <div ref={rowsContainerRef} className="h-52 overflow-y-auto">
                    {rows.map((row) => (
                      <div
                        key={row.id}
                        className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] last:border-b-0 transition-colors border-b border-line hover:bg-bg-sunk"
                      >
                        <div className="px-4 py-3 flex items-center justify-center">
                          <StockSearchCell
                            value={row.name}
                            onSelect={(name, stockCode) => handleStockSelect(row.id, name, stockCode)}
                          />
                        </div>
                        <div className="px-4 py-3 flex items-center justify-center">
                          <input
                            className="w-full text-center text-sm bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none transition-colors text-ink font-mono tabular focus:bg-accent-soft"
                            value={row.quantity ? row.quantity.toLocaleString() : ""}
                            onChange={(e) => handleChangeRow(row.id, "quantity", e.target.value)}
                            inputMode="numeric"
                            placeholder="0"
                          />
                        </div>
                        <div className="px-4 py-3 flex items-center justify-center">
                          <input
                            className="w-full text-center text-sm bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none transition-colors text-ink font-mono tabular focus:bg-accent-soft"
                            value={row.avgPrice ? row.avgPrice.toLocaleString() : ""}
                            onChange={(e) => handleChangeRow(row.id, "avgPrice", e.target.value)}
                            inputMode="numeric"
                            placeholder="0"
                          />
                        </div>
                        <div className="px-4 py-3 flex items-center justify-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveRow(row.id)}
                            className="p-1.5 rounded-lg transition-all duration-200 text-ink-4 hover:text-danger hover:bg-danger/10"
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

            </section>
          </div>

          {/* 둘째 행: 분석 옵션 (전체 너비, 가로 배치) */}
          <div className="w-full rounded-2xl p-5 bg-surface border border-line shadow-card">
            <h2 className="text-base font-bold text-ink tracking-tight mb-4">분석 옵션</h2>

            <div className="flex flex-col lg:flex-row gap-5">
              {/* 왼쪽: 기본 분석 + 추가 옵션 체크박스 */}
              <div className="flex-[3] flex flex-col gap-3">
                {/* 기본 분석 */}
                <div className="flex items-start gap-3 px-3 py-3 rounded-lg bg-accent-soft border border-accent/20">
                  <input
                    type="checkbox"
                    checked
                    disabled
                    className="mt-0.5 w-4 h-4 cursor-not-allowed accent-accent"
                  />
                  <div className="flex-1">
                    <span className="text-sm font-semibold text-accent">포트폴리오 기본 분석</span>
                    <span className="ml-2 text-xs text-ink-4">(변경 불가)</span>
                    <div className="mt-1 text-xs leading-relaxed text-ink-3">
                      <p>포트폴리오의 변동성, 분산, 효율성을 기본적으로 분석합니다.</p>
                      <p>모든 분석의 기준이 되는 핵심 계산이 포함됩니다.</p>
                    </div>
                  </div>
                </div>

                {/* 추가 분석 옵션 (2열 그리드) */}
                <div>
                  <p className="text-xs mb-2 text-ink-4">추가 분석 (선택사항)</p>
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-1">
                    {EXTRA_OPTIONS.map((opt) => (
                      <label
                        key={opt.key}
                        className="flex items-center gap-2 px-3 py-2.5 rounded-lg cursor-pointer transition-colors hover:bg-bg-sunk"
                      >
                        <input
                          type="checkbox"
                          checked={extraOptions[opt.key]}
                          onChange={() => toggleExtraOption(opt.key)}
                          className="w-4 h-4 cursor-pointer accent-accent flex-shrink-0"
                        />
                        <span className="text-sm font-medium text-ink-2">{opt.label}</span>
                        <div className="group relative flex-shrink-0">
                          <Info size={14} className="transition-colors text-ink-4 group-hover:text-ink-3" />
                          <div className="absolute left-5 top-0 w-64 p-2.5 text-xs rounded-lg shadow-lg opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-30 leading-relaxed bg-ink text-bg">
                            {opt.descriptions.map((d, i) => (
                              <p key={i} className={i > 0 ? "mt-1" : ""}>{d}</p>
                            ))}
                          </div>
                        </div>
                      </label>
                    ))}
                  </div>
                </div>
              </div>

              {/* 구분선 */}
              <div className="hidden lg:block w-px bg-line" />

              {/* 오른쪽: 투자 성향 지수 + 실행 버튼 */}
              <div className="flex-[2] flex flex-col gap-4">
                {/* 투자 성향 지수 */}
                <div className="flex flex-col gap-3 px-3 py-3 rounded-lg bg-accent-soft border border-accent/20">
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm font-semibold text-accent">투자 성향 지수</span>
                      <span className="text-xs text-ink-4">(필수)</span>
                    </div>
                    <button
                      type="button"
                      onClick={handleGoSurvey}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors text-accent bg-surface border border-accent/30 hover:bg-accent/10"
                    >
                      <ClipboardList size={14} />
                      설문으로 확인하기
                    </button>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="flex-1 flex flex-col gap-1">
                      <input
                        type="range"
                        min={0}
                        max={1}
                        step={0.01}
                        value={riskGamma ?? 0.5}
                        onChange={(e) => commitRiskGamma(Number(e.target.value))}
                        className="w-full cursor-pointer accent-accent"
                      />
                      <div className="flex justify-between text-[11px] text-ink-3">
                        <span>0.00 · 보수적</span>
                        <span>공격적 · 1.00</span>
                      </div>
                    </div>
                    <input
                      type="text"
                      inputMode="decimal"
                      value={riskGammaInput}
                      onChange={(e) => handleRiskGammaInputChange(e.target.value)}
                      onBlur={handleRiskGammaInputBlur}
                      placeholder="0.00"
                      className="w-20 text-center text-sm font-semibold rounded-lg py-1.5 focus:outline-none transition-colors text-ink bg-surface border border-line focus:border-accent font-mono tabular"
                    />
                  </div>

                  <p className="text-xs leading-relaxed text-ink-3">
                    0에 가까울수록 안정적인 자산 배분을, 1에 가까울수록 공격적인
                    자산 배분을 기준으로 분석합니다.
                  </p>
                </div>

              </div>
            </div>
          </div>

          {/* 분석 실행 배너 */}
          <section className="rounded-2xl border border-line shadow-card p-5 sm:p-6
                              bg-gradient-to-br from-accent-soft to-surface
                              flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="text-[11px] font-semibold text-accent tracking-tight mb-1">
                ✨ AI 포트폴리오 분석
              </div>
              <h3 className="text-lg font-bold text-ink tracking-tight">
                포트폴리오를 한 번에 분석해 드릴게요
              </h3>
              <p className="text-sm text-ink-3 mt-1">
                변동성 · 분산 구조 · 효율성을 종합한 리포트
              </p>
              {!loading && riskGamma === null && (
                <p className="text-xs text-ink-4 mt-1.5">투자 성향 지수를 먼저 입력해주세요.</p>
              )}
              {err && <p className="text-xs text-danger mt-1.5">{err}</p>}
            </div>
            <div className="flex items-center gap-3 flex-shrink-0">
              <div ref={modelRef} className="relative">
                <button
                  onClick={() => setIsModelOpen((prev) => !prev)}
                  className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-surface border border-line text-sm"
                >
                  <span className="text-ink-3 text-[11px]">모델</span>
                  <span className="font-semibold text-ink">{llmVendor}</span>
                  {isModelOpen
                    ? <ChevronUp size={14} className="text-ink-3 pointer-events-none" />
                    : <ChevronDown size={14} className="text-ink-3 pointer-events-none" />}
                </button>
                {isModelOpen && (
                  <div className="absolute right-0 bottom-full mb-1 w-full bg-surface border border-line rounded-xl shadow-pop z-50 max-h-60 overflow-y-auto">
                    {LLM_VENDOR_OPTIONS.map((vendor) => (
                      <button
                        key={vendor}
                        onClick={() => { setLlmVendor(vendor); setIsModelOpen(false); }}
                        className={`w-full text-left px-3.5 py-2.5 text-sm first:rounded-t-xl last:rounded-b-xl ${
                          vendor === llmVendor
                            ? "bg-accent-soft text-accent font-semibold"
                            : "text-ink hover:bg-bg-sunk"
                        }`}
                      >
                        {vendor}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={handleAnalyzeClick}
                disabled={loading || riskGamma === null}
                className="px-5 py-2.5 rounded-xl bg-ink text-bg font-semibold text-sm
                           hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity tracking-tight"
              >
                분석 결과 보기 →
              </button>
            </div>
          </section>

          {/* 하단 전체 너비: 분석 결과 영역 */}
          {!analysisResult && !loading && (
            <div className="w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center py-24 bg-bg-sunk border-line text-ink-4">
              <p className="text-base font-medium">분석 결과가 여기에 표시됩니다</p>
              <p className="text-sm mt-1">포트폴리오를 입력한 뒤 분석을 실행하세요</p>
            </div>
          )}

          {loading && (
            <div className="w-full rounded-2xl border-2 flex items-center justify-center py-24 bg-bg-sunk border-line">
              <p className="text-base animate-pulse text-ink-3">분석 중입니다...</p>
            </div>
          )}

          {/* 3.1 핵심 요약 카드 */}
          {analysisResult && (
            <section className="w-full flex flex-col gap-5">
              <h2 className="text-lg font-bold text-ink tracking-tight">핵심 요약</h2>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* 카드 1: 위험 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    위험 수준
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {analysisResult.volatility.toFixed(1)}
                      <span className="text-base font-normal ml-0.5 text-ink-4">%</span>
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">포트폴리오 변동성</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.riskLevel === "Low"
                        ? "bg-success/10 text-success"
                        : analysisResult.riskLevel === "Mid"
                          ? "bg-warn/10 text-warn"
                          : "bg-danger/10 text-danger"
                    }`}
                  >
                    {analysisResult.riskLevel}
                  </span>
                </div>

                {/* 카드 2: 분산 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    변동성 기반 분산 수준
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {analysisResult.covarianceScore.toFixed(2)}
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">
                    공분산 기준으로 포트폴리오가 얼마나 분산되어 있는지 나타냅니다
                  </p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.diversification === "분산됨"
                        ? "bg-success/10 text-success"
                        : analysisResult.diversification === "보통"
                          ? "bg-warn/10 text-warn"
                          : "bg-danger/10 text-danger"
                    }`}
                  >
                    {analysisResult.diversification}
                  </span>
                </div>

                {/* 카드 3: 효율성 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    효율성
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {analysisResult.gamma.toFixed(3)}
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">감마 기준 효율 상태</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.efficiency === "Efficient"
                        ? "bg-success/10 text-success"
                        : "bg-danger/10 text-danger"
                    }`}
                  >
                    {analysisResult.efficiency}
                  </span>
                </div>
              </div>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
