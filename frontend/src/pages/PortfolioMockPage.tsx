// src/pages/PortfolioMockPage.tsx

import { useEffect, useRef, useState } from "react";
import type { MouseEvent, ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Trash2, Plus, Info, ClipboardList, Sun, Moon, ChevronDown, ChevronUp } from "lucide-react";
import { useTheme } from "../hooks/useTheme";
import StockSearchCell from "../components/StockSearchCell";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { fetchPortfolioAnalysis, previewFeature3OverlayCache } from "../api/portfolio";
import { fetchFeature2MacroRates } from "../api/feature2";
import { getApiErrorMessage } from "../utils/errorMessage";
import type { Feature2ExchangeRatePoint } from "../types/feature2";

const RISK_GAMMA_STORAGE_KEY = "qaima_risk_gamma";
const SURVEY_RESULT_STORAGE_KEY = "qaima_survey_result";

const LLM_VENDOR_OPTIONS = [
  "GPT-5.4",
  "GPT-5.2",
  "GPT-5 mini",
  "GPT-4.1",
  "GPT-4o",
  "Gemini 3.1 Pro",
  "Gemini 3 Pro",
  "Gemini 3 Flash",
  "Gemini 3.1 Flash Lite",
  "Gemini 2.5 Flash",
  "Gemini 2.5 Pro",
  "Claude Opus 4.6",
  "Claude Opus 4.5",
  "Claude Sonnet 4.6",
  "Claude Sonnet 4",
  "Claude Haiku 4.5",
  "Grok 4",
  "Grok 4.1 Fast",
  "Grok 4 Fast",
  "Grok 3",
  "Grok 3 Mini",
] as const;

const clampRiskGamma = (v: number): number => {
  if (!Number.isFinite(v)) return 0;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return Math.round(v * 100) / 100;
};

const formatRiskGamma = (v: number): string => v.toFixed(2);

const clampCashLimit = (v: number): number => {
  if (!Number.isFinite(v)) return 0.2;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return Math.round(v * 100) / 100;
};

const autoCashLimitForRiskScore = (riskScore: number): number => {
  return clampCashLimit(1 - clampRiskGamma(riskScore));
};

type AnalysisWindowPreset = {
  label: string;
  lookbackTradingDays: number;
  fetchCalendarDays: number;
};

const ANALYSIS_WINDOWS: AnalysisWindowPreset[] = [
  { label: "6개월", lookbackTradingDays: 126, fetchCalendarDays: 190 },
  { label: "1년", lookbackTradingDays: 252, fetchCalendarDays: 370 },
  { label: "2년", lookbackTradingDays: 504, fetchCalendarDays: 740 },
];

type HoldingRow = {
  id: number;
  name: string;
  stockCode?: string;
  quantity: number;
  avgPrice: number;
};

const CASH_COLOR = "#94a3b8";
const pieColorForIndex = (index: number): string => {
  const hue = (220 + index * 137.508) % 360;
  const lightness = index % 3 === 0 ? 44 : index % 3 === 1 ? 52 : 38;
  return `hsl(${hue.toFixed(1)} 68% ${lightness}%)`;
};

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
      "내가 보유한 종목들 간의 실제 움직임을 기반으로 위험 집중도를 분석합니다.","또한, 보유 종목이 속한 산업군 내에 비슷한 흐름을 보이는 외부 종목들을 함께 참고합니다.",
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

import type { Feature3CapmAsset, Feature3OverlayCachePreviewResponse, Feature3SclSeries, PortfolioAnalyzeResponse } from "../api/portfolio";

const formatPct = (value?: number | null, digits = 1): string =>
  `${(((value ?? 0) as number) * 100).toFixed(digits)}%`;

const formatPctPoint = (value?: number | null, digits = 2): string => {
  const numeric = (value ?? 0) as number;
  const sign = numeric > 0 ? "+" : "";
  return `${sign}${(numeric * 100).toFixed(digits)}%p`;
};

const formatKRW = (value?: number | null): string =>
  `${Math.round(value ?? 0).toLocaleString("ko-KR")}원`;

const capmWeightReasons = (asset: Feature3CapmAsset): string[] => {
  const warnings = new Set(asset.warnings ?? []);
  const reasons: string[] = [];
  if (warnings.has("CAPM_LOW_R_SQUARED") || (asset.rSquared !== null && asset.rSquared !== undefined && asset.rSquared < 0.1)) {
    reasons.push("낮은 R²");
  }
  if (asset.correlation !== null && asset.correlation !== undefined && Math.abs(asset.correlation) < 0.3) {
    reasons.push("낮은 correlation");
  }
  if (warnings.has("CAPM_COMMON_SAMPLE_INSUFFICIENT") || warnings.has("CAPM_PARTIAL_LOW_COMMON_SAMPLE") || (asset.commonSampleSize ?? 0) < 120) {
    reasons.push("표본 부족");
  }
  if (warnings.has("CAPM_BETA_UNAVAILABLE")) {
    reasons.push("beta 계산 불가");
  }
  if (
    warnings.has("CAPM_HIGH_VOLATILITY")
    || (asset.volatilityReliabilityFactor !== null && asset.volatilityReliabilityFactor !== undefined && asset.volatilityReliabilityFactor < 0.75)
    || (asset.annualVolatility !== null && asset.annualVolatility !== undefined && asset.annualVolatility > 0.4)
  ) {
    reasons.push("높은 변동성");
  }
  if ((asset.capmWeight ?? 0) < 0.3 && reasons.length === 0) {
    reasons.push("신뢰도 낮음");
  }
  return reasons.slice(0, 3);
};

const capmWeightReasonText = (asset: Feature3CapmAsset): string => {
  const reasons = capmWeightReasons(asset);
  if (!reasons.length) return "CAPM 품질 지표 양호";
  return `${reasons.join(" · ")}으로 CAPM 반영 제한`;
};

const riskLevelLabel: Record<string, string> = {
  LOW: "낮음",
  MID: "보통",
  HIGH: "높음",
};

const capmStatusLabel: Record<string, string> = {
  AVAILABLE: "분석 가능",
  DISABLED: "분석 제외",
  UNAVAILABLE: "데이터 없음",
};

const capmAssetStatusLabel: Record<string, string> = {
  APPLIED: "반영",
  PARTIAL: "부분 반영",
  EXCLUDED: "제외",
};

const benchmarkSourceLabel: Record<string, string> = {
  DB: "저장 데이터",
  KIS_BACKFILLED: "외부 데이터 보강",
  DB_INSUFFICIENT: "저장 데이터 부족",
  DB_STALE: "최신 데이터 아님",
  UNAVAILABLE: "데이터 없음",
};

const pricePolicyLabel: Record<string, string> = {
  YAHOO_ADJ_CLOSE: "수정종가",
  RAW_CLOSE: "종가",
  YAHOO_ADJ_CLOSE_WITH_KIS_FALLBACK: "수정주가 + 종가 보완",
  ADJUSTED_CLOSE: "수정종가 요청",
  CLOSE: "종가",
};

const covarianceModelLabel: Record<string, string> = {
  LEDOIT_WOLF: "안정화 공분산",
  SAMPLE_COVARIANCE: "표본 공분산",
  SAMPLE: "표본 공분산",
};

const marketDataSourceLabel: Record<string, string> = {
  DB: "저장 데이터",
  KIS: "국내 시세",
  YAHOO: "Yahoo 수정종가",
  MARKETSTACK: "해외 시세",
  MIXED: "혼합",
  EMPTY: "데이터 없음",
  UNAVAILABLE: "사용 불가",
};

const cacheStatusLabel: Record<string, string> = {
  HIT: "재사용",
  MISS: "새 조회",
  STALE: "오래됨",
  AVAILABLE: "사용 가능",
  UNAVAILABLE: "확인 필요",
  BYPASSED: "캐시 미사용",
  REFRESHED: "갱신됨",
};

type OverlayCachePolicy = "REUSE_AVAILABLE" | "FORCE_REFRESH";
type OverlayCachePolicyMap = Record<string, OverlayCachePolicy>;

const overlayPreviewMessage = (message?: string | null): string => {
  if (!message) return "";
  return message;
};

const featureSourceLabel: Record<string, string> = {
  CORE_RISK: "핵심 리스크",
  FEATURE1: "기본 지표",
  FEATURE2: "보조 관측",
  FEATURE2_INDUSTRY: "업종 관측",
  FEATURE2_PEERCLUSTER: "유사 종목 관측",
  FEATURE2_NEWS: "뉴스 관측",
  FEATURE3: "포트폴리오 분석",
};

type OverlayValueItem = {
  label: string;
  value: string;
  tone?: "default" | "good" | "warn" | "muted";
};

const metricLabels: Record<string, string> = {
  PER: "PER",
  PBR: "PBR",
  PSR: "PSR",
  ROE: "ROE",
  OPM: "영업이익률",
  NPM: "순이익률",
  Debt: "부채비율",
  Current: "유동비율",
  RevenueGrowth: "매출 성장",
  EPSGrowth: "EPS 성장",
  alignment: "추세 정렬",
  percent_b: "밴드 위치",
  zone: "스토캐스틱",
  k_minus_d: "K-D",
  score: "감성 점수",
  count: "뉴스 수",
};

const valueToneClass: Record<NonNullable<OverlayValueItem["tone"]>, string> = {
  default: "border-line bg-surface text-ink-3",
  good: "border-success/30 bg-success/10 text-success",
  warn: "border-warn/30 bg-warn/10 text-warn",
  muted: "border-line bg-bg-sunk text-ink-4",
};

const parseMetricMap = (value: string): Record<string, string> => {
  const result: Record<string, string> = {};
  const regex = /([A-Za-z0-9_]+)=([^,|]+)/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(value)) !== null) {
    result[match[1]] = match[2].trim();
  }
  return result;
};

const compactNumber = (raw?: string, digits = 2): string | null => {
  if (!raw) return null;
  const numeric = Number(raw);
  if (!Number.isFinite(numeric)) return raw;
  return numeric.toLocaleString("ko-KR", { maximumFractionDigits: digits });
};

const displayMetricValue = (key: string, raw?: string): string => {
  if (!raw) return "-";
  if (key === "alignment") return raw === "bullish" ? "상승 우위" : raw === "bearish" ? "하락 우위" : raw;
  if (key === "zone") {
    if (raw === "overbought") return "과열";
    if (raw === "oversold") return "침체";
    return "중립";
  }
  if (key === "percent_b") return `${compactNumber(raw, 1) ?? raw}%`;
  if (["ROE", "OPM", "NPM", "Debt", "Current", "RevenueGrowth", "EPSGrowth"].includes(key)) {
    return `${compactNumber(raw, 1) ?? raw}%`;
  }
  if (key === "count") return `${compactNumber(raw, 0) ?? raw}건`;
  return compactNumber(raw) ?? raw;
};

const toneForMetric = (key: string, raw?: string): OverlayValueItem["tone"] => {
  const numeric = raw ? Number(raw) : NaN;
  if (key === "alignment") return raw === "bullish" ? "good" : raw === "bearish" ? "warn" : "default";
  if (key === "zone") return raw === "overbought" ? "warn" : raw === "oversold" ? "good" : "default";
  if (key === "score") return Number.isFinite(numeric) && numeric < 0 ? "warn" : "good";
  if (["ROE", "OPM", "NPM", "RevenueGrowth", "EPSGrowth"].includes(key)) {
    return Number.isFinite(numeric) && numeric < 0 ? "warn" : Number.isFinite(numeric) && numeric > 8 ? "good" : "default";
  }
  if (key === "Debt") return Number.isFinite(numeric) && numeric >= 200 ? "warn" : "default";
  return "default";
};

const renderValueItems = (items: OverlayValueItem[], caption?: string): ReactNode => (
  <div className="flex flex-col gap-1.5">
    {caption ? <p className="text-[11px] leading-tight text-ink-4">{caption}</p> : null}
    <div className="flex flex-wrap gap-1.5">
      {items.map((item, index) => (
        <span
          key={`${item.label}-${item.value}-${index}`}
          className={`rounded-md border px-2 py-1 text-[11px] leading-tight ${valueToneClass[item.tone ?? "default"]}`}
        >
          <span className="text-ink-4">{item.label}</span> {item.value}
        </span>
      ))}
    </div>
  </div>
);

const renderMetricOverlayValue = (value: string, keys: string[], caption?: string): ReactNode => {
  const metrics = parseMetricMap(value);
  const items = keys
    .filter((key) => metrics[key] !== undefined)
    .map((key) => ({
      label: metricLabels[key] ?? key,
      value: displayMetricValue(key, metrics[key]),
      tone: toneForMetric(key, metrics[key]),
    }));
  return items.length ? renderValueItems(items, caption) : value;
};

const renderIndustryOverlayValue = (value: string): ReactNode => {
  const [industry, indexName] = value.split("/").map((part) => part.trim()).filter(Boolean);
  return renderValueItems([
    { label: "산업", value: industry || value },
    ...(indexName ? [{ label: "업종 지수", value: indexName, tone: "muted" as const }] : []),
  ]);
};

const relationLabel = (raw?: string): string => {
  if (raw === "LEADER") return "선행";
  if (raw === "FOLLOWER") return "후행";
  if (raw === "COINCIDENT") return "동행";
  return "관계 미확정";
};

const renderPeerOverlayValue = (value: string): ReactNode => {
  const peers = value.split(" | ");
  const items = peers.map((part) => {
    if (part.startsWith("selectedPeers=")) {
      return { label: "선택 peer", value: `${part.replace("selectedPeers=", "")}개`, tone: "muted" as const };
    }
    const [namePart, ...rest] = part.split(",");
    const metrics = parseMetricMap(rest.join(","));
    const corr = parseMetricMap(namePart).corr ?? namePart.split(" corr=")[1];
    const name = namePart.split(" corr=")[0];
    const lag = metrics.lag ? ` · ${metrics.lag}일 lag` : "";
    return {
      label: name,
      value: `corr ${compactNumber(corr, 3) ?? "-"} · ${relationLabel(metrics.relation)}${lag}`,
      tone: Number(corr) >= 0.75 ? "warn" as const : "default" as const,
    };
  });
  return renderValueItems(items, "Peer corr은 동행 종목 참고 정보이며 비중 조정에는 직접 사용하지 않습니다.");
};

const renderOverlayValue = (overlayType: string, value?: string | null): ReactNode => {
  if (!value) return "-";
  if (overlayType === "fundamentals") {
    return renderMetricOverlayValue(value, ["PER", "PBR", "PSR", "ROE", "OPM", "NPM", "Debt", "RevenueGrowth", "EPSGrowth"]);
  }
  if (overlayType === "technical") {
    return renderMetricOverlayValue(value, ["alignment", "percent_b", "zone", "k_minus_d"], "EMA, 볼린저밴드, 스토캐스틱 핵심값입니다.");
  }
  if (overlayType === "news") {
    return renderMetricOverlayValue(value, ["score", "count"]);
  }
  if (overlayType === "industry") {
    return renderIndustryOverlayValue(value);
  }
  if (overlayType === "correlation" && value.includes(" | ")) {
    return renderPeerOverlayValue(value);
  }
  return value;
};

type PortfolioExplainSection = NonNullable<NonNullable<PortfolioAnalyzeResponse["explain"]>["sections"]>[keyof NonNullable<NonNullable<PortfolioAnalyzeResponse["explain"]>["sections"]>];

const explainSections = (explain?: PortfolioAnalyzeResponse["explain"] | null) => {
  const sections = explain?.sections;
  return [
    sections?.coreRisk,
    sections?.overlayObservations,
    sections?.portfolioComparison,
    sections?.volatilityAnalysis,
    sections?.efficiencyAnalysis,
    sections?.finalJudgement,
  ].filter((section): section is NonNullable<PortfolioExplainSection> => Boolean(section?.summary || section?.bullets?.length));
};

const renderExplainSection = (section?: PortfolioExplainSection | null, options?: { hideTitle?: boolean }) => {
  if (!section?.summary && !section?.bullets?.length) return null;
  return (
    <div className="rounded-xl bg-bg-sunk border border-line p-4">
      {!options?.hideTitle ? <h4 className="text-sm font-bold text-ink">{section.title ?? "요약"}</h4> : null}
      {section.summary ? <p className={`${options?.hideTitle ? "" : "mt-1"} text-xs leading-relaxed text-ink-3`}>{section.summary}</p> : null}
      {!section.summary && section.bullets?.length ? (
        <ul className={`${options?.hideTitle ? "" : "mt-2"} flex flex-col gap-1`}>
          {section.bullets.slice(0, 4).map((bullet, index) => (
            <li key={`${section.title ?? "explain"}-${index}`} className="text-xs leading-relaxed text-ink-4">
              {bullet}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
};

const portfolioTypeLabel: Record<string, string> = {
  CURRENT: "현재 구성",
  STABLE: "안정형",
  BALANCED: "균형형",
  AGGRESSIVE: "공격형",
  PSYCHOLOGICAL: "심리형",
  RISK_ALLOCATION: "CAL 기반 위험배분",
  UTILITY_OPTIMAL: "효용최대 포트폴리오",
  THEORETICAL_UTILITY: "이론적 효용접점",
  OVERLAY_BALANCED: "보조 관측 균형 시나리오",
  QUALITY_TILT: "품질 압력 시나리오",
  MOMENTUM_AWARE: "기술 흐름 압력",
  NEWS_GUARDED: "뉴스 경계 압력",
  DIVERSIFICATION_TILT: "분산 압력 시나리오",
};

const suitabilityLabel: Record<string, string> = {
  CONSERVATIVE_THAN_PROFILE: "성향보다 안정적",
  ALIGNED: "성향과 유사",
  AGGRESSIVE_THAN_PROFILE: "성향보다 공격적",
};

const portfolioPieStyle = (weights: PortfolioAnalyzeResponse["currentPortfolio"]["weights"]) => {
  let cursor = 0;
  const segments = weights.map((weight, index) => {
    const start = cursor;
    cursor += Math.max(0, weight.weight) * 100;
    const color = weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index);
    return `${color} ${start}% ${cursor}%`;
  });
  return {
    background: `conic-gradient(${segments.join(", ") || "#e5e7eb 0% 100%"})`,
  };
};

const weightMapByStock = (weights: PortfolioAnalyzeResponse["currentPortfolio"]["weights"]) =>
  new Map(weights.map((weight) => [weight.stockCode, weight.weight]));

type HoldingAllocation = {
  id: number;
  label: string;
  value: number;
  weight: number;
  color: string;
};

type FrontierHoverState = {
  x: number;
  y: number;
};

const holdingAllocationPieStyle = (allocations: HoldingAllocation[]) => {
  let cursor = 0;
  const segments = allocations.map((allocation) => {
    const start = cursor;
    cursor += allocation.weight * 100;
    return `${allocation.color} ${start}% ${cursor}%`;
  });
  return {
    background: `conic-gradient(${segments.join(", ") || "#e5e7eb 0% 100%"})`,
  };
};

export default function PortfolioMockPage() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedMarket, setSelectedMarket] = useState<"국내" | "해외">("국내");
  const [selectedWindow, setSelectedWindow] = useState<AnalysisWindowPreset>(ANALYSIS_WINDOWS[1]);
  const [usdKrwRate, setUsdKrwRate] = useState<Feature2ExchangeRatePoint | null>(null);
  const [exchangeRateError, setExchangeRateError] = useState("");
  const dropdownRef = useRef<HTMLDivElement | null>(null);
  const rowsContainerRef = useRef<HTMLDivElement | null>(null);

  // 투자 성향 지수 (0.00 ~ 1.00, null 이면 미입력 상태)
  const [riskGamma, setRiskGamma] = useState<number | null>(null);
  // 숫자 입력 필드의 표시값 (타이핑 도중 소수점 입력을 허용하기 위해 문자열로 관리)
  const [riskGammaInput, setRiskGammaInput] = useState<string>("");
  const [cashLimit, setCashLimit] = useState<number>(autoCashLimitForRiskScore(0.5));

  // 설문에서 복귀했으면(sessionStorage) 그 값을, 없으면 마지막 저장값(localStorage)을 초기값으로 사용
  useEffect(() => {
    const fromSurvey = sessionStorage.getItem(SURVEY_RESULT_STORAGE_KEY);
    if (fromSurvey !== null) {
      const parsed = Number(fromSurvey);
      if (Number.isFinite(parsed)) {
        const clamped = clampRiskGamma(parsed);
        setRiskGamma(clamped);
        setRiskGammaInput(formatRiskGamma(clamped));
        setCashLimit(autoCashLimitForRiskScore(clamped));
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
        setCashLimit(autoCashLimitForRiskScore(clamped));
      }
    }
  }, []);

  useEffect(() => {
    let alive = true;
    fetchFeature2MacroRates()
      .then((res) => {
        if (!alive) return;
        setUsdKrwRate(res.data?.usdKrw ?? null);
        setExchangeRateError(res.data?.usdKrw ? "" : "환율 없음");
      })
      .catch(() => {
        if (!alive) return;
        setUsdKrwRate(null);
        setExchangeRateError("환율 로드 실패");
      });
    return () => {
      alive = false;
    };
  }, []);

  const commitRiskGamma = (v: number) => {
    const clamped = clampRiskGamma(v);
    setRiskGamma(clamped);
    setRiskGammaInput(formatRiskGamma(clamped));
    setCashLimit(autoCashLimitForRiskScore(clamped));
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
      const clamped = clampRiskGamma(parsed);
      setRiskGamma(clamped);
      setCashLimit(autoCashLimitForRiskScore(clamped));
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
    { id: 1, name: "삼성전자", stockCode: "005930", quantity: 125, avgPrice: 85000 },
    { id: 2, name: "SK하이닉스", stockCode: "000660", quantity: 20, avgPrice: 500000 },
    { id: 3, name: "NAVER", stockCode: "035420", quantity: 14, avgPrice: 250000 },
  ]);

  const [overseasRows, setOverseasRows] = useState<HoldingRow[]>([
    { id: 1, name: "AAPL", stockCode: "AAPL", quantity: 10, avgPrice: 180 },
    { id: 2, name: "MSFT", stockCode: "MSFT", quantity: 5, avgPrice: 400 },
  ]);
  const [domesticCash, setDomesticCash] = useState(1_000_000);
  const [overseasCash, setOverseasCash] = useState(0);

  // 현재 선택된 마켓에 맞는 rows / setter
  const rows = selectedMarket === "국내" ? domesticRows : overseasRows;
  const setRows = selectedMarket === "국내" ? setDomesticRows : setOverseasRows;
  const cashAmount = selectedMarket === "국내" ? domesticCash : overseasCash;
  const setCashAmount = selectedMarket === "국내" ? setDomesticCash : setOverseasCash;
  const cashCurrency = selectedMarket === "국내" ? "KRW" : "USD";
  const cashLabel = selectedMarket === "국내" ? "보유중인 현금" : "USD 현금";
  const exchangeRate = usdKrwRate?.value ?? null;
  const cashValueKRW = selectedMarket === "국내" ? cashAmount : exchangeRate ? cashAmount * exchangeRate : 0;
  const cashValueDisplay = Math.max(0, cashAmount);
  const riskyValue = rows.reduce((sum, r) => sum + r.quantity * r.avgPrice, 0);

  // 총 금액 계산 (국내: 원, 해외: 달러 기준이라고 가정)
  const totalKRW =
    selectedMarket === "국내"
      ? riskyValue + cashValueKRW
      : exchangeRate ? riskyValue * exchangeRate + cashValueKRW : 0;

  const totalUSD =
    selectedMarket === "국내"
      ? exchangeRate ? totalKRW / exchangeRate : 0
      : riskyValue + cashValueDisplay;

  // 현재 구현: Portfolio Manager의 rows 상태를 즉시 비중 데이터로 변환해 왼쪽 파이차트에 실시간 반영한다.
  // 진행 예정: pie slice 클릭 시 해당 보유 종목 수정 popup을 연결한다.
  const allocationBaseValue = rows.reduce((sum, row) => sum + Math.max(0, row.quantity * row.avgPrice), 0) + Math.max(0, cashValueDisplay);
  // 입력 행에서는 같은 종목을 평균단가별로 따로 두지만, 비율 차트에서는 동일 종목을 합산해 한 조각으로 표시한다.
  const aggregatedByStock = new Map<string, { id: number; label: string; value: number }>();
  const aggregatedOrder: string[] = [];
  rows.forEach((row) => {
    const value = Math.max(0, row.quantity * row.avgPrice);
    if (value <= 0) return;
    const key =
      (row.stockCode && row.stockCode.trim()) || row.name.trim() || `__row_${row.id}`;
    const existing = aggregatedByStock.get(key);
    if (existing) {
      existing.value += value;
    } else {
      aggregatedByStock.set(key, {
        id: row.id,
        label: row.name.trim() || "미입력 종목",
        value,
      });
      aggregatedOrder.push(key);
    }
  });
  const holdingAllocations: HoldingAllocation[] = [
    ...aggregatedOrder.map((key, index) => {
      const item = aggregatedByStock.get(key)!;
      return {
        id: item.id,
        label: item.label,
        value: item.value,
        weight: allocationBaseValue > 0 ? item.value / allocationBaseValue : 0,
        color: pieColorForIndex(index),
      };
    }),
    ...(cashValueDisplay > 0
      ? [{
          id: -1,
          label: cashLabel,
          value: cashValueDisplay,
          weight: allocationBaseValue > 0 ? cashValueDisplay / allocationBaseValue : 0,
          color: CASH_COLOR,
        }]
      : []),
  ];

  // 원기둥 파이차트: 윗면과 동일한 conic을 어둡게 깔아 세그먼트 색을 따라가는 옆면을 만든다
  const pieBackground = holdingAllocationPieStyle(holdingAllocations).background;
  const PIE_DEPTH = 16; // 옆면(기둥) 두께 px
  // 윗면 + 바깥 옆면 링: 가운데 구멍(안쪽 반지름 ≈ 박스의 26%)
  const PIE_TOP_MASK =
    "radial-gradient(circle closest-side at 50% 50%, transparent 0 52%, #000 52.5%)";

  // 분석 옵션 상태
  const [extraOptions, setExtraOptions] = useState<Record<string, boolean>>(
    Object.fromEntries(EXTRA_OPTIONS.map((o) => [o.key, false])),
  );
  const [analysisResult, setAnalysisResult] = useState<PortfolioAnalyzeResponse | null>(null);
  const [analysisTab, setAnalysisTab] = useState<"BASIC" | "ADVANCED">("BASIC");
  const [overlayPreview, setOverlayPreview] = useState<Feature3OverlayCachePreviewResponse | null>(null);
  const [overlayCachePolicies, setOverlayCachePolicies] = useState<OverlayCachePolicyMap>({});
  const [frontierHover, setFrontierHover] = useState<FrontierHoverState | null>(null);
  const [sclHover, setSclHover] = useState<FrontierHoverState | null>(null);
  const [smlHover, setSmlHover] = useState<FrontierHoverState | null>(null);
  const [selectedSclStockCode, setSelectedSclStockCode] = useState<string | null>(null);
  const [selectedSmlBenchmarkCode, setSelectedSmlBenchmarkCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [llmVendor, setLlmVendor] = useState<string>("Gemini 2.5 Flash");
  const [isModelOpen, setIsModelOpen] = useState(false);
  const modelRef = useRef<HTMLDivElement | null>(null);
  const portfolioReportRef = useRef<HTMLDivElement | null>(null);

  const toggleExtraOption = (key: string) => {
    setOverlayPreview(null);
    setOverlayCachePolicies({});
    setExtraOptions((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const overlayPreviewCredit = overlayPreview
    ? overlayPreview.coreCredit + overlayPreview.overlays.reduce((sum, overlay) => {
        if (overlay.policySelectable && overlay.cacheStatus === "HIT" && overlayCachePolicies[overlay.overlayType] === "FORCE_REFRESH") {
          return sum + 1;
        }
        return sum + (overlay.additionalCredit ?? 0);
      }, 0)
    : 0;

  const overlayPolicyPayload = (overlays: string[]): OverlayCachePolicyMap =>
    Object.fromEntries(overlays.map((overlay) => [overlay, overlayCachePolicies[overlay] ?? "REUSE_AVAILABLE"]));

  const handleAnalyzeClick = async (skipOverlayPreview = false) => {
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
      if (selectedOptions.length > 0 && !skipOverlayPreview) {
        const preview = await previewFeature3OverlayCache({
          stockCodes: validRows.map((row) => row.stockCode ?? row.name.trim()),
          selectedOverlays: selectedOptions,
          cachePolicy: "REUSE_AVAILABLE",
          overlayCachePolicies: overlayPolicyPayload(selectedOptions),
        });
        setOverlayPreview(preview);
        setOverlayCachePolicies(Object.fromEntries(
          preview.overlays
            .filter((overlay) => overlay.policySelectable !== false)
            .map((overlay) => [overlay.overlayType, "REUSE_AVAILABLE" as const]),
        ));
        return;
      }
      const analysisWindow = selectedWindow;
      const result = await fetchPortfolioAnalysis({
        holdings: validRows.map((r) => ({
          stockCode: r.stockCode ?? r.name.trim(),
          companyName: r.name.trim(),
          quantity: r.quantity,
          avgPrice: r.avgPrice,
          currency: selectedMarket === "국내" ? "KRW" : "USD",
          assetType: "EQUITY",
        })),
        cashPositions: cashAmount > 0
          ? [{
              currency: cashCurrency,
              amount: cashAmount,
            }]
          : [],
        riskProfile: {
          riskToleranceScore: riskGamma,
        },
        options: {
          viewMode: "BASIC",
          priceBasis: "ADJUSTED_CLOSE",
          covarianceModel: "LEDOIT_WOLF",
          returnType: "LOG_RETURN",
          lookbackTradingDays: analysisWindow.lookbackTradingDays,
          fetchCalendarDays: analysisWindow.fetchCalendarDays,
          annualizationFactor: 252,
          cachePolicy: "REUSE_AVAILABLE",
          overlayCachePolicies: overlayPolicyPayload(selectedOptions),
          selectedOverlays: selectedOptions,
          includeFrontier: true,
          includeDiagnostics: true,
          includeLlmExplain: true,
          llmVendor,
          maxCashWeight: cashLimit,
        },
      });

      setAnalysisResult(result);
      setAnalysisTab("BASIC");
      setOverlayPreview(null);
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

    const handleClickOutside = (event: globalThis.MouseEvent) => {
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

  useEffect(() => {
    if (!isModelOpen) return;

    const handleClickOutside = (event: globalThis.MouseEvent) => {
      if (
        modelRef.current &&
        !modelRef.current.contains(event.target as Node)
      ) {
        setIsModelOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isModelOpen]);

  useEffect(() => {
    const reportNode = portfolioReportRef.current;
    if (!reportNode || !analysisResult) return;

    const sections = Array.from(reportNode.children).filter(
      (child): child is HTMLElement => child instanceof HTMLElement,
    );

    sections.forEach((section, index) => {
      section.classList.remove("is-visible");
      section.style.transitionDelay = `${Math.min(index * 55, 320)}ms`;
    });

    if (typeof IntersectionObserver === "undefined") {
      sections.forEach((section) => section.classList.add("is-visible"));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.18, rootMargin: "0px 0px -28% 0px" },
    );

    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [analysisResult, analysisTab]);

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

  const handleCashChange = (value: string) => {
    setCashAmount(Number(value.replace(/[^0-9.-]/g, "")) || 0);
  };

  return (
    <div className="min-h-screen bg-bg ml-[84px]">
      <div className="qaima-stagger max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6">
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
            <section className="w-full lg:flex-[1.6] flex flex-col">
              <div className="flex-1 flex flex-col rounded-2xl bg-surface border border-line shadow-card">
                {/* 카드 헤더: 드롭다운(좌) + 타이틀(중앙) + 총금액(우) */}
                <div className="relative px-6 pt-5 pb-3 flex items-center justify-between gap-4">
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

                  {/* 타이틀: 좌우 내용 너비가 변해도 항상 카드 정중앙 고정 (밀림 방지) */}
                  <p className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-lg font-bold text-ink tracking-tight whitespace-nowrap pointer-events-none">
                    {selectedMarket} 투자 자산 비율
                  </p>

                  {/* 총 금액 / 환율 */}
                  <div className="flex-shrink-0 flex flex-col items-end gap-0.5">
                    <p className="font-medium text-sm font-mono tabular tracking-tight text-ink">
                      {selectedMarket === "국내" || exchangeRate
                        ? `${Math.round(totalKRW).toLocaleString()}원`
                        : "-"}
                    </p>
                    <p className="text-xs text-ink-3 font-mono tabular">
                      {selectedMarket === "해외" || exchangeRate
                        ? `${totalUSD.toLocaleString(undefined, { maximumFractionDigits: 2 })}달러`
                        : "-"}
                    </p>
                    <p className="text-[11px] text-ink-4">
                      {exchangeRate
                        ? `환율 ${exchangeRate.toLocaleString()} · ${usdKrwRate?.date ?? usdKrwRate?.source ?? ""}`
                        : exchangeRateError || "환율 로딩중"}
                    </p>
                  </div>
                </div>

                {/* Portfolio Manager 입력값이 바뀔 때마다 즉시 다시 렌더링되는 실시간 비중 파이차트 */}
                <div className="px-4 pb-4 flex-1 flex flex-col">
                  <div className="flex-1 rounded-xl bg-bg-sunk border border-line p-4 flex flex-col sm:flex-row items-center justify-center gap-8 sm:gap-10">
                    <div className="relative w-56 h-56 sm:w-64 sm:h-64 flex-shrink-0 mb-6">
                      <div className="qaima-pie-in absolute inset-0">
                        {/* 바닥 그림자 */}
                        <div
                          className="absolute inset-0 rounded-full shadow-[0_20px_24px_-10px_rgba(15,23,42,0.5)]"
                          style={{ transform: `translateY(${PIE_DEPTH}px)` }}
                        />
                        {/* 옆면: 같은 conic을 어둡게 한 레이어를 아래로 쌓아 기둥처럼 */}
                        {Array.from({ length: PIE_DEPTH }).map((_, i) => {
                          const depthRatio = i / PIE_DEPTH;
                          const brightness = (0.42 + depthRatio * 0.26).toFixed(3);
                          return (
                            <div
                              key={i}
                              className="absolute inset-0 rounded-full"
                              style={{
                                background: pieBackground,
                                filter: `brightness(${brightness}) saturate(1.12)`,
                                transform: `translateY(${PIE_DEPTH - i}px)`,
                                WebkitMaskImage: PIE_TOP_MASK,
                                maskImage: PIE_TOP_MASK,
                              }}
                            />
                          );
                        })}
                        {/* 윗면 */}
                        <div
                          className="absolute inset-0 rounded-full"
                          style={{
                            background: pieBackground,
                            WebkitMaskImage: PIE_TOP_MASK,
                            maskImage: PIE_TOP_MASK,
                          }}
                          aria-label={`${selectedMarket} 투자 자산 비율 파이차트`}
                        />
                        {/* 윗면 광택 */}
                        <div
                          className="absolute inset-0 rounded-full pointer-events-none"
                          style={{
                            background:
                              "radial-gradient(circle at 34% 24%, rgba(255,255,255,0.5), rgba(255,255,255,0) 58%)",
                          }}
                        />
                      </div>
                      {/* 가운데 흰 구멍 (정중앙 고정) */}
                      <div className="absolute inset-[24%] rounded-full bg-surface shadow-[inset_0_2px_4px_rgba(15,23,42,0.12)] pointer-events-none" />
                      {/* 총 평가액: 바닥과 무관하게 차트 정중앙 고정 */}
                      <div className="absolute inset-0 grid place-items-center text-center pointer-events-none">
                        <div className="max-w-[58%]">
                          <p className="text-[10px] text-ink-4">총 평가액</p>
                          <p className="mt-0.5 text-sm font-bold text-ink font-mono tabular leading-tight truncate">
                            {selectedMarket === "국내"
                              ? `${Math.round(totalKRW).toLocaleString()}원`
                              : `$${totalUSD.toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="w-full sm:w-auto sm:min-w-[200px] sm:max-w-[260px] min-w-0 flex flex-col gap-2 max-h-56 overflow-y-auto">
                      {holdingAllocations.length === 0 ? (
                        <div className="text-center sm:text-left">
                          <p className="text-sm font-medium text-ink-3">표시할 보유 비중이 없습니다</p>
                          <p className="mt-1 text-xs text-ink-4">종목명, 수량, 평균단가를 입력하면 바로 반영됩니다.</p>
                        </div>
                      ) : (
                        holdingAllocations.map((allocation) => (
                          <div key={allocation.id} className="flex items-center gap-2">
                            <div className="flex-1 min-w-0 flex items-center justify-end gap-2">
                              <span
                                className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                                style={{ backgroundColor: allocation.color }}
                              />
                              <span className="min-w-0 truncate text-sm font-medium text-ink">
                                {allocation.label}
                              </span>
                            </div>
                            <span className="w-14 text-right text-xs font-mono tabular text-ink-3">
                              {formatPct(allocation.weight)}
                            </span>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </section>

            {/* 오른쪽: Portfolio Manager */}
            <section className="w-full lg:flex-[1.4] flex flex-col gap-4">
              <div className="w-full rounded-2xl bg-surface border border-line shadow-card">
                <div className="px-6 pt-5 pb-3 flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-ink tracking-tight">Portfolio Manager</h2>
                    <p className="text-sm mt-0.5 text-ink-3">보유 종목을 추가하거나 수정하세요</p>
                  </div>
                  <button
                    type="button"
                    onClick={handleAddRow}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors bg-accent text-white hover:opacity-90"
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
                  <div ref={rowsContainerRef} className="h-64 overflow-y-auto">
                    {rows.map((row) => (
                      <div
                        key={row.id}
                        className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] transition-colors border-b border-line hover:bg-bg-sunk"
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
                    <div className="mt-3 grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] rounded-xl bg-bg-sunk border border-line">
                      <div className="px-4 py-3 flex flex-col justify-center">
                        <span className="text-sm font-semibold text-ink">{cashLabel}</span>
                      </div>
                      <div className="px-4 py-3 flex items-center justify-center">
                        <span className="text-xs text-ink-4">-</span>
                      </div>
                      <div className="px-4 py-3 flex items-center justify-center">
                        <input
                          className="w-full text-center text-sm bg-transparent border-none rounded-lg px-2 py-1 focus:outline-none transition-colors text-ink font-mono tabular focus:bg-accent-soft"
                          value={cashAmount ? cashAmount.toLocaleString() : ""}
                          onChange={(e) => handleCashChange(e.target.value)}
                          inputMode="numeric"
                          placeholder="0"
                        />
                      </div>
                      <div className="px-4 py-3 flex items-center justify-center">
                        <span className="text-xs text-ink-4">고정</span>
                      </div>
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
                <div>
                  <p className="text-xs mb-2 text-ink-4">분석 기간</p>
                  <div className="flex flex-wrap items-center gap-1.5">
                    {ANALYSIS_WINDOWS.map((window) => (
                      <button
                        key={window.label}
                        type="button"
                        onClick={() => setSelectedWindow(window)}
                        className={`px-3.5 py-1.5 rounded-full text-sm transition-colors ${
                          selectedWindow.label === window.label
                            ? "bg-accent text-white font-semibold"
                            : "bg-bg-sunk text-ink-3 hover:bg-surface-2 font-medium"
                        }`}
                      >
                        {window.label}
                      </button>
                    ))}
                    <span className="ml-1 text-xs text-ink-4">1일봉</span>
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
	                    자산 배분을 기준으로 분석해요.
	                  </p>
	                </div>

	                <div className="flex flex-col gap-3 px-3 py-3 rounded-lg bg-bg-sunk border border-line">
	                  <div className="flex items-center justify-between flex-wrap gap-2">
	                    <div className="flex items-center gap-1.5">
	                      <span className="text-sm font-semibold text-ink">최대 현금비중 한도</span>
	                    </div>
	                  </div>

	                  <div className="flex items-center gap-3">
	                    <div className="flex-1 flex flex-col gap-1">
	                      <input
	                        type="range"
	                        min={0}
	                        max={1}
	                        step={0.01}
	                        value={cashLimit}
	                        onChange={(e) => setCashLimit(clampCashLimit(Number(e.target.value)))}
	                        className="w-full cursor-pointer accent-accent"
	                      />
	                      <div className="flex justify-between text-[11px] text-ink-3">
	                        <span>0% · 현금 X</span>
	                        <span>현금 O · 100%</span>
	                      </div>
	                    </div>
	                    <span className="w-20 inline-block text-center text-sm font-semibold rounded-lg py-1.5 text-ink bg-surface border border-line font-mono tabular">
	                      {formatPct(cashLimit, 0)}
	                    </span>
	                  </div>

	                  <p className="text-xs leading-relaxed text-ink-3">
	                    포트폴리오에 현금을 최대로 얼마나 남길지 설정해요. 투자 성향 지수를 조정하면 기본 한도가 함께 갱신되고, 여기서 직접 조정할 수도 있어요. 해당 값이 분석에 반영돼요.
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
            <div className="flex flex-col items-end gap-1 flex-shrink-0">
              <div className="flex items-center gap-3">
                <div ref={modelRef} className="relative">
                  <button
                    type="button"
                    onClick={() => setIsModelOpen((prev) => !prev)}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-surface border border-line text-sm"
                  >
                    <span className="text-ink-3 text-[11px]">모델</span>
                    <span className="font-semibold text-ink">{llmVendor}</span>
                    {isModelOpen
                      ? <ChevronUp size={14} className="text-ink-3 pointer-events-none" />
                      : <ChevronDown size={14} className="text-ink-3 pointer-events-none" />}
                  </button>
                  {isModelOpen && (
                    <div className="absolute right-0 bottom-full mb-1 w-full min-w-44 bg-surface border border-line rounded-lg shadow-pop z-50 max-h-60 overflow-y-auto">
                      {LLM_VENDOR_OPTIONS.map((vendor) => (
                        <button
                          key={vendor}
                          type="button"
                          onClick={() => { setLlmVendor(vendor); setIsModelOpen(false); }}
                          className={`w-full text-left px-3.5 py-2.5 text-sm first:rounded-t-lg last:rounded-b-lg ${
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
                  onClick={() => void handleAnalyzeClick()}
                  disabled={loading || riskGamma === null}
                  className="px-5 py-2.5 rounded-xl bg-accent text-white font-semibold text-sm
                             hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity tracking-tight"
                >
                  {loading ? "분석 중..." : "분석 결과 보기 →"}
                </button>
              </div>
              {!loading && riskGamma === null && (
                <p className="text-xs text-ink-4">투자 성향 지수를 먼저 입력해주세요.</p>
              )}
              {err && <p className="text-xs text-danger">{err}</p>}
            </div>
          </section>

          {overlayPreview && (
            <section className="rounded-2xl border border-accent/30 shadow-card p-5 bg-surface">
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-ink">추가 분석 비용 확인</h3>
                  <p className="mt-1 text-sm text-ink-3">{overlayPreviewMessage(overlayPreview.userMessage)}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-ink-4">예상 차감</p>
                  <p className="text-2xl font-bold font-mono tabular text-ink">
                    {overlayPreviewCredit} credit
                  </p>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {overlayPreview.overlays.map((overlay) => (
                  <div key={overlay.overlayType} className="rounded-xl bg-bg-sunk border border-line p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-bold text-ink">{overlay.overlayType}</p>
                      <span className="text-xs font-bold text-ink-4">{cacheStatusLabel[overlay.cacheStatus ?? ""] ?? overlay.cacheStatus}</span>
                    </div>
                    <p className="mt-1 text-xs text-ink-3">{overlayPreviewMessage(overlay.userMessage)}</p>
                    <p className="mt-2 text-[11px] text-ink-4">
                      {overlay.cacheStatus === "MISS"
                        ? "새 분석 후 기준 시각이 기록됩니다"
                        : overlay.cacheAsOf
                          ? overlayCachePolicies[overlay.overlayType] === "FORCE_REFRESH"
                            ? `기존 기준: ${overlay.cacheAsOf} · 새로 갱신 예정`
                            : overlay.cacheStatus === "AVAILABLE"
                              ? `기준: ${overlay.cacheAsOf}`
                              : `최근 결과 기준: ${overlay.cacheAsOf}`
                          : overlay.cacheStatus === "AVAILABLE"
                            ? "기준: 현재 등록된 산업 분류"
                            : "기준 시각 확인 전"}
                    </p>
                    {overlay.policySelectable !== false && overlay.cacheStatus === "HIT" && (
                      <div className="mt-3 grid grid-cols-1 gap-1.5">
                        {[
                          { value: "REUSE_AVAILABLE" as const, label: "최근 결과 사용해서 분석" },
                          { value: "FORCE_REFRESH" as const, label: "새로 분석하기" },
                        ].map((option) => {
                          const checked = (overlayCachePolicies[overlay.overlayType] ?? "REUSE_AVAILABLE") === option.value;
                          return (
                            <label
                              key={`${overlay.overlayType}-${option.value}`}
                              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold cursor-pointer transition-colors ${
                                checked
                                  ? "border-accent bg-accent-soft text-accent"
                                  : "border-line bg-surface text-ink-3 hover:border-accent/40"
                              }`}
                            >
                              <input
                                type="checkbox"
                                checked={checked}
                                onChange={() => {
                                  setOverlayCachePolicies((prev) => ({
                                    ...prev,
                                    [overlay.overlayType]: option.value,
                                  }));
                                }}
                                className="h-3.5 w-3.5 rounded border-line accent-accent"
                              />
                              <span>{option.label}</span>
                            </label>
                          );
                        })}
                      </div>
                    )}
                    <p className="mt-2 text-xs font-mono tabular text-ink-4">
                      +{overlay.policySelectable !== false && overlay.cacheStatus === "HIT" && overlayCachePolicies[overlay.overlayType] === "FORCE_REFRESH"
                        ? 1
                        : overlay.additionalCredit} credit
                    </p>
                  </div>
                ))}
              </div>
              <div className="mt-4 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setOverlayPreview(null)}
                  className="px-4 py-2 rounded-lg text-sm font-semibold bg-bg-sunk text-ink border border-line hover:bg-surface"
                >
                  취소
                </button>
                <button
                  type="button"
                  onClick={() => void handleAnalyzeClick(true)}
                  className="px-4 py-2 rounded-lg text-sm font-semibold bg-ink text-bg hover:opacity-90"
                >
                  이 조건으로 분석
                </button>
              </div>
            </section>
          )}

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
            <section className="qaima-portfolio-report qaima-report-enter w-full flex flex-col gap-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <h2 className="text-lg font-bold text-ink tracking-tight">분석 결과</h2>
                <div className="inline-flex self-start sm:self-auto rounded-xl bg-bg-sunk border border-line p-1">
                  <button
                    type="button"
                    onClick={() => setAnalysisTab("BASIC")}
                    className={`px-4 py-2 rounded-lg text-sm font-bold transition-colors ${
                      analysisTab === "BASIC"
                        ? "bg-surface text-ink shadow-card"
                        : "text-ink-3 hover:text-ink"
                    }`}
                  >
                    리스크 요약
                  </button>
                  <button
                    type="button"
                    onClick={() => setAnalysisTab("ADVANCED")}
                    className={`px-4 py-2 rounded-lg text-sm font-bold transition-colors ${
                      analysisTab === "ADVANCED"
                        ? "bg-surface text-ink shadow-card"
                        : "text-ink-3 hover:text-ink"
                    }`}
                  >
                    최적화 관측
                  </button>
                </div>
              </div>

              {analysisTab === "BASIC" && <div ref={portfolioReportRef} className="qaima-scroll-stagger flex flex-col gap-5">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* 카드 1: 위험 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    위험 수준
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {(analysisResult.currentPortfolio.volatility * 100).toFixed(1)}
                      <span className="text-base font-normal ml-0.5 text-ink-4">%</span>
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">포트폴리오 변동성</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.summary.riskLevel === "LOW"
                        ? "bg-success/10 text-success"
                        : analysisResult.summary.riskLevel === "MID"
                          ? "bg-warn/10 text-warn"
                          : "bg-danger/10 text-danger"
                    }`}
                  >
                    {riskLevelLabel[analysisResult.summary.riskLevel] ?? analysisResult.summary.riskLevel}
                  </span>
                </div>

                {/* 카드 2: 분산 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    성향 기준 변동성
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {(analysisResult.summary.targetVolatility * 100).toFixed(1)}
                      <span className="text-base font-normal ml-0.5 text-ink-4">%</span>
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">
                    투자 성향 기준 목표 변동성입니다
                  </p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.summary.suitability === "CONSERVATIVE_THAN_PROFILE"
                        ? "bg-success/10 text-success"
                        : analysisResult.summary.suitability === "ALIGNED"
                          ? "bg-warn/10 text-warn"
                          : "bg-danger/10 text-danger"
                    }`}
                  >
                    {suitabilityLabel[analysisResult.summary.suitability] ?? analysisResult.summary.suitability}
                  </span>
                </div>

                {/* 카드 3: 효율성 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    성향 프로필
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {analysisResult.policy.riskProfile.profileType}
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">심리 기반 투자 성향</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.summary.volatilityGap <= 0
                        ? "bg-success/10 text-success"
                        : "bg-danger/10 text-danger"
                    }`}
                  >
                    차이 {(analysisResult.summary.volatilityGap * 100).toFixed(1)}%
                  </span>
                </div>
              </div>

              {analysisResult.explain?.sections?.coreRisk ? (
                <div>
                  {renderExplainSection(analysisResult.explain.sections.coreRisk)}
                </div>
              ) : null}

              {(() => {
                const basicPortfolioCards = [
                  analysisResult.currentPortfolio,
                  ...["STABLE", "BALANCED", "AGGRESSIVE", "PSYCHOLOGICAL"]
                    .map((type) => analysisResult.basicPortfolios.find((portfolio) => portfolio.type === type))
                    .filter((portfolio): portfolio is NonNullable<typeof portfolio> => Boolean(portfolio)),
                ];
                return (
                  <section className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                    <h3 className="text-base font-bold text-ink">변동성 기반 분석</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-3">
                      가격 시계열의 공분산과 현금 한도를 기준으로 목표 변동성에 가까운 포트폴리오를 비교합니다.
                      <br></br>
                      LOW/MID/HIGH는 절대 위험등급이 아닌 각 카드의 목표 변동성 대비 실현 변동성 수준입니다.
                    </p>
                    <div className="mt-3">
                      {renderExplainSection(analysisResult.explain?.sections?.volatilityAnalysis, { hideTitle: true })}
                    </div>
                    <div className="mt-3 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                      {basicPortfolioCards.map((portfolio) => (
                        <div key={portfolio.type} className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-bold text-ink">
                                {portfolioTypeLabel[portfolio.type] ?? portfolio.label}
                              </p>
                              <p className="text-xs mt-1 text-ink-4">{portfolio.userDescription}</p>
                            </div>
                            <span
                              title="각 카드의 목표 변동성 대비 실현 변동성 등급입니다."
                              className={`px-2 py-1 rounded-full text-[11px] font-bold ${
                                portfolio.riskLevel === "LOW"
                                  ? "bg-success/10 text-success"
                                  : portfolio.riskLevel === "MID"
                                    ? "bg-warn/10 text-warn"
                                    : "bg-danger/10 text-danger"
                              }`}
                            >
                              {riskLevelLabel[portfolio.riskLevel] ?? portfolio.riskLevel}
                            </span>
                          </div>

                          <div className="mt-4 flex items-center gap-4">
                            <div
                              className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                              style={portfolioPieStyle(portfolio.weights)}
                              aria-label={`${portfolio.label} 비중 차트`}
                            />
                            <div className="flex-1 min-w-0">
                              <p className="text-xs text-ink-4">변동성</p>
                              <p className="text-2xl font-bold text-ink font-mono tabular tracking-tighter">
                                {formatPct(portfolio.volatility)}
                              </p>
                              <p className="mt-1 text-[11px] text-ink-4 font-mono tabular">
                                Sharpe {portfolio.sharpeRatio?.toFixed(2) ?? "-"}
                              </p>
                              {portfolio.targetVolatility !== null && portfolio.targetVolatility !== undefined && (
                                <p className="text-[11px] text-ink-4">
                                  기준 {formatPct(portfolio.targetVolatility)}
                                </p>
                              )}
                            </div>
                          </div>

                          <div className="mt-4 flex flex-col gap-2">
                      {portfolio.weights.map((weight) => (
                              <div key={`${portfolio.type}-${weight.stockCode}`} className="flex items-center gap-2">
                                <span className="w-20 truncate text-xs text-ink-3">
                                  {weight.companyName ?? weight.stockCode}
                                </span>
                                <div className="flex-1 h-2 rounded-full bg-bg-sunk overflow-hidden">
                                  <div
                                    className="h-full rounded-full bg-accent"
                                    style={{ width: `${Math.min(100, Math.max(0, weight.weight * 100))}%` }}
                                  />
                                </div>
                                <span className="w-12 text-right text-xs font-mono tabular text-ink-3">
                                  {formatPct(weight.weight, 0)}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </section>
                );
              })()}

	              {(() => {
	                const portfolioLabel = (type: string, label?: string) => {
	                  if (type === "CURRENT") return "현재 포트폴리오";
	                  if (type === "MIN_VOL") return "변동성 최소안";
	                  if (type === "MAX_SHARPE") return "위험 대비 수익 우수안";
	                  if (type === "RISK_ALLOCATION") return "CAL 기반 위험배분";
	                  if (type === "UTILITY_OPTIMAL") return "효용최대안";
	                  if (type === "THEORETICAL_UTILITY") return label ?? "목표 투자안";
	                  return label ?? type;
	                };
	                const theoreticalPortfolio = analysisResult.advanced?.candidatePortfolios.find((portfolio) => portfolio.type === "THEORETICAL_UTILITY") ?? null;
	                const theoreticalBasicLabel = theoreticalPortfolio?.constraintBinding === "CASH_MAX" ? "현금 확대 목표안" : "추가 투자 목표안";
	                const theoreticalFundingLabel = theoreticalPortfolio?.constraintBinding === "CASH_MAX" ? "필요 현금 비중" : "추가 필요액";
	                const theoreticalFundingValue = theoreticalPortfolio?.constraintBinding === "CASH_MAX"
	                  ? `현금 ${formatPct(1 - (theoreticalPortfolio.theoreticalRiskyAllocation ?? 1))}`
	                  : formatKRW(theoreticalPortfolio?.additionalRequiredCash);
	                const portfolioCards = [
	                  analysisResult.currentPortfolio,
	                  ...["MIN_VOL", "MAX_SHARPE", "RISK_ALLOCATION", "UTILITY_OPTIMAL"]
                    .map((type) => analysisResult.advanced?.candidatePortfolios.find((portfolio) => portfolio.type === type))
                    .filter((portfolio): portfolio is NonNullable<typeof portfolio> => Boolean(portfolio)),
                ];

                return (
                  <div className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                    <h3 className="text-base font-bold text-ink">효율성 기반 분석</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-3">
                      기대수익률과 변동성을 함께 고려해 위험 대비 수익 효율이 높은 포트폴리오를 비교합니다. <br></br>
                      평단 기준 현재 수익률은 참고 용도로 제공되며, 분석에 사용되지 않습니다.
                    </p>
                    <div className="mt-3">
                      {renderExplainSection(analysisResult.explain?.sections?.efficiencyAnalysis, { hideTitle: true })}
                    </div>
                    <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                      {portfolioCards.map((portfolio) => (
                        <div key={`basic-pie-${portfolio.type}`} className="rounded-lg bg-bg-sunk border border-line p-3">
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-xs font-bold text-ink">{portfolioLabel(portfolio.type, portfolio.label)}</p>
                            <span className="text-[10px] font-mono tabular text-ink-4">
                              Sharpe {portfolio.sharpeRatio?.toFixed(2) ?? "-"}
                            </span>
                          </div>
                          <div className="mt-3 flex items-center gap-3">
                            <div
                              className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                              style={portfolioPieStyle(portfolio.weights)}
                              aria-label={`${portfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                            />
                            <div className="min-w-0 flex-1">
                              <div className="grid grid-cols-2 gap-2 text-[10px] text-ink-4">
                                <div>
                                  <p>σ</p>
                                  <p className="mt-0.5 font-mono tabular text-ink">{formatPct(portfolio.volatility)}</p>
                                </div>
                                <div>
                                  <p>E[R]</p>
                                  <p className="mt-0.5 font-mono tabular text-ink">
                                    {portfolio.expectedReturn !== null && portfolio.expectedReturn !== undefined
                                      ? formatPct(portfolio.expectedReturn)
                                      : "-"}
                                  </p>
                                </div>
                              </div>
                              <div className="mt-2 flex flex-col gap-1">
                                {portfolio.weights.map((weight, index) => (
                                  <div key={`${portfolio.type}-${weight.stockCode}`} className="text-[10px]">
                                    <div className="flex items-center gap-1.5">
                                      <span
                                        className="w-2 h-2 rounded-full flex-shrink-0"
                                        style={{ backgroundColor: weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index) }}
                                      />
                                      <span className="min-w-0 flex-1 truncate text-ink-3">
                                        {weight.companyName ?? weight.stockCode}
                                      </span>
                                      <span className="font-mono tabular text-ink">
                                        {formatPct(weight.weight)}
                                      </span>
                                    </div>
                                    {portfolio.type === "CURRENT" && weight.assetType !== "CASH" && (
                                      <div className="mt-0.5 ml-3.5 flex items-center justify-between gap-2 font-mono tabular">
                                        <span className="truncate text-ink-4">평단 기준 현재 수익률</span>
                                        <span className={weight.unrealizedPnl !== undefined && weight.unrealizedPnl !== null && weight.unrealizedPnl < 0 ? "text-danger" : "text-success"}>
                                          {formatPct(weight.unrealizedReturnRate)}
                                        </span>
                                      </div>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          </div>
                        </div>
	                      ))}
	                    </div>
	                    {theoreticalPortfolio && (
	                      <div className="mt-3 rounded-lg bg-bg-sunk border border-danger/30 p-3">
	                        <div className="flex flex-wrap items-center justify-between gap-2">
	                          <div>
	                            <p className="text-xs font-bold text-ink">{theoreticalBasicLabel}</p>
	                            <p className="mt-1 text-[11px] leading-relaxed text-ink-4">
	                              {theoreticalPortfolio.constraintBinding === "CASH_MAX"
	                                ? "지금 설정한 현금 한도보다 현금을 더 많이 둘 때 목표로 삼을 수 있는 구성입니다."
	                                : "현재 투자금만으로는 도달하기 어려워, 추가 현금을 넣을 때 목표로 삼을 수 있는 구성입니다."}
	                            </p>
	                          </div>
	                          <div className="text-right">
	                            <p className="text-[10px] text-ink-4">{theoreticalFundingLabel}</p>
	                            <p className="font-mono tabular text-sm font-bold text-danger">
	                              {theoreticalFundingValue}
	                            </p>
	                          </div>
	                        </div>
	                        <div className="mt-3 flex items-center gap-3">
	                          <div
	                            className="w-20 h-20 rounded-full border border-line flex-shrink-0"
	                            style={portfolioPieStyle(theoreticalPortfolio.weights)}
	                            aria-label={`${theoreticalBasicLabel} 구성비 파이차트`}
	                          />
	                          <div className="min-w-0 flex-1">
	                            <div className="grid grid-cols-3 gap-2 text-[10px] text-ink-4">
	                              <div>
	                                <p>σ</p>
	                                <p className="mt-0.5 font-mono tabular text-ink">{formatPct(theoreticalPortfolio.volatility)}</p>
	                              </div>
	                              <div>
	                                <p>E[R]</p>
	                                <p className="mt-0.5 font-mono tabular text-ink">{formatPct(theoreticalPortfolio.expectedReturn ?? 0)}</p>
	                              </div>
	                              <div>
	                                <p>위험자산 배수</p>
	                                <p className="mt-0.5 font-mono tabular text-ink">{theoreticalPortfolio.theoreticalRiskyAllocation?.toFixed(2) ?? "-"}x</p>
	                              </div>
	                            </div>
	                            <div className="mt-2 flex flex-col gap-1">
	                              {theoreticalPortfolio.weights.map((weight, index) => (
	                                <div key={`basic-theoretical-${weight.stockCode}`} className="flex items-center gap-1.5 text-[10px]">
	                                  <span
	                                    className="w-2 h-2 rounded-full flex-shrink-0"
	                                    style={{ backgroundColor: weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index) }}
	                                  />
	                                  <span className="min-w-0 flex-1 truncate text-ink-3">
	                                    {weight.companyName ?? weight.stockCode}
	                                  </span>
	                                  <span className="font-mono tabular text-ink">{formatPct(weight.weight)}</span>
	                                </div>
	                              ))}
	                            </div>
	                          </div>
	                        </div>
	                      </div>
	                    )}
	                  </div>
	                );
	              })()}

              {(() => {
                const benchmark = analysisResult.advanced?.benchmarkPolicy;
                const capm = analysisResult.advanced?.capmPolicy;
                const expectedPolicy = analysisResult.advanced?.expectedReturnPolicy;
                const capmAssets = ((expectedPolicy?.assets ?? analysisResult.advanced?.scl?.assets ?? []) as Feature3CapmAsset[]);
                if (!benchmark && !capm && !capmAssets.length) return null;
                const averageCapmWeight = typeof expectedPolicy?.averageCapmWeight === "number" ? expectedPolicy.averageCapmWeight : 0;
                const averageBlendConfidence = typeof expectedPolicy?.averageBlendConfidence === "number" ? expectedPolicy.averageBlendConfidence : 0;
                const appliedCount = (capm?.appliedAssetCount ?? 0) + (capm?.partialAssetCount ?? 0);
                const excludedCount = capm?.excludedAssetCount ?? 0;
                const aiSummary = analysisResult.explain?.sections?.efficiencyAnalysis?.summary;
                const plainSummary = aiSummary
                  ?.replaceAll("CAPM", "시장 기준")
                  .replaceAll("historical", "과거 흐름")
                  .replaceAll("Historical", "과거 흐름")
                  .replaceAll("blended expected return", "최종 기대 흐름")
                  .replaceAll("blendedExpectedReturn", "최종 기대 흐름")
                  .replaceAll("blended E[R]", "최종 기대 흐름")
                  .replaceAll("E[R]", "기대 흐름")
                  .replaceAll("SCL/SML", "시장 민감도 진단");
                return (
                  <section className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="text-base font-bold text-ink">시장 기준 분석</h3>
                        <p className="mt-1 text-sm leading-relaxed text-ink-3">
                          과거 가격 흐름과 시장 기준 흐름을 함께 반영해 최종 기대 흐름이 어떻게 잡혔는지 확인합니다.
                        </p>
                      </div>
                      <span className="rounded-md border border-line bg-bg-sunk px-2 py-1 text-[11px] font-mono text-ink-3">
                        {capmStatusLabel[capm?.status ?? "UNAVAILABLE"] ?? capm?.status ?? "데이터 없음"}
                      </span>
                    </div>

                    {plainSummary ? (
                      <div className="mt-3 rounded-xl bg-bg-sunk border border-line p-4">
                        <p className="text-xs leading-relaxed text-ink-3">{plainSummary}</p>
                      </div>
                    ) : null}

                    <div className="mt-3 grid grid-cols-1 md:grid-cols-4 gap-2">
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">벤치마크</p>
                        <p className="mt-1 text-sm font-bold text-ink">{benchmark?.benchmarkName ?? benchmark?.benchmarkCode ?? "-"}</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">{benchmarkSourceLabel[benchmark?.source ?? ""] ?? "-"} · 결측 {formatPct(benchmark?.missingRate)}</p>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">반영된 종목</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{appliedCount}개</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">제외 {excludedCount}개 · 부분 {capm?.partialAssetCount ?? 0}개</p>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">시장 기준 반영 비중</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{formatPct(averageCapmWeight)}</p>
                        <div className="mt-2 h-2 rounded-full bg-surface overflow-hidden">
                          <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, Math.max(0, averageCapmWeight * 100))}%` }} />
                        </div>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">평균 신뢰도</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{formatPct(averageBlendConfidence)}</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">낮을수록 과거 흐름 중심</p>
                      </div>
                    </div>

                    {capmAssets.length ? (
                      <div className="mt-3 grid grid-cols-1 xl:grid-cols-2 gap-3">
                        <div className="rounded-lg bg-bg-sunk border border-line p-3">
                          <h4 className="text-xs font-bold text-ink">종목별 최종 기대 흐름</h4>
                          <div className="mt-3 flex flex-col gap-3">
                            {capmAssets.slice(0, 8).map((asset) => {
                              const capmWeight = Math.min(1, Math.max(0, asset.capmWeight ?? 0));
                              const historicalWeight = Math.min(1, Math.max(0, asset.historicalWeight ?? (1 - capmWeight)));
                              return (
                                <div key={`capm-basic-bar-${asset.stockCode}`}>
                                  <div className="flex items-center justify-between gap-2 text-[11px]">
                                    <span className="font-semibold text-ink truncate">{asset.companyName ?? asset.stockCode}</span>
                                    <span className="font-mono tabular text-ink-4">
                                      과거 {formatPct(historicalWeight, 0)} · 시장 {formatPct(capmWeight, 0)}
                                    </span>
                                  </div>
                                  <div className="mt-1 flex h-2 rounded-full bg-surface overflow-hidden">
                                    <div className="h-full bg-slate-400" style={{ width: `${historicalWeight * 100}%` }} />
                                    <div className="h-full bg-accent" style={{ width: `${capmWeight * 100}%` }} />
                                  </div>
                                  <div className="mt-1 grid grid-cols-3 gap-2 text-[10px] text-ink-4">
                                    <span>과거 흐름 {formatPct(asset.historicalExpectedReturn)}</span>
                                    <span>시장 기준 {asset.capmExpectedReturn !== null && asset.capmExpectedReturn !== undefined ? formatPct(asset.capmExpectedReturn) : "-"}</span>
                                    <span className="text-ink">최종 {formatPct(asset.blendedExpectedReturn)}</span>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>

                        <div className="rounded-lg bg-bg-sunk border border-line p-3">
                          <h4 className="text-xs font-bold text-ink">종목별 결과 요약</h4>
                          <div className="mt-2 overflow-x-auto">
                            <table className="w-full min-w-[460px] text-left text-[11px]">
                              <thead className="text-ink-4">
                                <tr>
                                  <th className="py-2 pr-3">종목</th>
                                  <th className="py-2 pr-3">시장 반영</th>
                                  <th className="py-2 pr-3">최종 기대 흐름</th>
                                  <th className="py-2 pr-3">상태</th>
                                </tr>
                              </thead>
                              <tbody>
                                {capmAssets.slice(0, 8).map((asset) => (
                                  <tr key={`capm-basic-table-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                    <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                    <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.capmWeight)}</td>
                                    <td className="py-2 pr-3 font-mono tabular text-ink">{formatPct(asset.blendedExpectedReturn)}</td>
                                    <td className="py-2 pr-3">{capmAssetStatusLabel[asset.status ?? ""] ?? "-"}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    ) : null}

                    <p className="mt-3 rounded-lg bg-bg-sunk border border-line px-3 py-2 text-[11px] leading-relaxed text-ink-4">
                      시장 기준 분석은 종목이 시장 흐름과 얼마나 같이 움직였는지 참고해 최종 기대 흐름을 보정한 결과입니다. 예상 수익률 보장이나 추천 비중으로 해석하지 않습니다.
                    </p>
                  </section>
                );
              })()}

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                  <h3 className="text-base font-bold text-ink">위험 기여도</h3>
                  <p className="mt-1 text-xs text-ink-4">보유 비중보다 실제 위험을 많이 만드는 종목을 확인합니다.</p>
                  <div className="mt-4 flex flex-col gap-3">
                    {analysisResult.currentPortfolio.riskContributions.map((contribution) => (
                      <div key={contribution.stockCode}>
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-sm font-medium text-ink truncate">
                            {contribution.companyName ?? contribution.stockCode}
                          </span>
                          <span className="text-sm font-mono tabular text-ink-3">
                            {formatPct(contribution.riskContributionPct)}
                          </span>
                        </div>
                        <div className="mt-1 h-2.5 rounded-full bg-bg-sunk overflow-hidden">
                          <div
                            className="h-full rounded-full bg-danger"
                            style={{ width: `${Math.min(100, Math.max(0, contribution.riskContributionPct * 100))}%` }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                  <h3 className="text-base font-bold text-ink">주요 해석</h3>
                  <div className="mt-4 flex flex-col gap-3">
                    {analysisResult.riskDrivers.map((driver) => (
                      <div key={driver.code} className="rounded-xl bg-bg-sunk border border-line p-3">
                        <div className="flex items-center justify-between gap-2">
                          <p className="text-sm font-bold text-ink">{driver.title}</p>
                          <span className="text-[11px] font-bold text-ink-4">{featureSourceLabel[driver.source] ?? driver.source}</span>
                        </div>
                        <p className="mt-1 text-xs leading-relaxed text-ink-3">{driver.description}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {analysisResult.overlays?.adjustedPortfolios?.length ? (
                <section className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                  <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-3">
                    <div>
	                      <h3 className="text-base font-bold text-ink">보조 관측 시나리오</h3>
	                      <p className="mt-1 text-sm leading-relaxed text-ink-3">
	                        핵심 리스크 최적화 결과를 대체하지 않는 보조 관측 기반 민감도 분석입니다.
	                        선택한 보조 관측이 현재 구성의 확대·축소·분산 판단에 미치는 영향을 보여주며, 투자 추천 비중이나 수익률 최적해로 해석하지 않습니다.
	                      </p>
                    </div>
                    <span className="text-xs font-mono tabular text-ink-4">
                      signals {analysisResult.overlays.overlaySignals?.length ?? 0}
                    </span>
                  </div>

                  {analysisResult.overlays.visualizations?.length ? (
                    <div className="mt-5 grid grid-cols-1 lg:grid-cols-2 gap-4">
                      {analysisResult.overlays.visualizations.map((viz) => (
                        <div key={viz.type} className="rounded-xl bg-bg-sunk border border-line p-4">
                          <h4 className="text-sm font-bold text-ink">{viz.title}</h4>
                          <div className="mt-3 flex flex-col gap-2">
                            {viz.items.map((item) => {
                              return (
                                <div key={`${viz.type}-${item.stockCode}`} className="grid grid-cols-[96px_1fr_48px] items-center gap-2">
                                  <span className="text-xs text-ink-3 truncate">{item.companyName ?? item.stockCode}</span>
                                  <div className="relative h-2.5 rounded-full bg-surface overflow-hidden">
                                    <div className="absolute left-1/2 top-0 h-full w-px bg-line" />
                                    <div
                                      className={`h-full rounded-full ${item.score >= 0 ? "bg-success" : "bg-danger"}`}
                                      style={{
                                        width: `${Math.abs(item.score) * 50}%`,
                                        marginLeft: item.score >= 0 ? "50%" : `${50 - Math.abs(item.score) * 50}%`,
                                      }}
                                    />
                                  </div>
                                  <span className="text-right text-xs font-mono tabular text-ink-4">{item.score.toFixed(2)}</span>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : null}

                  <div className="mt-5">
                    {renderExplainSection(analysisResult.explain?.sections?.overlayObservations)}
                  </div>

	                  <div className="mt-5 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
	                    {analysisResult.overlays.adjustedPortfolios.map((portfolio) => {
                        const baseWeights = weightMapByStock(analysisResult.currentPortfolio.weights);
                        const maxAbsDelta = Math.max(
                          ...portfolio.weights.map((weight) => Math.abs(weight.weight - (baseWeights.get(weight.stockCode) ?? 0))),
                          0,
                        );
                        return (
	                      <div key={portfolio.type} className="rounded-xl bg-bg-sunk border border-line p-4">
	                        <div className="flex items-start justify-between gap-3">
	                          <div>
	                            <p className="text-sm font-bold text-ink">{portfolioTypeLabel[portfolio.type] ?? portfolio.label}</p>
	                            <p className="mt-1 text-xs leading-relaxed text-ink-3">{portfolio.userDescription}</p>
	                          </div>
	                          <span className="text-[11px] font-bold text-ink-4">{riskLevelLabel[portfolio.riskLevel] ?? portfolio.riskLevel}</span>
	                        </div>
                        <div className="mt-4 flex items-center gap-3">
                          <div
                            className="w-16 h-16 rounded-full border border-line flex-shrink-0"
                            style={portfolioPieStyle(portfolio.weights)}
                            aria-label={`${portfolio.label} 구성비 파이차트`}
                          />
                          <div className="min-w-0">
                            <p className="text-[11px] text-ink-4">변동성</p>
	                            <p className="text-lg font-bold font-mono tabular text-ink">{formatPct(portfolio.volatility)}</p>
	                            <p className="mt-0.5 text-[11px] font-mono tabular text-ink-4">
	                              최대 변화 {formatPctPoint(maxAbsDelta)}
	                            </p>
	                          </div>
	                        </div>
	                        <div className="mt-4 flex flex-col gap-1.5">
	                          {portfolio.weights.map((weight) => {
                              const baseWeight = baseWeights.get(weight.stockCode) ?? 0;
                              const delta = weight.weight - baseWeight;
                              const deltaClass = Math.abs(delta) < 0.0005
                                ? "text-ink-4"
                                : delta > 0
                                  ? "text-success"
                                  : "text-danger";
                              return (
	                            <div key={`${portfolio.type}-${weight.stockCode}`} className="flex items-center gap-2">
	                              <span className="w-20 truncate text-xs text-ink-3">{weight.companyName ?? weight.stockCode}</span>
	                              <div className="flex-1 h-2 rounded-full bg-surface overflow-hidden">
	                                <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, Math.max(0, weight.weight * 100))}%` }} />
	                              </div>
	                              <div className="w-[118px] text-right font-mono tabular">
	                                <p className="text-[11px] text-ink-3">{formatPct(baseWeight)} → {formatPct(weight.weight)}</p>
	                                <p className={`text-[10px] ${deltaClass}`}>{formatPctPoint(delta)}</p>
	                              </div>
	                            </div>
	                            );
                            })}
	                        </div>
	                      </div>
	                    );
                      })}
	                  </div>

                  <div className="mt-5">
                    {renderExplainSection(analysisResult.explain?.sections?.portfolioComparison)}
                  </div>

                  {analysisResult.overlays.explanations?.length ? (
                    <div className="mt-5 grid grid-cols-1 lg:grid-cols-2 gap-3">
                      {analysisResult.overlays.explanations.slice(0, 8).map((item, index) => (
                        <div key={`${item.overlayType}-${item.stockCode}-${index}`} className="rounded-xl bg-bg-sunk border border-line p-3">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="text-[11px] font-semibold text-ink-4 truncate">
                                {item.companyName ?? item.stockCode}
                                {item.companyName && item.stockCode ? <span className="ml-1 font-mono tabular">({item.stockCode})</span> : null}
                              </p>
                              <p className="mt-0.5 text-sm font-bold text-ink">{item.title}</p>
                            </div>
                            <span className={`flex-shrink-0 text-xs font-mono tabular ${item.score >= 0 ? "text-success" : "text-danger"}`}>
                              {item.score.toFixed(2)}
                            </span>
                          </div>
                          <div className="mt-1 text-xs leading-relaxed text-ink-3">{renderOverlayValue(item.overlayType, item.description)}</div>
                        </div>
                      ))}
                    </div>
                  ) : null}

                  {analysisResult.explain?.sections?.finalJudgement ? (
                    <div className="mt-5">
                      {renderExplainSection(analysisResult.explain.sections.finalJudgement)}
                    </div>
                  ) : analysisResult.explain?.text && !explainSections(analysisResult.explain).length ? (
                    <div className="mt-5 rounded-xl bg-bg-sunk border border-line p-4">
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="text-base font-bold text-ink">설명 요약</h3>
                        <span className="text-[11px] font-bold text-ink-4">{analysisResult.explain.provider}</span>
                      </div>
                      <p className="mt-3 text-sm leading-relaxed text-ink-3">{analysisResult.explain.text}</p>
                    </div>
                  ) : null}
                </section>
              ) : null}

              {analysisResult.warnings.length > 0 && (
                <div className="rounded-2xl p-4 bg-warn/10 border border-warn/20">
                  <p className="text-sm font-bold text-ink">참고해주세요</p>
                  <div className="mt-2 flex flex-col gap-1">
                    {analysisResult.warnings.slice(0, 4).map((warning) => (
                      <p key={`${warning.code}-${warning.target ?? "global"}`} className="text-xs text-ink-3">
                        {warning.userMessage ?? warning.message}
                      </p>
                    ))}
                  </div>
                </div>
              )}

              </div>}

              {analysisTab === "ADVANCED" && <div ref={portfolioReportRef} className="qaima-scroll-stagger rounded-2xl p-5 bg-surface border border-line shadow-card">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-base font-bold text-ink">분석 진단</h3>
                    <p className="mt-1 text-xs text-ink-4">{analysisResult.freshness.userMessage}</p>
                  </div>
                  <span className="text-xs font-mono tabular text-ink-4">
                    분석대상 {analysisResult.policy.dataQuality.includedHoldingCount}개 · 제외 {analysisResult.policy.dataQuality.excludedHoldingCount}개
                  </span>
                </div>

                <div className="mt-4 grid grid-cols-1 lg:grid-cols-5 gap-3">
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4">가격 시계열 기준</p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {pricePolicyLabel[analysisResult.policy.pricePolicy.used] ?? analysisResult.policy.pricePolicy.used}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4">수익률 표본수</p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {analysisResult.advanced?.covarianceDiagnostics?.sampleSize ?? 0}일
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4">공통 결측률</p>
                    <p className="mt-1 text-sm font-bold text-ink font-mono tabular">
                      {formatPct(analysisResult.policy.dataQuality.commonMissingRate)}
                    </p>
                    <p className="mt-0.5 text-[11px] text-ink-4">
                      기준 {formatPct(analysisResult.policy.dataPolicy.maxCommonMissingRate, 0)} · 공통 {analysisResult.policy.dataQuality.commonPriceCount}일
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4">공분산 추정 모형</p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {covarianceModelLabel[analysisResult.advanced?.covarianceDiagnostics?.usedCovarianceModel ?? ""] ?? analysisResult.advanced?.covarianceDiagnostics?.usedCovarianceModel ?? "-"}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4">무위험수익률 r_f</p>
                    <p className="mt-1 text-sm font-bold text-ink font-mono tabular">
                      {formatPct(analysisResult.policy.riskFreePolicy?.rate ?? 0)}
                    </p>
                    <p className="mt-0.5 text-[11px] text-ink-4">
                      {analysisResult.policy.riskFreePolicy?.instrumentName ?? analysisResult.policy.riskFreePolicy?.source ?? "-"}
                    </p>
                  </div>
                </div>

                {(() => {
                  const benchmark = analysisResult.advanced?.benchmarkPolicy;
                  const capm = analysisResult.advanced?.capmPolicy;
                  const expectedPolicy = analysisResult.advanced?.expectedReturnPolicy;
                  const capmAssets = ((expectedPolicy?.assets ?? analysisResult.advanced?.scl?.assets ?? []) as Feature3CapmAsset[]);
                  const sclSeries = (analysisResult.advanced?.scl?.series ?? []) as Feature3SclSeries[];
                  const selectableSclSeries = sclSeries.filter((series) => (series.points?.length ?? 0) > 1);
                  const fallbackSclSeries = selectableSclSeries[0] ?? null;
                  const activeSclSeries = (
                    selectedSclStockCode
                      ? selectableSclSeries.find((series) => series.stockCode === selectedSclStockCode)
                      : null
                  ) ?? fallbackSclSeries;
                  const activeSclAsset = activeSclSeries
                    ? capmAssets.find((asset) => asset.stockCode === activeSclSeries.stockCode)
                    : null;
                  const sclPoints = activeSclSeries?.points ?? [];
                  const sclLine = activeSclSeries?.line;
                  const smlGroups = analysisResult.advanced?.sml?.groups ?? [];
                  const activeSmlGroup = (
                    selectedSmlBenchmarkCode
                      ? smlGroups.find((group) => group.benchmarkCode === selectedSmlBenchmarkCode)
                      : null
                  ) ?? smlGroups[0] ?? null;
                  const smlLine = activeSmlGroup?.line ?? analysisResult.advanced?.sml?.line ?? [];
                  const smlAssets = ((activeSmlGroup?.assets ?? analysisResult.advanced?.sml?.assets ?? capmAssets) as Feature3CapmAsset[]);
                  const primaryBenchmark = benchmark?.benchmarks?.[0] ?? benchmark;
                  if (!benchmark && !capm && !capmAssets.length) return null;
                  const betaValues = [
                    ...smlLine.map((point) => point.beta),
                    ...smlAssets.map((asset) => asset.beta).filter((value): value is number => value !== null && value !== undefined),
                  ];
                  const returnValues = [
                    ...smlLine.map((point) => point.expectedReturn),
                    ...smlAssets
                      .flatMap((asset) => [asset.capmExpectedReturn, asset.historicalExpectedReturn, asset.blendedExpectedReturn])
                      .filter((value): value is number => value !== null && value !== undefined),
                  ];
                  const minBeta = Math.min(...betaValues, -1);
                  const maxBeta = Math.max(...betaValues, 1);
                  const minReturn = Math.min(...returnValues, -0.1);
                  const maxReturn = Math.max(...returnValues, 0.1);
                  const betaPad = Math.max((maxBeta - minBeta) * 0.12, 0.2);
                  const returnPad = Math.max((maxReturn - minReturn) * 0.18, 0.03);
                  const chartMinBeta = minBeta - betaPad;
                  const chartMaxBeta = maxBeta + betaPad;
                  const chartMinReturn = minReturn - returnPad;
                  const chartMaxReturn = maxReturn + returnPad;
                  const smlX = (beta: number) => 44 + ((beta - chartMinBeta) / Math.max(chartMaxBeta - chartMinBeta, 0.0001)) * 292;
                  const smlY = (ret: number) => 168 - ((ret - chartMinReturn) / Math.max(chartMaxReturn - chartMinReturn, 0.0001)) * 128;
                  const toSmlBeta = (svgX: number) => chartMinBeta + ((Math.min(336, Math.max(44, svgX)) - 44) / 292) * (chartMaxBeta - chartMinBeta);
                  const toSmlReturn = (svgY: number) => chartMaxReturn - ((Math.min(168, Math.max(40, svgY)) - 40) / 128) * (chartMaxReturn - chartMinReturn);
                  const smlPath = smlLine.length
                    ? smlLine
                        .sort((a, b) => a.beta - b.beta)
                        .map((point, index) => `${index === 0 ? "M" : "L"} ${smlX(point.beta).toFixed(1)} ${smlY(point.expectedReturn).toFixed(1)}`)
                        .join(" ")
                    : "";
                  const sclMarketValues = sclPoints.map((point) => point.marketReturn).filter(Number.isFinite);
                  const sclAssetValues = sclPoints.map((point) => point.assetReturn).filter(Number.isFinite);
                  const sclMinXRaw = Math.min(...sclMarketValues, -0.01);
                  const sclMaxXRaw = Math.max(...sclMarketValues, 0.01);
                  const sclMinYRaw = Math.min(...sclAssetValues, -0.01);
                  const sclMaxYRaw = Math.max(...sclAssetValues, 0.01);
                  const sclPadX = Math.max((sclMaxXRaw - sclMinXRaw) * 0.12, 0.005);
                  const sclPadY = Math.max((sclMaxYRaw - sclMinYRaw) * 0.12, 0.005);
                  const sclMinX = sclMinXRaw - sclPadX;
                  const sclMaxX = sclMaxXRaw + sclPadX;
                  const sclMinY = sclMinYRaw - sclPadY;
                  const sclMaxY = sclMaxYRaw + sclPadY;
                  const sclX = (value: number) => 44 + ((value - sclMinX) / Math.max(sclMaxX - sclMinX, 0.0001)) * 292;
                  const sclY = (value: number) => 168 - ((value - sclMinY) / Math.max(sclMaxY - sclMinY, 0.0001)) * 128;
                  const toSclMarket = (svgX: number) => sclMinX + ((Math.min(336, Math.max(44, svgX)) - 44) / 292) * (sclMaxX - sclMinX);
                  const toSclAsset = (svgY: number) => sclMaxY - ((Math.min(168, Math.max(40, svgY)) - 40) / 128) * (sclMaxY - sclMinY);
                  const regressionY = (marketReturn: number) => (sclLine?.dailyAlpha ?? 0) + (sclLine?.beta ?? 0) * marketReturn;
                  const sclRegressionPath = activeSclSeries && sclLine?.beta !== null && sclLine?.beta !== undefined
                    ? `M ${sclX(sclMinXRaw).toFixed(1)} ${sclY(regressionY(sclMinXRaw)).toFixed(1)} L ${sclX(sclMaxXRaw).toFixed(1)} ${sclY(regressionY(sclMaxXRaw)).toFixed(1)}`
                    : "";
                  const handleSmallChartHover = (
                    event: MouseEvent<SVGSVGElement>,
                    setter: (value: FrontierHoverState | null) => void,
                  ) => {
                    const svg = event.currentTarget;
                    const matrix = svg.getScreenCTM();
                    if (!matrix) return;
                    const point = svg.createSVGPoint();
                    point.x = event.clientX;
                    point.y = event.clientY;
                    const svgPoint = point.matrixTransform(matrix.inverse());
                    setter({
                      x: Math.min(336, Math.max(44, svgPoint.x)),
                      y: Math.min(168, Math.max(40, svgPoint.y)),
                    });
                  };
                  const sclHoverMarket = sclHover ? toSclMarket(sclHover.x) : null;
                  const sclHoverAsset = sclHover ? toSclAsset(sclHover.y) : null;
                  const smlHoverBeta = smlHover ? toSmlBeta(smlHover.x) : null;
                  const smlHoverReturn = smlHover ? toSmlReturn(smlHover.y) : null;
                  return (
                    <div className="mt-4 rounded-xl bg-bg-sunk border border-line p-3">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <h4 className="text-sm font-bold text-ink">CAPM · SCL · SML 기대수익률 혼합</h4>
                          <p className="mt-1 text-xs leading-relaxed text-ink-4">
                            최적화 입력 E[R]은 과거 흐름 추정값과 CAPM 기대수익률을 표본수·벤치마크 품질·설명력 기반 신뢰도로 혼합한 값입니다.
                          </p>
                        </div>
                        <span className="rounded-md border border-line bg-surface px-2 py-1 text-[11px] font-mono text-ink-3">
                          {capmStatusLabel[capm?.status ?? "UNAVAILABLE"] ?? capm?.status ?? "데이터 없음"}
                        </span>
                      </div>
                      <div className="mt-3 grid grid-cols-1 md:grid-cols-4 gap-2">
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">벤치마크</p>
                          <p className="mt-1 text-xs font-bold text-ink">
                            {benchmark?.mode === "MULTI_BENCHMARK"
                              ? `상장시장별 ${benchmark.benchmarks?.length ?? 0}개`
                              : primaryBenchmark?.benchmarkName ?? primaryBenchmark?.benchmarkCode ?? "-"}
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {benchmark?.mode ?? "SINGLE_BENCHMARK"} · {benchmarkSourceLabel[primaryBenchmark?.source ?? ""] ?? "-"}
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">벤치마크 결측률</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">{formatPct(primaryBenchmark?.missingRate)}</p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {primaryBenchmark?.availablePriceCount ?? 0}/{primaryBenchmark?.expectedTradingDayCount ?? 0}
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">CAPM 반영 종목</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">
                            {(capm?.appliedAssetCount ?? 0) + (capm?.partialAssetCount ?? 0)}개
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            제외 {capm?.excludedAssetCount ?? 0}개
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">평균 CAPM 반영 비중</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">
                            {formatPct(typeof expectedPolicy?.averageCapmWeight === "number" ? expectedPolicy.averageCapmWeight : 0)}
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            신뢰도 {formatPct(typeof expectedPolicy?.averageBlendConfidence === "number" ? expectedPolicy.averageBlendConfidence : 0)}
                          </p>
                        </div>
                      </div>
                      {capmAssets.length ? (
                        <div className="mt-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <h5 className="text-xs font-bold text-ink">최종 E[R]</h5>
                            <p className="text-[11px] text-ink-4">
                              과거 흐름 E[R]과 CAPM E[R]을 신뢰도 기반 반영 비중으로 혼합합니다.
                            </p>
                          </div>
                          <div className="mt-2 overflow-x-auto">
                          <table className="w-full min-w-[900px] text-left text-[11px]">
                            <thead className="text-ink-4">
                              <tr>
                                <th className="py-2 pr-3">종목</th>
                                <th className="py-2 pr-3">벤치마크</th>
                                <th className="py-2 pr-3">상태</th>
                                <th className="py-2 pr-3">표본</th>
                                <th className="py-2 pr-3">β</th>
                                <th className="py-2 pr-3">연율 α</th>
                                <th className="py-2 pr-3">R²</th>
                                <th className="py-2 pr-3">과거 흐름 E[R]</th>
                                <th className="py-2 pr-3">CAPM E[R]</th>
                                <th className="py-2 pr-3">최종 E[R]</th>
                                <th className="py-2 pr-3">CAPM 반영 비중</th>
                                <th className="py-2 pr-3">낮은 비중 근거</th>
                              </tr>
                            </thead>
                            <tbody>
                              {capmAssets.map((asset) => (
                                <tr key={`capm-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                  <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                  <td className="py-2 pr-3">{asset.benchmarkName ?? asset.benchmarkCode ?? "-"}</td>
                                  <td className="py-2 pr-3">{capmAssetStatusLabel[asset.status ?? ""] ?? "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.commonSampleSize ?? 0}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.beta?.toFixed(3) ?? "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.annualAlpha !== null && asset.annualAlpha !== undefined ? formatPct(asset.annualAlpha) : "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.rSquared?.toFixed(3) ?? "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.historicalExpectedReturn)}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.capmExpectedReturn !== null && asset.capmExpectedReturn !== undefined ? formatPct(asset.capmExpectedReturn) : "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular text-ink">{formatPct(asset.blendedExpectedReturn)}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.capmWeight)}</td>
                                  <td className="py-2 pr-3">{capmWeightReasonText(asset)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                          </div>
                        </div>
                      ) : null}
                      {capmAssets.length ? (
                        <div className="mt-3 grid grid-cols-1 xl:grid-cols-2 gap-3">
                          <div className="rounded-lg bg-surface border border-line p-3">
                            <div className="flex items-center justify-between gap-2">
                              <h5 className="text-xs font-bold text-ink">SCL 진단</h5>
                              <span className="text-[10px] text-ink-4">α: 일간 / 연율 구분 표시</span>
                            </div>
                            {selectableSclSeries.length ? (
                              <div className="mt-2 flex flex-wrap gap-1.5">
                                {selectableSclSeries.map((series) => {
                                  const selected = activeSclSeries?.stockCode === series.stockCode;
                                  return (
                                    <button
                                      key={`scl-tab-${series.stockCode}`}
                                      type="button"
                                      onClick={() => setSelectedSclStockCode(series.stockCode)}
                                      className={`rounded-md border px-2 py-1 text-[11px] font-semibold transition ${
                                        selected
                                          ? "border-accent bg-accent text-white"
                                          : "border-line bg-bg-sunk text-ink-3 hover:border-accent/50 hover:text-ink"
                                      }`}
                                    >
                                      {series.companyName ?? capmAssets.find((asset) => asset.stockCode === series.stockCode)?.companyName ?? series.stockCode}
                                    </button>
                                  );
                                })}
                              </div>
                            ) : null}
                            {activeSclSeries ? (
                              <div className="mt-2 rounded-lg bg-bg-sunk border border-line p-2">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                  <div>
                                    <p className="text-[11px] font-bold text-ink">
                                      {activeSclSeries.companyName ?? activeSclAsset?.companyName ?? activeSclSeries.stockCode}
                                    </p>
                                    <p className="mt-0.5 text-[10px] text-ink-4">
                                      {activeSclSeries.benchmarkName ?? activeSclAsset?.benchmarkName ?? activeSclSeries.benchmarkCode ?? activeSclAsset?.benchmarkCode ?? "벤치마크"} 기준 · 종목 수익률 = 일간 α + β × 벤치마크 수익률
                                    </p>
                                  </div>
                                  <div className="flex flex-wrap gap-2 text-[10px] font-mono tabular text-ink-4">
                                    <span>β {sclLine?.beta?.toFixed(3) ?? "-"}</span>
                                    <span>일간 α {sclLine?.dailyAlpha !== null && sclLine?.dailyAlpha !== undefined ? formatPct(sclLine.dailyAlpha, 3) : "-"}</span>
                                    <span>R² {activeSclAsset?.rSquared?.toFixed(3) ?? "-"}</span>
                                  </div>
                                </div>
                                <svg
                                  viewBox="0 0 360 198"
                                  className="mt-2 h-52 w-full cursor-crosshair"
                                  role="img"
                                  aria-label="Security Characteristic Line regression chart"
                                  onMouseMove={(event) => handleSmallChartHover(event, setSclHover)}
                                  onMouseLeave={() => setSclHover(null)}
                                >
                                  <line x1="44" y1="168" x2="336" y2="168" stroke="currentColor" className="text-line" />
                                  <line x1="44" y1="168" x2="44" y2="40" stroke="currentColor" className="text-line" />
                                  <line x1={sclX(0)} y1="40" x2={sclX(0)} y2="168" stroke="#cbd5e1" strokeDasharray="3 4" />
                                  <line x1="44" y1={sclY(0)} x2="336" y2={sclY(0)} stroke="#cbd5e1" strokeDasharray="3 4" />
                                  <text x="336" y="158" textAnchor="end" className="fill-ink-4 text-[10px]">벤치마크 수익률</text>
                                  <text x="44" y="24" className="fill-ink-4 text-[10px]">종목 수익률</text>
                                  {!sclHover && (
                                    <g>
                                      <text x="44" y="184" className="fill-ink-4 text-[9px]">{formatPct(sclMinX)}</text>
                                      <text x="336" y="184" textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(sclMaxX)}</text>
                                      <text x="38" y={sclY(sclMinY)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(sclMinY)}</text>
                                      <text x="38" y={sclY(sclMaxY)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(sclMaxY)}</text>
                                    </g>
                                  )}
                                  {sclRegressionPath && <path d={sclRegressionPath} fill="none" stroke="#dc2626" strokeWidth="2.4" strokeLinecap="round" />}
                                  {sclPoints.slice(-180).map((point, index) => (
                                    <circle
                                      key={`scl-scatter-${activeSclSeries.stockCode}-${point.date ?? index}`}
                                      cx={sclX(point.marketReturn)}
                                      cy={sclY(point.assetReturn)}
                                      r="2.3"
                                      fill="#2563eb"
                                      opacity="0.42"
                                    />
                                  ))}
                                  {sclHover && sclHoverMarket !== null && sclHoverAsset !== null && (
                                    <g pointerEvents="none">
                                      <line x1={sclHover.x} y1="40" x2={sclHover.x} y2="168" stroke="#64748b" strokeDasharray="3 3" opacity="0.7" />
                                      <line x1="44" y1={sclHover.y} x2="336" y2={sclHover.y} stroke="#64748b" strokeDasharray="3 3" opacity="0.7" />
                                      <rect x={Math.min(236, Math.max(50, sclHover.x - 50))} y="170" width="100" height="20" rx="5" fill="#111827" opacity="0.92" />
                                      <text x={Math.min(290, Math.max(96, sclHover.x))} y="184" textAnchor="middle" className="fill-white text-[10px] font-mono">
                                        벤치 {formatPct(sclHoverMarket, 2)}
                                      </text>
                                      <rect x="0" y={Math.min(146, Math.max(42, sclHover.y - 10))} width="62" height="20" rx="5" fill="#111827" opacity="0.92" />
                                      <text x="31" y={Math.min(160, Math.max(56, sclHover.y + 4))} textAnchor="middle" className="fill-white text-[9px] font-mono">
                                        종목:{formatPct(sclHoverAsset, 1)}
                                      </text>
                                    </g>
                                  )}
                                </svg>
                                <div className="mt-1 flex flex-wrap gap-3 text-[10px] text-ink-4">
                                  <span className="text-accent">파란 점: 공통 거래일 일간 로그수익률</span>
                                  <span className="text-danger">빨간 선: SCL 회귀선</span>
                                  <span>최근 {Math.min(180, sclPoints.length)}개 관측치 표시</span>
                                </div>
                              </div>
                            ) : (
                              <div className="mt-2 rounded-lg bg-bg-sunk border border-line p-3 text-[11px] text-ink-4">
                                SCL 회귀선을 그릴 공통 수익률 관측치가 부족합니다.
                              </div>
                            )}
                            <div className="mt-2 overflow-x-auto">
                              <table className="w-full min-w-[560px] text-left text-[11px]">
                                <thead className="text-ink-4">
                                  <tr>
                                    <th className="py-2 pr-3">종목</th>
                                    <th className="py-2 pr-3">β</th>
                                    <th className="py-2 pr-3">일간 α</th>
                                    <th className="py-2 pr-3">연율 α</th>
                                    <th className="py-2 pr-3">R²</th>
                                    <th className="py-2 pr-3">표본</th>
                                    <th className="py-2 pr-3">상태</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {capmAssets.map((asset) => (
                                    <tr key={`scl-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                      <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.beta?.toFixed(3) ?? "-"}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.dailyAlpha !== null && asset.dailyAlpha !== undefined ? formatPct(asset.dailyAlpha, 3) : "-"}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.annualAlpha !== null && asset.annualAlpha !== undefined ? formatPct(asset.annualAlpha) : "-"}</td>
                                      <td className="py-2 pr-3">
                                        <div className="flex items-center gap-2">
                                          <div className="h-1.5 w-16 rounded-full bg-bg-sunk overflow-hidden">
                                            <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, Math.max(0, (asset.rSquared ?? 0) * 100))}%` }} />
                                          </div>
                                          <span className="font-mono tabular">{asset.rSquared?.toFixed(3) ?? "-"}</span>
                                        </div>
                                      </td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.commonSampleSize ?? 0}</td>
                                      <td className="py-2 pr-3">{capmAssetStatusLabel[asset.status ?? ""] ?? "-"}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                            <p className="mt-2 text-[11px] text-ink-4">
                              SCL은 벤치마크 일간 로그수익률과 종목 일간 로그수익률의 관계를 요약한 보조 진단입니다.
                            </p>
                          </div>
                          <div className="rounded-lg bg-surface border border-line p-3">
                            <div className="flex items-center justify-between gap-2">
                              <h5 className="text-xs font-bold text-ink">SML 진단</h5>
                              <span className="text-[10px] text-ink-4">x: β · y: 연율 E[R]</span>
                            </div>
                            {smlGroups.length > 1 ? (
                              <div className="mt-2 flex flex-wrap gap-1.5">
                                {smlGroups.map((group) => {
                                  const selected = activeSmlGroup?.benchmarkCode === group.benchmarkCode;
                                  return (
                                    <button
                                      key={`sml-group-${group.benchmarkCode ?? "unknown"}`}
                                      type="button"
                                      onClick={() => setSelectedSmlBenchmarkCode(group.benchmarkCode ?? null)}
                                      className={`rounded-md border px-2 py-1 text-[11px] font-semibold transition ${
                                        selected
                                          ? "border-accent bg-accent text-white"
                                          : "border-line bg-bg-sunk text-ink-3 hover:border-accent/50 hover:text-ink"
                                      }`}
                                    >
                                      {group.benchmarkName ?? group.benchmarkCode ?? "벤치마크"}
                                    </button>
                                  );
                                })}
                              </div>
                            ) : null}
                            {activeSmlGroup ? (
                              <div className="mt-2 rounded-md bg-bg-sunk border border-line px-2 py-1 text-[10px] text-ink-4">
                                {(activeSmlGroup.benchmarkName ?? activeSmlGroup.benchmarkCode ?? "벤치마크")} · {benchmarkSourceLabel[activeSmlGroup.source ?? ""] ?? activeSmlGroup.source ?? "-"} · {activeSmlGroup.availablePriceCount ?? 0}개 · 결측 {formatPct(activeSmlGroup.missingRate)}
                              </div>
                            ) : null}
                            <svg
                              viewBox="0 0 360 198"
                              className="mt-2 h-52 w-full cursor-crosshair"
                              role="img"
                              aria-label="Security Market Line chart"
                              onMouseMove={(event) => handleSmallChartHover(event, setSmlHover)}
                              onMouseLeave={() => setSmlHover(null)}
                            >
                              <line x1="44" y1="168" x2="336" y2="168" stroke="currentColor" className="text-line" />
                              <line x1="44" y1="168" x2="44" y2="40" stroke="currentColor" className="text-line" />
                              <text x="336" y="158" textAnchor="end" className="fill-ink-4 text-[10px]">β</text>
                              <text x="44" y="24" className="fill-ink-4 text-[10px]">연율 E[R]</text>
                              {!smlHover && (
                                <g>
                                  <text x="44" y="184" className="fill-ink-4 text-[9px]">{chartMinBeta.toFixed(1)}</text>
                                  <text x="336" y="184" textAnchor="end" className="fill-ink-4 text-[9px]">{chartMaxBeta.toFixed(1)}</text>
                                  <text x="38" y={smlY(chartMinReturn)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(chartMinReturn)}</text>
                                  <text x="38" y={smlY(chartMaxReturn)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(chartMaxReturn)}</text>
                                </g>
                              )}
                              {smlPath && <path d={smlPath} fill="none" stroke="#2563eb" strokeWidth="2.4" strokeLinecap="round" />}
                              {smlAssets.map((asset, index) => {
                                if (asset.beta === null || asset.beta === undefined || asset.blendedExpectedReturn === null || asset.blendedExpectedReturn === undefined) return null;
                                const xPoint = smlX(asset.beta);
                                const yPoint = smlY(asset.blendedExpectedReturn);
                                return (
                                  <g key={`sml-point-${asset.stockCode}`}>
                                    <circle cx={xPoint} cy={yPoint} r="4.5" fill={pieColorForIndex(index)} stroke="white" strokeWidth="1.5" />
                                    <text x={xPoint + 6} y={yPoint - 6} className="fill-ink text-[9px] font-semibold">
                                      {asset.companyName ?? asset.stockCode}
                                    </text>
                                  </g>
                                );
                              })}
                              {smlHover && smlHoverBeta !== null && smlHoverReturn !== null && (
                                <g pointerEvents="none">
                                  <line x1={smlHover.x} y1="40" x2={smlHover.x} y2="168" stroke="#64748b" strokeDasharray="3 3" opacity="0.7" />
                                  <line x1="44" y1={smlHover.y} x2="336" y2={smlHover.y} stroke="#64748b" strokeDasharray="3 3" opacity="0.7" />
                                  <rect x={Math.min(266, Math.max(50, smlHover.x - 38))} y="170" width="76" height="20" rx="5" fill="#111827" opacity="0.92" />
                                  <text x={Math.min(304, Math.max(88, smlHover.x))} y="184" textAnchor="middle" className="fill-white text-[10px] font-mono">
                                    β {smlHoverBeta.toFixed(2)}
                                  </text>
                                  <rect x="0" y={Math.min(146, Math.max(42, smlHover.y - 10))} width="58" height="20" rx="5" fill="#111827" opacity="0.92" />
                                  <text x="29" y={Math.min(160, Math.max(56, smlHover.y + 4))} textAnchor="middle" className="fill-white text-[9px] font-mono">
                                    E[R]:{formatPct(smlHoverReturn, 0)}
                                  </text>
                                </g>
                              )}
                            </svg>
                            <div className="mt-2 overflow-x-auto">
                              <table className="w-full min-w-[520px] text-left text-[11px]">
                                <thead className="text-ink-4">
                                  <tr>
                                    <th className="py-2 pr-3">종목</th>
                                    <th className="py-2 pr-3">β</th>
                                    <th className="py-2 pr-3">CAPM E[R]</th>
                                    <th className="py-2 pr-3">과거 흐름 E[R]</th>
                                    <th className="py-2 pr-3">최종 E[R]</th>
                                    <th className="py-2 pr-3">CAPM 반영 비중</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {smlAssets.map((asset) => (
                                    <tr key={`sml-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                      <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.beta?.toFixed(3) ?? "-"}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{asset.capmExpectedReturn !== null && asset.capmExpectedReturn !== undefined ? formatPct(asset.capmExpectedReturn) : "-"}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.historicalExpectedReturn)}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.blendedExpectedReturn)}</td>
                                      <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.capmWeight)}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                            <p className="mt-2 text-[11px] text-ink-4">
                              SML은 CAPM line 대비 종목 위치를 보는 보조 진단이며 예상 수익률 보장이나 추천 비중이 아닙니다.
                            </p>
                          </div>
                        </div>
                      ) : null}
                      <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                        <p className="text-ink">
                          CAPM 반영 비중은 공통 표본 수, 벤치마크 품질, 결측률, R², correlation, 변동성 안정성을 함께 반영해 계산합니다.
                        </p>
                        <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-x-4 gap-y-1.5">
                          <p><span className="font-semibold text-ink">공통 표본</span>: 종목과 벤치마크의 공통 수익률 표본 수가 충분할수록 커집니다.</p>
                          <p><span className="font-semibold text-ink">벤치마크 출처</span>: 저장 데이터 또는 외부 보강 데이터이면 높고, 저장 데이터가 부족하면 낮게 반영됩니다.</p>
                          <p><span className="font-semibold text-ink">데이터 커버리지</span>: 벤치마크 결측률이 낮을수록 커집니다.</p>
                          <p><span className="font-semibold text-ink">시장 설명력(R²)</span>: 시장수익률이 종목수익률을 설명하는 정도가 높을수록 커집니다.</p>
                          <p><span className="font-semibold text-ink">상관관계(correlation)</span>: 종목과 벤치마크 수익률의 동행성이 강할수록 커집니다.</p>
                          <p><span className="font-semibold text-ink">변동성 안정성</span>: 종목 변동성이 과도하게 높으면 CAPM 반영을 줄입니다.</p>
                          <p><span className="font-semibold text-ink">과거 흐름 비중</span>: CAPM 신뢰도가 낮을수록 과거 흐름 추정값 비중이 커집니다.</p>
                        </div>
                      </div>
                    </div>
                  );
                })()}

                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[640px] text-left text-xs">
                    <thead className="text-ink-4">
                      <tr>
                        <th className="py-2 pr-3">구성종목</th>
                        <th className="py-2 pr-3">가격 관측치</th>
                        <th className="py-2 pr-3">결측률</th>
                        <th className="py-2 pr-3">데이터 소스</th>
                        <th className="py-2 pr-3">캐시 상태</th>
                      </tr>
                    </thead>
                    <tbody>
                      {analysisResult.policy.dataQuality.priceSeries.map((series) => (
                        <tr key={series.stockCode} className="border-t border-line text-ink-3">
                          <td className="py-2 pr-3 font-medium text-ink">{series.companyName ?? series.stockCode}</td>
                          <td className="py-2 pr-3 font-mono tabular">{series.availablePriceCount}</td>
                          <td className="py-2 pr-3 font-mono tabular">{formatPct(series.missingRate)}</td>
                          <td className="py-2 pr-3">{marketDataSourceLabel[series.source ?? ""] ?? series.source}</td>
                          <td className="py-2 pr-3">{cacheStatusLabel[series.cacheStatus ?? ""] ?? series.cacheStatus}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {analysisResult.advanced?.frontier?.length ? (
                  <div className="mt-5">
                    <div className="flex items-center justify-between gap-3">
                      <h4 className="text-sm font-bold text-ink">효율적 프론티어</h4>
                      <p className="text-xs text-ink-4">
                        E[R]은 극단 수익률을 완화한 과거 흐름과 시장 기준 기대수익률을 신뢰도에 따라 가중한 연율 추정값입니다.
                      </p>
                    </div>
                    {(() => {
                      const frontier = analysisResult.advanced?.frontier ?? [];
                      const graphReturn = (point: { expectedReturn?: number | null; displayExpectedReturn?: number | null }) =>
                        point.displayExpectedReturn ?? point.expectedReturn ?? 0;
                      const markers = [
                        analysisResult.currentPortfolio,
                        ...analysisResult.basicPortfolios,
                        ...(analysisResult.advanced?.candidatePortfolios ?? []),
                      ].filter((portfolio) => portfolio.expectedReturn !== null && portfolio.expectedReturn !== undefined);
                      const keyMarkers = markers.filter((portfolio) =>
                        ["MIN_VOL", "MAX_SHARPE", "RISK_ALLOCATION", "UTILITY_OPTIMAL", "THEORETICAL_UTILITY"].includes(portfolio.type),
                      );
                      const utilityOptimal = markers.find((portfolio) => portfolio.type === "UTILITY_OPTIMAL") ?? null;
                      const theoreticalUtility = markers.find((portfolio) => portfolio.type === "THEORETICAL_UTILITY") ?? null;
                      const utilityCurveAnchor = theoreticalUtility ?? utilityOptimal;
                      const riskAversionGamma = analysisResult.policy.riskProfile.riskAversionGamma
                        ?? 10 - 9 * analysisResult.policy.riskProfile.riskToleranceScore;
                      const scaleVols = [...frontier.map((p) => p.volatility), ...keyMarkers.map((p) => p.volatility)];
                      const rawMinVol = Math.min(...scaleVols);
                      const rawMaxVol = Math.max(...scaleVols, 0.01);
                      const rawVolRange = Math.max(rawMaxVol - rawMinVol, 0.01);
                      const utilityValue = utilityCurveAnchor?.expectedReturn !== null && utilityCurveAnchor?.expectedReturn !== undefined
                        ? graphReturn(utilityCurveAnchor) - 0.5 * riskAversionGamma * utilityCurveAnchor.volatility * utilityCurveAnchor.volatility
                        : null;
                      const scaleReturns = [
                        ...frontier.map((p) => graphReturn(p)),
                        ...keyMarkers.map((p) => graphReturn(p)),
                      ];
                      const rawMinReturn = Math.min(...scaleReturns);
                      const rawMaxReturn = Math.max(...scaleReturns, rawMinReturn + 0.01);
                      const volPad = Math.max((rawMaxVol - rawMinVol) * 0.08, 0.003);
                      const returnPad = Math.max((rawMaxReturn - rawMinReturn) * 0.12, 0.006);
                      const minVol = Math.max(0, rawMinVol - volPad);
                      const maxVol = rawMaxVol + volPad;
                      const minReturn = rawMinReturn - returnPad;
                      const maxReturn = rawMaxReturn + returnPad;
                      const viewW = 900;
                      const viewH = 640;
                      const plotLeft = 78;
                      const plotRight = 850;
                      const plotTop = 62;
                      const plotBottom = 548;
                      const plotWidth = plotRight - plotLeft;
                      const plotHeight = plotBottom - plotTop;
                      const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
                      const x = (vol: number) => plotLeft + ((clamp(vol, minVol, maxVol) - minVol) / Math.max(maxVol - minVol, 0.0001)) * plotWidth;
                      const y = (ret: number) => plotBottom - ((clamp(ret, minReturn, maxReturn) - minReturn) / Math.max(maxReturn - minReturn, 0.0001)) * plotHeight;
                      const xRaw = (vol: number) => plotLeft + ((vol - minVol) / Math.max(maxVol - minVol, 0.0001)) * plotWidth;
                      const yRaw = (ret: number) => plotBottom - ((ret - minReturn) / Math.max(maxReturn - minReturn, 0.0001)) * plotHeight;
                      const toVol = (svgX: number) => minVol + ((clamp(svgX, plotLeft, plotRight) - plotLeft) / plotWidth) * (maxVol - minVol);
                      const toReturn = (svgY: number) => maxReturn - ((clamp(svgY, plotTop, plotBottom) - plotTop) / plotHeight) * (maxReturn - minReturn);
                      const distance = (ax: number, ay: number, bx: number, by: number) => Math.hypot(ax - bx, ay - by);
                      const maxSharpe = markers.find((portfolio) => portfolio.type === "MAX_SHARPE") ?? null;
                      const frontierWithTangentAnchor = maxSharpe?.expectedReturn !== null && maxSharpe?.expectedReturn !== undefined
                        ? [
                            ...frontier,
                            {
                              volatility: maxSharpe.volatility,
                              expectedReturn: maxSharpe.expectedReturn,
                              rawHistoricalReturn: maxSharpe.rawHistoricalReturn,
                              displayExpectedReturn: maxSharpe.displayExpectedReturn,
                              isDisplayCapped: maxSharpe.isDisplayCapped,
                              sharpeRatio: maxSharpe.sharpeRatio,
                            },
                          ]
                        : frontier;
                      const sortedFrontier = Array.from(
                        new Map(
                          [...frontierWithTangentAnchor]
                            .sort((a, b) => a.volatility - b.volatility || graphReturn(a) - graphReturn(b))
                            .map((point) => [`${point.volatility.toFixed(6)}:${graphReturn(point).toFixed(6)}`, point]),
                        ).values(),
                      );
                      const points = sortedFrontier.map((point) => ({
                        x: x(point.volatility),
                        y: y(graphReturn(point)),
                      }));
                      const buildSmoothPath = (curvePoints: Array<{ x: number; y: number }>) => curvePoints.length < 2
                        ? curvePoints.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ")
                        : curvePoints.reduce((acc, point, index) => {
                            if (index === 0) return `M ${point.x.toFixed(1)} ${point.y.toFixed(1)}`;
                            const prev = curvePoints[index - 1];
                            const dx = (point.x - prev.x) / 2;
                            return `${acc} C ${(prev.x + dx).toFixed(1)} ${prev.y.toFixed(1)}, ${(point.x - dx).toFixed(1)} ${point.y.toFixed(1)}, ${point.x.toFixed(1)} ${point.y.toFixed(1)}`;
                          }, "");
                      const buildLinearPath = (curvePoints: Array<{ x: number; y: number }>) =>
                        curvePoints.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
                      const path = buildSmoothPath(points);
                      const indifferenceCurveAnchorPoint = utilityCurveAnchor && utilityValue !== null
                        ? {
                            volatility: utilityCurveAnchor.volatility,
                            expectedReturn: graphReturn(utilityCurveAnchor),
                            x: x(utilityCurveAnchor.volatility),
                            y: y(graphReturn(utilityCurveAnchor)),
                          }
                        : null;
                      const indifferencePoints = utilityValue === null
                        ? []
                        : [
                            ...Array.from({ length: 120 }, (_, index) => {
                            const ratio = index / 119;
                            const localHalfWidth = Math.max(rawVolRange * 0.55, 0.025);
                            const localMinVol = Math.max(minVol, (utilityCurveAnchor?.volatility ?? rawMinVol) - localHalfWidth);
                            const localMaxVol = Math.min(maxVol, (utilityCurveAnchor?.volatility ?? rawMaxVol) + localHalfWidth);
                            const vol = localMinVol + (localMaxVol - localMinVol) * ratio;
                            const expectedReturn = utilityValue + 0.5 * riskAversionGamma * vol * vol;
                            return { volatility: vol, expectedReturn, x: x(vol), y: y(expectedReturn) };
                            }),
                            ...(indifferenceCurveAnchorPoint ? [indifferenceCurveAnchorPoint] : []),
                          ]
                            .filter((point) => point.expectedReturn >= minReturn && point.expectedReturn <= maxReturn)
                            .sort((a, b) => a.volatility - b.volatility);
                      const indifferencePath = buildLinearPath(indifferencePoints);
                      const riskFreeRate = analysisResult.policy.riskFreePolicy?.rate ?? 0;
                      const riskFreeX = xRaw(0);
                      const riskFreeY = yRaw(riskFreeRate);
                      const riskFreeGuideY = clamp(riskFreeY, plotTop + 18, plotBottom - 18);
                      const calEndPortfolio = theoreticalUtility ?? maxSharpe;
                      const calEndPoint = calEndPortfolio?.expectedReturn !== null && calEndPortfolio?.expectedReturn !== undefined
                        ? { x: xRaw(calEndPortfolio.volatility), y: yRaw(graphReturn(calEndPortfolio)) }
                        : null;
                      const maxSharpePoint = maxSharpe?.expectedReturn !== null && maxSharpe?.expectedReturn !== undefined
                        ? { x: xRaw(maxSharpe.volatility), y: yRaw(graphReturn(maxSharpe)) }
                        : null;
                      const riskFreeVisualPoint = calEndPoint
                        ? (() => {
                            const markerX = plotLeft + 16;
                            const slope = (calEndPoint.y - riskFreeY) / Math.max(calEndPoint.x - riskFreeX, 1e-6);
                            const markerY = riskFreeY + slope * (markerX - riskFreeX);
                            return {
                              x: markerX,
                              y: clamp(markerY, plotTop + 18, plotBottom - 18),
                            };
                          })()
                        : { x: plotLeft + 16, y: riskFreeGuideY };
                      const markerColor = (type: string) => {
                        if (type === "CURRENT") return "#64748b";
                        if (type === "MAX_SHARPE") return "#16a34a";
                        if (type === "MIN_VOL") return "#2563eb";
                        if (type === "RISK_ALLOCATION") return "#ea580c";
                        if (type === "UTILITY_OPTIMAL") return "#dc2626";
                        if (type === "THEORETICAL_UTILITY") return "#9333ea";
                        return "#f59e0b";
                      };
                      const fallbackLabelOffset = (type: string, index: number) => {
                        if (type === "CURRENT") return { dx: 12, dy: -16, anchor: "start" as const };
                        if (type === "MAX_SHARPE") return { dx: 12, dy: 16, anchor: "start" as const };
                        if (type === "RISK_ALLOCATION") return { dx: 12, dy: 2, anchor: "start" as const };
                        if (type === "UTILITY_OPTIMAL") return { dx: -12, dy: -18, anchor: "end" as const };
                        if (type === "THEORETICAL_UTILITY") return { dx: 12, dy: -18, anchor: "start" as const };
                        if (type === "MIN_VOL") return { dx: 12, dy: -8, anchor: "start" as const };
                        return { dx: 10, dy: index % 2 === 0 ? -10 : 16, anchor: "start" as const };
                      };
                      const visibleMarkers = Array.from(
                        new Map(
                          markers
                            .filter((portfolio) =>
                              ["CURRENT", "MIN_VOL", "MAX_SHARPE", "RISK_ALLOCATION", "UTILITY_OPTIMAL", "THEORETICAL_UTILITY"].includes(portfolio.type),
                            )
                            .map((portfolio) => [portfolio.type, portfolio]),
                        ).values(),
                      );
                      const labelSlots = [
                        { dx: 14, dy: -18, anchor: "start" as const },
                        { dx: 14, dy: 18, anchor: "start" as const },
                        { dx: -14, dy: -18, anchor: "end" as const },
                        { dx: -14, dy: 18, anchor: "end" as const },
                        { dx: 0, dy: -24, anchor: "middle" as const },
                        { dx: 0, dy: 28, anchor: "middle" as const },
                      ];
                      const markerScreenPoints = visibleMarkers.map((portfolio) => ({
                        type: portfolio.type,
                        x: x(portfolio.volatility),
                        y: y(graphReturn(portfolio)),
                      }));
                      const riskFreeScreenPoint = {
                        type: "RISK_FREE",
                        x: riskFreeVisualPoint.x,
                        y: riskFreeVisualPoint.y,
                      };
                      const tooltipAvoidancePoints = [...markerScreenPoints, riskFreeScreenPoint];
                      const markerLabelLayout = new Map<string, { dx: number; dy: number; anchor: "start" | "middle" | "end" }>();
                      markerScreenPoints.forEach((point, index) => {
	                        const nearbyPreviousCount = markerScreenPoints
	                          .slice(0, index)
	                          .filter((other) => distance(point.x, point.y, other.x, other.y) < 76)
	                          .length;
	                        const isClustered = markerScreenPoints.some((other, otherIndex) =>
	                          otherIndex !== index && distance(point.x, point.y, other.x, other.y) < 76,
                        );
                        markerLabelLayout.set(
                          point.type,
                          isClustered
                            ? labelSlots[(nearbyPreviousCount + index) % labelSlots.length]
                            : fallbackLabelOffset(point.type, index),
                        );
                      });
                      const hoverVol = frontierHover ? toVol(frontierHover.x) : null;
                      const hoverReturn = frontierHover ? toReturn(frontierHover.y) : null;
                      const nearestFrontier = frontierHover
                        ? sortedFrontier
                            .map((point) => ({
                              point,
                              x: x(point.volatility),
                              y: y(graphReturn(point)),
                              d: distance(frontierHover.x, frontierHover.y, x(point.volatility), y(graphReturn(point))),
                            }))
                            .sort((a, b) => a.d - b.d)[0] ?? null
                        : null;
                      const nearestMarker = frontierHover
                        ? visibleMarkers
                            .map((portfolio) => ({
                              portfolio,
                              x: x(portfolio.volatility),
                              y: y(graphReturn(portfolio)),
                              d: distance(frontierHover.x, frontierHover.y, x(portfolio.volatility), y(graphReturn(portfolio))),
                            }))
                            .sort((a, b) => a.d - b.d)[0] ?? null
                        : null;
                      const activeMarker = nearestMarker && nearestMarker.d <= 28 ? nearestMarker : null;
                      const activeRiskFree = frontierHover && distance(
                        frontierHover.x,
                        frontierHover.y,
                        riskFreeVisualPoint.x,
                        riskFreeVisualPoint.y,
                      ) <= 28;
                      const activeFrontier = nearestFrontier && nearestFrontier.d <= 34 ? nearestFrontier : null;
                      const tooltipW = 218;
                      const tooltipH = activeMarker || activeRiskFree ? 84 : 64;
                      const hoverX = frontierHover ? clamp(frontierHover.x, plotLeft, plotRight) : 0;
                      const hoverY = frontierHover ? clamp(frontierHover.y, plotTop, plotBottom) : 0;
                      const tooltipAnchorX = activeRiskFree ? riskFreeVisualPoint.x : activeMarker?.x ?? activeFrontier?.x ?? hoverX;
                      const tooltipAnchorY = activeRiskFree ? riskFreeVisualPoint.y : activeMarker?.y ?? activeFrontier?.y ?? hoverY;
                      const clampTooltip = (left: number, top: number) => ({
                        x: Math.min(viewW - tooltipW - 18, Math.max(plotLeft + 12, left)),
                        y: Math.min(plotBottom - tooltipH - 10, Math.max(plotTop + 12, top)),
                      });
                      const pointToRectDistance = (
                        point: { x: number; y: number },
                        rect: { x: number; y: number },
                      ) => {
                        const dx = Math.max(rect.x - point.x, 0, point.x - (rect.x + tooltipW));
                        const dy = Math.max(rect.y - point.y, 0, point.y - (rect.y + tooltipH));
                        return Math.hypot(dx, dy);
                      };
                      const tooltipCandidates = [
                        clampTooltip(tooltipAnchorX + 34, tooltipAnchorY - tooltipH - 30),
                        clampTooltip(tooltipAnchorX - tooltipW - 34, tooltipAnchorY - tooltipH - 30),
                        clampTooltip(tooltipAnchorX + 34, tooltipAnchorY + 30),
                        clampTooltip(tooltipAnchorX - tooltipW - 34, tooltipAnchorY + 30),
                        clampTooltip(tooltipAnchorX - tooltipW / 2, tooltipAnchorY - tooltipH - 44),
                        clampTooltip(tooltipAnchorX - tooltipW / 2, tooltipAnchorY + 44),
                      ];
                      const bestTooltip = tooltipCandidates
                        .map((rect) => ({
                          rect,
                          score: Math.min(...tooltipAvoidancePoints.map((point) => pointToRectDistance(point, rect))),
                        }))
                        .sort((a, b) => b.score - a.score)[0]?.rect ?? { x: 0, y: 0 };
                      const tooltipX = frontierHover ? bestTooltip.x : 0;
                      const tooltipY = frontierHover ? bestTooltip.y : 0;
                      const guideMarkers = visibleMarkers.filter((portfolio) =>
                        ["MIN_VOL", "RISK_ALLOCATION", "UTILITY_OPTIMAL"].includes(portfolio.type),
                      );
                      const yAxisBadgeCenters = guideMarkers
                        .map((portfolio) => ({
                          type: portfolio.type,
                          y: y(graphReturn(portfolio)),
                        }))
                        .sort((a, b) => a.y - b.y);
                      const minYAxisBadgeGap = 28;
                      const yAxisBadgeMin = plotTop + 14;
                      const yAxisBadgeMax = plotBottom - 14;
                      yAxisBadgeCenters.forEach((badge, index) => {
                        badge.y = clamp(badge.y, yAxisBadgeMin, yAxisBadgeMax);
                        if (index > 0) {
                          badge.y = Math.max(badge.y, yAxisBadgeCenters[index - 1].y + minYAxisBadgeGap);
                        }
                      });
                      for (let index = yAxisBadgeCenters.length - 1; index >= 0; index -= 1) {
                        if (index === yAxisBadgeCenters.length - 1) {
                          yAxisBadgeCenters[index].y = Math.min(yAxisBadgeCenters[index].y, yAxisBadgeMax);
                        } else {
                          yAxisBadgeCenters[index].y = Math.min(
                            yAxisBadgeCenters[index].y,
                            yAxisBadgeCenters[index + 1].y - minYAxisBadgeGap,
                          );
                        }
                      }
                      const yAxisBadgeLayout = new Map(
                        yAxisBadgeCenters.map((badge) => [
                          badge.type,
                          clamp(badge.y, yAxisBadgeMin, yAxisBadgeMax),
                        ]),
                      );
                      const xAxisBadgeCenters = guideMarkers
                        .map((portfolio) => ({
                          type: portfolio.type,
                          x: x(portfolio.volatility),
                        }))
                        .sort((a, b) => a.x - b.x);
                      const minXAxisBadgeGap = 88;
                      const xAxisBadgeMin = plotLeft + 39;
                      const xAxisBadgeMax = plotRight - 39;
                      xAxisBadgeCenters.forEach((badge, index) => {
                        badge.x = clamp(badge.x, xAxisBadgeMin, xAxisBadgeMax);
                        if (index > 0) {
                          badge.x = Math.max(badge.x, xAxisBadgeCenters[index - 1].x + minXAxisBadgeGap);
                        }
                      });
                      for (let index = xAxisBadgeCenters.length - 1; index >= 0; index -= 1) {
                        if (index === xAxisBadgeCenters.length - 1) {
                          xAxisBadgeCenters[index].x = Math.min(xAxisBadgeCenters[index].x, xAxisBadgeMax);
                        } else {
                          xAxisBadgeCenters[index].x = Math.min(
                            xAxisBadgeCenters[index].x,
                            xAxisBadgeCenters[index + 1].x - minXAxisBadgeGap,
                          );
                        }
                      }
                      const xAxisBadgeLayout = new Map(
                        xAxisBadgeCenters.map((badge) => [
                          badge.type,
                          clamp(badge.x, xAxisBadgeMin, xAxisBadgeMax),
                        ]),
                      );
                      const markerMetricCards = visibleMarkers.filter((portfolio) => portfolio.type !== "CURRENT");
                      const advancedPortfolioLabel = (type: string, label?: string) => {
                        if (type === "CURRENT") return "현재 포트폴리오";
                        if (type === "MIN_VOL") return "최소분산 포트폴리오";
                        if (type === "MAX_SHARPE") return "최대샤프 포트폴리오";
                        if (type === "RISK_ALLOCATION") return "CAL 기반 위험배분";
                        if (type === "UTILITY_OPTIMAL") return "효용최대 포트폴리오";
                        if (type === "THEORETICAL_UTILITY") return "이론적 효용접점";
                        return label ?? type;
                      };
                      const advancedMarkerLabel = (type: string) => {
                        if (type === "CURRENT") return "현재";
                        if (type === "MIN_VOL") return "최소분산";
                        if (type === "MAX_SHARPE") return "최대샤프";
                        if (type === "RISK_ALLOCATION") return "위험배분";
                        if (type === "UTILITY_OPTIMAL") return "효용최대";
                        if (type === "THEORETICAL_UTILITY") return "이론접점";
                        return type;
                      };
                      const portfolioPieCards = [
                        analysisResult.currentPortfolio,
                        ...["MIN_VOL", "MAX_SHARPE", "RISK_ALLOCATION", "UTILITY_OPTIMAL"]
                          .map((type) => markers.find((portfolio) => portfolio.type === type))
                          .filter((portfolio): portfolio is NonNullable<typeof portfolio> => Boolean(portfolio)),
                      ];
                      const handleFrontierHover = (event: MouseEvent<SVGSVGElement>) => {
                        const svg = event.currentTarget;
                        const matrix = svg.getScreenCTM();
                        if (!matrix) return;
                        const point = svg.createSVGPoint();
                        point.x = event.clientX;
                        point.y = event.clientY;
                        const svgPoint = point.matrixTransform(matrix.inverse());
                        setFrontierHover({ x: svgPoint.x, y: svgPoint.y });
                      };
                      return (
                        <div className="mt-3 rounded-xl bg-bg-sunk border border-line p-3">
                          <svg
                            viewBox={`0 0 ${viewW} ${viewH}`}
                            className="w-full h-[640px] cursor-crosshair"
                            role="img"
                            aria-label="효율적 프론티어, 자본배분선, 투자자 무차별곡선"
                            onMouseMove={handleFrontierHover}
                            onMouseLeave={() => setFrontierHover(null)}
                          >
                            <line x1={plotLeft} y1={plotBottom} x2={plotRight} y2={plotBottom} stroke="currentColor" className="text-line" />
                            <line x1={plotLeft} y1={plotBottom} x2={plotLeft} y2={plotTop} stroke="currentColor" className="text-line" />
                            <text x={plotRight} y={viewH - 24} textAnchor="end" className="fill-ink-4 text-[12px]">변동성 σ</text>
                            <text x={16} y={plotTop - 18} className="fill-ink-4 text-[12px]">기대수익률 E[R]</text>
                            <text x={plotLeft} y={plotBottom + 26} className="fill-ink-4 text-[11px]">{formatPct(minVol)}</text>
                            <text x={plotRight} y={plotBottom + 26} textAnchor="end" className="fill-ink-4 text-[11px]">{formatPct(maxVol)}</text>
                            <text x={plotLeft - 12} y={y(minReturn)} textAnchor="end" className="fill-ink-4 text-[11px]">{formatPct(minReturn)}</text>
                            <text x={plotLeft - 12} y={y(maxReturn)} textAnchor="end" className="fill-ink-4 text-[11px]">{formatPct(maxReturn)}</text>
                            <g>
                              <circle
                                cx={riskFreeVisualPoint.x}
                                cy={riskFreeVisualPoint.y}
                                r={activeRiskFree ? "8" : "6"}
                                fill="#94a3b8"
                                stroke="white"
                                strokeWidth={activeRiskFree ? "3" : "2"}
                              />
                              <text
                                x={riskFreeVisualPoint.x + 10}
                                y={riskFreeVisualPoint.y - 8}
                                fill="#475569"
                                className={activeRiskFree ? "text-[12px] font-bold" : "text-[9px] font-semibold"}
                              >
                                RF
                              </text>
                              <text
                                x={riskFreeVisualPoint.x + 10}
                                y={riskFreeVisualPoint.y + 6}
                                fill="#475569"
                                className={activeRiskFree ? "text-[10px] font-mono font-bold" : "text-[9px] font-mono"}
                              >
                                {formatPct(riskFreeRate)}
                              </text>
                            </g>
                            {indifferencePath && (
                              <path
                                d={indifferencePath}
                                fill="none"
                                stroke="#7c3aed"
                                strokeWidth="2.4"
                                strokeLinecap="round"
                                strokeDasharray="10 5"
                                opacity="0.9"
                              />
                            )}
                            <path d={path} fill="none" stroke="#2563eb" strokeWidth="3" strokeLinecap="round" />
                            {calEndPoint && (
                              <line
                                x1={riskFreeVisualPoint.x}
                                y1={riskFreeVisualPoint.y}
                                x2={calEndPoint.x}
                                y2={calEndPoint.y}
                                stroke="#16a34a"
                                strokeWidth="2.4"
                                strokeDasharray="7 5"
                                strokeLinecap="round"
                                opacity="0.9"
                              />
                            )}
                            {theoreticalUtility && maxSharpePoint && calEndPoint && (
                              <line
                                x1={maxSharpePoint.x}
                                y1={maxSharpePoint.y}
                                x2={calEndPoint.x}
                                y2={calEndPoint.y}
                                stroke="#16a34a"
                                strokeWidth="3"
                                strokeDasharray="7 5"
                                strokeLinecap="round"
                                opacity="0.98"
                                pointerEvents="none"
                              />
                            )}
                            {maxSharpePoint && (
                              <g pointerEvents="none">
                                <circle cx={maxSharpePoint.x} cy={maxSharpePoint.y} r="15" fill="#16a34a" opacity="0.10" />
                                <circle cx={maxSharpePoint.x} cy={maxSharpePoint.y} r="9" fill="none" stroke="#16a34a" strokeWidth="1.5" strokeDasharray="3 3" opacity="0.75" />
                              </g>
                            )}
                            {sortedFrontier.map((point, index) => (
                              <circle
                                key={`${point.volatility}-${point.expectedReturn}-${index}`}
                                cx={x(point.volatility)}
                                cy={y(graphReturn(point))}
                                r={activeFrontier?.point === point ? "5" : "3"}
                                fill="#2563eb"
                                opacity={activeFrontier?.point === point ? "1" : "0.65"}
                              />
                            ))}
                            {guideMarkers.map((portfolio) => {
                              const markerX = x(portfolio.volatility);
                              const markerY = y(graphReturn(portfolio));
                              const xAxisBadgeX = xAxisBadgeLayout.get(portfolio.type) ?? markerX;
                              const yAxisBadgeY = yAxisBadgeLayout.get(portfolio.type) ?? markerY;
                              const color = markerColor(portfolio.type);
                              return (
                                <g key={`guide-${portfolio.type}`} pointerEvents="none">
                                  <line
                                    x1={markerX}
                                    y1={markerY}
                                    x2={markerX}
                                    y2={plotBottom}
                                    stroke={color}
                                    strokeWidth="1.4"
                                    strokeDasharray="5 5"
                                    opacity="0.72"
                                  />
                                  <line
                                    x1={plotLeft}
                                    y1={markerY}
                                    x2={markerX}
                                    y2={markerY}
                                    stroke={color}
                                    strokeWidth="1.4"
                                    strokeDasharray="5 5"
                                    opacity="0.72"
                                  />
                                  {Math.abs(xAxisBadgeX - markerX) > 1 && (
                                    <line
                                      x1={markerX}
                                      y1={plotBottom + 30}
                                      x2={xAxisBadgeX}
                                      y2={plotBottom + 30}
                                      stroke={color}
                                      strokeWidth="1"
                                      strokeDasharray="2 3"
                                      opacity="0.58"
                                    />
                                  )}
                                  <rect x={xAxisBadgeX - 39} y={plotBottom + 34} width="78" height="22" rx="5" fill={color} opacity="0.95" />
                                  <text x={xAxisBadgeX} y={plotBottom + 49} textAnchor="middle" className="fill-white text-[10px] font-mono">
                                    {formatPct(portfolio.volatility)}
                                  </text>
                                  {Math.abs(yAxisBadgeY - markerY) > 1 && (
                                    <line
                                      x1={plotLeft - 12}
                                      y1={markerY}
                                      x2={plotLeft - 12}
                                      y2={yAxisBadgeY}
                                      stroke={color}
                                      strokeWidth="1"
                                      strokeDasharray="2 3"
                                      opacity="0.58"
                                    />
                                  )}
                                  <rect x={plotLeft - 74} y={yAxisBadgeY - 11} width="62" height="22" rx="5" fill={color} opacity="0.95" />
                                  <text x={plotLeft - 43} y={yAxisBadgeY + 4} textAnchor="middle" className="fill-white text-[10px] font-mono">
                                    {formatPct(graphReturn(portfolio))}
                                  </text>
                                </g>
                              );
                            })}
                            {visibleMarkers.map((portfolio, index) => {
                              const offset = markerLabelLayout.get(portfolio.type) ?? fallbackLabelOffset(portfolio.type, index);
                              return (
                              <g key={`marker-${portfolio.type}`}>
                                <circle
                                  cx={x(portfolio.volatility)}
                                  cy={y(graphReturn(portfolio))}
                                  r={activeMarker?.portfolio === portfolio ? 8 : ["RISK_ALLOCATION", "UTILITY_OPTIMAL"].includes(portfolio.type) ? 7 : 5}
                                  fill={markerColor(portfolio.type)}
                                  stroke="white"
                                  strokeWidth={activeMarker?.portfolio === portfolio ? "3" : "2"}
                                />
                                <text
                                  x={x(portfolio.volatility) + offset.dx}
                                  y={y(graphReturn(portfolio)) + offset.dy}
                                  textAnchor={offset.anchor}
                                  className={activeMarker?.portfolio === portfolio ? "fill-ink text-[12px] font-bold" : "fill-ink text-[10px] font-semibold"}
                                >
                                  {advancedMarkerLabel(portfolio.type)}
                                </text>
                              </g>
                              );
                            })}
                            {frontierHover && hoverVol !== null && hoverReturn !== null && (
                              <g pointerEvents="none">
                                <line x1={hoverX} y1={plotTop} x2={hoverX} y2={plotBottom} stroke="#cbd5e1" strokeDasharray="3 3" opacity="0.75" />
                                <line x1={plotLeft} y1={hoverY} x2={plotRight} y2={hoverY} stroke="#cbd5e1" strokeDasharray="3 3" opacity="0.75" />
                                <rect x={hoverX - 38} y={plotBottom + 8} width="76" height="22" rx="5" fill="#111827" opacity="0.92" />
                                <text x={hoverX} y={plotBottom + 23} textAnchor="middle" className="fill-white text-[11px] font-mono">
                                  {formatPct(hoverVol)}
                                </text>
                                <rect x={plotLeft - 72} y={hoverY - 11} width="60" height="22" rx="5" fill="#111827" opacity="0.92" />
                                <text x={plotLeft - 42} y={hoverY + 4} textAnchor="middle" className="fill-white text-[11px] font-mono">
                                  {formatPct(hoverReturn)}
                                </text>
                                <rect x={tooltipX} y={tooltipY} width={tooltipW} height={tooltipH} rx="8" fill="#111827" stroke="#334155" opacity="0.96" />
                                <text x={tooltipX + 12} y={tooltipY + 19} className="fill-white text-[12px] font-semibold">
                                  {activeRiskFree ? "국채 무위험수익률" : activeMarker ? advancedPortfolioLabel(activeMarker.portfolio.type, activeMarker.portfolio.label) : "좌표"}
                                </text>
                                <text x={tooltipX + 12} y={tooltipY + 38} className="fill-slate-300 text-[11px] font-mono">
                                  {activeRiskFree ? `σ ${formatPct(0)} · r_f ${formatPct(riskFreeRate)}` : `σ ${formatPct(hoverVol)} · E[R] ${formatPct(hoverReturn)}`}
                                </text>
                                <text x={tooltipX + 12} y={tooltipY + 57} className="fill-sky-300 text-[11px] font-mono">
                                  {activeRiskFree ? "시각화상 CAL 축외 끝에 표시" : `EF ${activeFrontier ? `${formatPct(activeFrontier.point.volatility)} / ${formatPct(graphReturn(activeFrontier.point))}` : "-"}`}
                                </text>
                                {activeRiskFree && (
                                  <text x={tooltipX + 12} y={tooltipY + 76} className="fill-slate-300 text-[11px]">
                                    실제 점은 변동성 0% 위치입니다.
                                  </text>
                                )}
                                {activeMarker && !activeRiskFree && (
                                  <text x={tooltipX + 12} y={tooltipY + 76} className="fill-emerald-300 text-[11px] font-semibold">
                                    {advancedMarkerLabel(activeMarker.portfolio.type)} · Sharpe {activeMarker.portfolio.sharpeRatio?.toFixed(2) ?? "-"}
                                  </text>
                                )}
                              </g>
                            )}
                          </svg>
	                          <div className="mt-2 flex flex-wrap gap-3 text-[11px] text-ink-3">
	                            <span>파란선 효율적 프론티어</span>
	                            <span>회색점 무위험자산 · σ 0% 축외</span>
	                            <span className="text-success">녹색 점선 자본배분선(CAL)</span>
                            <span className="text-purple-600">보라 점선 투자자 무차별곡선</span>
                            <span className="text-success">최대샤프 포트폴리오</span>
                            <span className="text-orange-600">CAL 기반 위험배분</span>
                            <span className="text-danger">효용최대 포트폴리오</span>
                            <span className="text-purple-600">이론적 효용접점</span>
                            <span className="text-accent">최소분산 포트폴리오</span>
                            <span>회색 현재 포트폴리오</span>
                          </div>
                          <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                            그래프의 E[R]은 시각화 안정성을 위해 -30%~60% 범위로 표시됩니다. 계산에는 cap 이전 기대수익률이 사용됩니다.
                          </div>
	                          <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
	                            <p className="font-semibold text-ink">마커 산출 기준</p>
	                            <div className="mt-1.5 grid grid-cols-1 lg:grid-cols-2 gap-x-4 gap-y-2">
	                              <p>
	                                <span className="font-semibold text-ink">효율적 프론티어</span>는 현금을 제외한 위험자산 100% 조합에서 목표 기대수익률별 최소분산 포트폴리오를 구한 뒤, 지배되는 점을 제거한 상단 경계입니다. 같은 σ에서 더 높은 E[R]을 제공하는 조합만 남습니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-success">최대샤프 포트폴리오</span>는 효율적 프론티어 위에서 (E[R] - r_f) / σ가 가장 큰 위험자산 조합입니다. 무위험자산과 이 점을 잇는 직선이 자본배분선(CAL)의 기준선입니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-orange-600">CAL 기반 위험배분</span>은 최대샤프 위험자산 조합에 무위험자산을 섞고, 위험회피계수 γ와 현금 한도에 따라 위험자산 비중을 조절한 배분입니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-danger">효용최대 포트폴리오</span>는 U = E[R] - 0.5 · γ · σ²를 최대화하는 배분입니다. 현재 현금 한도, 레버리지 금지, 종목별 비중 제한을 적용하므로 이론 접점이 제약 밖이면 가능한 경계점에 위치합니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-purple-600">이론적 효용접점</span>은 제약을 풀고 CAL과 투자자 무차별곡선이 접하는 지점입니다. 위험자산 배수 y* = (E[R]_maxSharpe - r_f) / (γ · σ_maxSharpe²)로 계산하며, y*가 현재 제약 밖이면 별도 마커와 목표 카드로 표시합니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-accent">최소분산 포트폴리오</span>는 위험자산만 100% 보유한다고 가정했을 때 wᵀΣw가 가장 작은 조합입니다. 기대수익률보다 공분산 행렬의 구조가 핵심입니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-purple-600">투자자 무차별곡선</span>은 동일한 효용 U를 갖는 σ-E[R] 조합입니다. 내부해에서는 CAL과 접하고, 현금 한도나 위험자산 100% 상한에 걸리면 효용최대 포트폴리오에서는 교차처럼 보일 수 있습니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-ink">현재 포트폴리오</span>는 사용자가 입력한 수량, 평균단가, 보유 현금으로 계산한 현재 구성입니다. 평단 대비 손익은 표시용이며, 최적화 계산에는 가격 시계열의 수익률/공분산이 사용됩니다.
	                              </p>
	                              <p>
	                                <span className="font-semibold text-success">자본배분선(CAL)</span>은 무위험수익률 r_f에서 최대샤프 포트폴리오 방향으로 확장되는 선입니다. 이론적 효용접점이 있으면 최대샤프 이후 구간까지 점선으로 연장해 표시합니다.
	                              </p>
	                            </div>
	                          </div>
                          {utilityValue !== null && (
                            <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                              투자자 무차별곡선은 실제 위험회피계수 γ {riskAversionGamma.toFixed(2)} 기준으로 효용최대 포트폴리오를 지나도록 렌더링됩니다.
                              <span className="ml-2 font-mono tabular text-ink">U={utilityValue.toFixed(4)}</span>
                            </div>
                          )}
	                          <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-2">
	                            {markerMetricCards.map((portfolio) => (
                              <div key={`marker-card-${portfolio.type}`} className="rounded-lg bg-surface border border-line px-3 py-2">
                                <div className="flex items-center gap-2">
                                  <span
                                    className="w-2.5 h-2.5 rounded-full"
                                    style={{ backgroundColor: markerColor(portfolio.type) }}
                                  />
                                  <span className="text-xs font-bold text-ink">{advancedPortfolioLabel(portfolio.type, portfolio.label)}</span>
                                </div>
                                <div className="mt-2 grid grid-cols-3 gap-2 text-[10px] text-ink-4">
                                  <div>
                                    <p>σ</p>
                                    <p className="mt-0.5 font-mono tabular text-ink">{formatPct(portfolio.volatility)}</p>
                                  </div>
                                  <div>
                                    <p>E[R]</p>
                                    <p className="mt-0.5 font-mono tabular text-ink">{formatPct(portfolio.expectedReturn ?? 0)}</p>
                                  </div>
                                  <div>
                                    <p>Sharpe Ratio</p>
                                    <p className="mt-0.5 font-mono tabular text-ink">{portfolio.sharpeRatio?.toFixed(2) ?? "-"}</p>
                                  </div>
                                </div>
	                              </div>
	                            ))}
	                          </div>
                          <div className="mt-4">
                            <h5 className="text-sm font-bold text-ink">효율성 기반 분석</h5>
                            <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                              {portfolioPieCards.map((portfolio) => (
                                <div key={`advanced-pie-${portfolio.type}`} className="rounded-lg bg-surface border border-line p-3">
                                  <div className="flex items-center justify-between gap-2">
                                    <p className="text-xs font-bold text-ink">{advancedPortfolioLabel(portfolio.type, portfolio.label)}</p>
                                    <span className="text-[10px] font-mono tabular text-ink-4">
                                      Sharpe {portfolio.sharpeRatio?.toFixed(2) ?? "-"}
                                    </span>
                                  </div>
                                  <div className="mt-3 flex items-center gap-3">
                                    <div
                                      className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                      style={portfolioPieStyle(portfolio.weights)}
                                      aria-label={`${advancedPortfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                                    />
                                    <div className="min-w-0 flex-1">
                                      <div className="grid grid-cols-2 gap-2 text-[10px] text-ink-4">
                                        <div>
                                          <p>σ</p>
                                          <p className="mt-0.5 font-mono tabular text-ink">{formatPct(portfolio.volatility)}</p>
                                        </div>
                                        <div>
                                          <p>E[R]</p>
                                          <p className="mt-0.5 font-mono tabular text-ink">{formatPct(portfolio.expectedReturn ?? 0)}</p>
                                        </div>
                                      </div>
                                      <div className="mt-2 flex flex-col gap-1">
                                        {portfolio.weights.map((weight, index) => (
                                          <div key={`${portfolio.type}-${weight.stockCode}`} className="text-[10px]">
                                            <div className="flex items-center gap-1.5">
                                              <span
                                                className="w-2 h-2 rounded-full flex-shrink-0"
                                                style={{ backgroundColor: weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index) }}
                                              />
                                              <span className="min-w-0 flex-1 truncate text-ink-3">
                                                {weight.companyName ?? weight.stockCode}
                                              </span>
                                              <span className="font-mono tabular text-ink">
                                                {formatPct(weight.weight)}
                                              </span>
                                            </div>
                                            {portfolio.type === "CURRENT" && weight.assetType !== "CASH" && (
                                              <div className="mt-0.5 ml-3.5 flex items-center justify-between gap-2 font-mono tabular">
                                                <span className="truncate text-ink-4">평단 기준 현재 수익률</span>
                                                <span className={weight.unrealizedPnl !== undefined && weight.unrealizedPnl !== null && weight.unrealizedPnl < 0 ? "text-danger" : "text-success"}>
                                                  {formatPct(weight.unrealizedReturnRate)}
                                                </span>
                                              </div>
                                            )}
                                          </div>
                                        ))}
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              ))}
                            </div>
                            {theoreticalUtility && (
                              <div className="mt-3 rounded-lg bg-surface border border-purple-300 p-3">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                  <div>
                                    <p className="text-xs font-bold text-ink">이론적 효용접점</p>
                                    <p className="mt-1 text-[11px] leading-relaxed text-ink-4">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX"
                                        ? "위험회피계수 γ 기준의 무제약 접점은 현재 현금 한도보다 더 높은 현금 비중을 요구합니다. 현금 한도를 완화하면 이 접점을 목표로 삼을 수 있습니다."
                                        : "위험회피계수 γ 기준의 무제약 접점은 현재 총자산보다 큰 위험자산 금액을 요구합니다. 추가입금을 가정하면 이 접점을 목표로 삼을 수 있습니다."}
                                    </p>
                                  </div>
                                  <div className="text-right">
                                    <p className="text-[10px] text-ink-4">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX" ? "현금 한도 완화 필요" : "추가 필요액"}
                                    </p>
                                    <p className="font-mono tabular text-sm font-bold text-danger">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX"
                                        ? `현금 ${formatPct(1 - (theoreticalUtility.theoreticalRiskyAllocation ?? 1))}`
                                        : formatKRW(theoreticalUtility.additionalRequiredCash)}
                                    </p>
                                  </div>
                                </div>
                                <div className="mt-3 flex items-center gap-3">
                                  <div
                                    className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                    style={portfolioPieStyle(theoreticalUtility.weights)}
                                    aria-label="이론적 효용접점 구성비 파이차트"
                                  />
                                  <div className="min-w-0 flex-1">
                                    <div className="grid grid-cols-3 gap-2 text-[10px] text-ink-4">
                                      <div>
                                        <p>σ</p>
                                        <p className="mt-0.5 font-mono tabular text-ink">{formatPct(theoreticalUtility.volatility)}</p>
                                      </div>
                                      <div>
                                        <p>E[R]</p>
                                        <p className="mt-0.5 font-mono tabular text-ink">{formatPct(theoreticalUtility.expectedReturn ?? 0)}</p>
                                      </div>
                                      <div>
                                        <p>위험자산 배수</p>
                                        <p className="mt-0.5 font-mono tabular text-ink">{theoreticalUtility.theoreticalRiskyAllocation?.toFixed(2) ?? "-"}x</p>
                                      </div>
                                    </div>
                                    <div className="mt-2 flex flex-col gap-1">
                                      {theoreticalUtility.weights.map((weight, index) => (
                                        <div key={`advanced-theoretical-${weight.stockCode}`} className="flex items-center gap-1.5 text-[10px]">
                                          <span
                                            className="w-2 h-2 rounded-full flex-shrink-0"
                                            style={{ backgroundColor: weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index) }}
                                          />
                                          <span className="min-w-0 flex-1 truncate text-ink-3">
                                            {weight.companyName ?? weight.stockCode}
                                          </span>
                                          <span className="font-mono tabular text-ink">{formatPct(weight.weight)}</span>
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })()}
                  </div>
                ) : null}
              </div>}
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
