// src/pages/PortfolioMockPage.tsx

import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { createPortal } from "react-dom";
import type { MouseEvent, ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Trash2, Plus, Info, ClipboardList, Sun, Moon, Lock, Save, Download, Maximize2 } from "lucide-react";
import InvestLevelBadge from "../components/InvestLevelBadge";
import { isLoggedIn } from "../utils/auth";
import { useTheme } from "../hooks/useTheme";
import StockSearchCell from "../components/StockSearchCell";
import TokenBalanceBadge from "../components/TokenBalanceBadge";
import { refreshTokenBalance } from "../api/billingStore";
import { useDictionary } from "../components/DictContext";
import DictionaryText from "../components/DictionaryText";
import { getMyRiskProfile } from "../api/user";
import { fetchMyDefaultPortfolio, fetchPortfolioAnalysis, previewFeature3OverlayCache, replaceMyDefaultPortfolio } from "../api/portfolio";
import { fetchFeature2MacroRates } from "../api/feature2";
import { getApiErrorMessage } from "../utils/errorMessage";
import type { Feature2ExchangeRatePoint } from "../types/feature2";
import {
  SURVEY_RESULT_STORAGE_KEY,
  autoCashLimitForRiskScore,
  clampCashLimit,
  clampRiskGamma,
  formatRiskGamma,
  readStoredFeature3CashLimit,
  readStoredFeature3CashLimitManual,
  readStoredFeature3RiskGamma,
  storeFeature3CashLimit,
  storeFeature3RiskGamma,
  syncFeature3RiskDefaults,
} from "../utils/riskProfile";
import qaimaLogo from "../assets/qaima-final.png";
import { clientLog } from "../utils/clientLog";
import { downloadElementAsPdf, waitForPdfCaptureReady } from "../utils/reportPdf";
import ReportHeader from "../components/ReportHeader";
import useReportUserName from "../hooks/useReportUserName";

type AnalysisWindowPreset = {
  label: string;
  lookbackTradingDays: number;
  fetchCalendarDays: number;
};

const ANALYSIS_WINDOWS: AnalysisWindowPreset[] = [
  { label: "6M", lookbackTradingDays: 126, fetchCalendarDays: 190 },
  { label: "1Y", lookbackTradingDays: 252, fetchCalendarDays: 370 },
  { label: "2Y", lookbackTradingDays: 504, fetchCalendarDays: 740 },
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

const capmWeightReasons = (asset: Feature3CapmAsset, t: TFunction<"portfolioPage">): string[] => {
  const warnings = new Set(asset.warnings ?? []);
  const reasons: string[] = [];
  if (warnings.has("CAPM_LOW_R_SQUARED") || (asset.rSquared !== null && asset.rSquared !== undefined && asset.rSquared < 0.1)) {
    reasons.push(t("capmWeightReason.lowRSquared"));
  }
  if (asset.correlation !== null && asset.correlation !== undefined && Math.abs(asset.correlation) < 0.3) {
    reasons.push(t("capmWeightReason.lowCorrelation"));
  }
  if (warnings.has("CAPM_COMMON_SAMPLE_INSUFFICIENT") || warnings.has("CAPM_PARTIAL_LOW_COMMON_SAMPLE") || (asset.commonSampleSize ?? 0) < 120) {
    reasons.push(t("capmWeightReason.insufficientSample"));
  }
  if (warnings.has("CAPM_BETA_UNAVAILABLE")) {
    reasons.push(t("capmWeightReason.betaUnavailable"));
  }
  if (
    warnings.has("CAPM_HIGH_VOLATILITY")
    || (asset.volatilityReliabilityFactor !== null && asset.volatilityReliabilityFactor !== undefined && asset.volatilityReliabilityFactor < 0.75)
    || (asset.annualVolatility !== null && asset.annualVolatility !== undefined && asset.annualVolatility > 0.4)
  ) {
    reasons.push(t("capmWeightReason.highVolatility"));
  }
  if ((asset.capmWeight ?? 0) < 0.3 && reasons.length === 0) {
    reasons.push(t("capmWeightReason.lowConfidence"));
  }
  return reasons.slice(0, 3);
};

const capmWeightReasonText = (asset: Feature3CapmAsset, t: TFunction<"portfolioPage">): string => {
  const reasons = capmWeightReasons(asset, t);
  if (!reasons.length) return t("capmWeightReason.goodQuality");
  return t("capmWeightReason.limited", { reasons: reasons.join(" · ") });
};


type OverlayCachePolicy = "REUSE_AVAILABLE" | "FORCE_REFRESH";
type OverlayCachePolicyMap = Record<string, OverlayCachePolicy>;

const overlayPreviewMessage = (message?: string | null): string => {
  if (!message) return "";
  return message;
};

type OverlayValueItem = {
  label: string;
  value: string;
  tone?: "default" | "good" | "warn" | "muted";
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

const displayMetricValue = (key: string, raw: string | undefined, t: TFunction<"portfolioPage">): string => {
  if (!raw) return "-";
  if (key === "alignment") return raw === "bullish" ? t("metric.alignment.bullish") : raw === "bearish" ? t("metric.alignment.bearish") : raw;
  if (key === "zone") {
    if (raw === "overbought") return t("metric.zone.overbought");
    if (raw === "oversold") return t("metric.zone.oversold");
    return t("metric.zone.neutral");
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

const renderMetricOverlayValue = (value: string, keys: string[], t: TFunction<"portfolioPage">, caption?: string): ReactNode => {
  const metrics = parseMetricMap(value);
  const items = keys
    .filter((key) => metrics[key] !== undefined)
    .map((key) => ({
      label: t(`metric.label.${key}` as `metric.label.${string}`, key),
      value: displayMetricValue(key, metrics[key], t),
      tone: toneForMetric(key, metrics[key]),
    }));
  return items.length ? renderValueItems(items, caption) : value;
};

const renderIndustryOverlayValue = (value: string, t: TFunction<"portfolioPage">): ReactNode => {
  const [industry, indexName] = value.split("/").map((part) => part.trim()).filter(Boolean);
  return renderValueItems([
    { label: t("industry.label"), value: industry || value },
    ...(indexName ? [{ label: t("industry.indexLabel"), value: indexName, tone: "muted" as const }] : []),
  ]);
};

const relationLabel = (raw: string | undefined, t: TFunction<"portfolioPage">): string => {
  if (raw === "LEADER") return t("relation.LEADER");
  if (raw === "FOLLOWER") return t("relation.FOLLOWER");
  if (raw === "COINCIDENT") return t("relation.COINCIDENT");
  return t("relation.UNKNOWN");
};

const renderPeerOverlayValue = (value: string, t: TFunction<"portfolioPage">): ReactNode => {
  const peers = value.split(" | ");
  const items = peers.map((part) => {
    if (part.startsWith("selectedPeers=")) {
      return { label: t("peer.selectedLabel"), value: t("peer.selectedCount", { count: part.replace("selectedPeers=", "") }), tone: "muted" as const };
    }
    const [namePart, ...rest] = part.split(",");
    const metrics = parseMetricMap(rest.join(","));
    const corr = parseMetricMap(namePart).corr ?? namePart.split(" corr=")[1];
    const name = namePart.split(" corr=")[0];
    const lag = metrics.lag ? t("peer.lagLabel", { lag: metrics.lag }) : "";
    return {
      label: name,
      value: `${t("peer.corrLabel", { corr: compactNumber(corr, 3) ?? "-" })} · ${relationLabel(metrics.relation, t)}${lag}`,
      tone: Number(corr) >= 0.75 ? "warn" as const : "default" as const,
    };
  });
  return renderValueItems(items, t("peer.footerNote"));
};

const renderOverlayValue = (overlayType: string, value: string | null | undefined, t: TFunction<"portfolioPage">): ReactNode => {
  if (!value) return "-";
  if (overlayType === "fundamentals") {
    return renderMetricOverlayValue(value, ["PER", "PBR", "PSR", "ROE", "OPM", "NPM", "Debt", "RevenueGrowth", "EPSGrowth"], t);
  }
  if (overlayType === "technical") {
    return renderMetricOverlayValue(value, ["alignment", "percent_b", "zone", "k_minus_d"], t, t("metric.techCaption"));
  }
  if (overlayType === "news") {
    return renderMetricOverlayValue(value, ["score", "count"], t);
  }
  if (overlayType === "industry") {
    return renderIndustryOverlayValue(value, t);
  }
  if (overlayType === "correlation" && value.includes(" | ")) {
    return renderPeerOverlayValue(value, t);
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

const renderExplainSection = (section: PortfolioExplainSection | null | undefined, options: { hideTitle?: boolean } | undefined, t: TFunction<"portfolioPage">) => {
  if (!section?.summary && !section?.bullets?.length) return null;
  return (
    <div className="rounded-xl bg-bg-sunk border border-line p-4">
      {!options?.hideTitle ? <h4 className="text-sm font-bold text-ink">{section.title ?? t("explainSummary.fallbackTitle")}</h4> : null}
      {section.summary ? (
        <p className={`${options?.hideTitle ? "" : "mt-1"} text-xs leading-relaxed text-ink-3`}>
          <DictionaryText text={section.summary} />
        </p>
      ) : null}
      {!section.summary && section.bullets?.length ? (
        <ul className={`${options?.hideTitle ? "" : "mt-2"} flex flex-col gap-1`}>
          {section.bullets.slice(0, 4).map((bullet, index) => (
            <li key={`${section.title ?? "explain"}-${index}`} className="text-xs leading-relaxed text-ink-4">
              <DictionaryText text={bullet} />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
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

const polarPoint = (cx: number, cy: number, radius: number, angleDeg: number) => {
  const angle = ((angleDeg - 90) * Math.PI) / 180;
  return {
    x: cx + radius * Math.cos(angle),
    y: cy + radius * Math.sin(angle),
  };
};

function SvgPortfolioPieChart({
  weights,
  label,
  className,
}: {
  weights: PortfolioAnalyzeResponse["currentPortfolio"]["weights"];
  label: string;
  className: string;
}) {
  const positiveWeights = weights.filter((weight) => Math.max(0, weight.weight) > 0);
  const total = positiveWeights.reduce((sum, weight) => sum + Math.max(0, weight.weight), 0);
  let cursor = 0;

  if (total <= 0) {
    return (
      <svg viewBox="0 0 100 100" className={className} role="img" aria-label={label}>
        <circle cx="50" cy="50" r="49" fill="#e5e7eb" stroke="rgb(var(--color-line))" strokeWidth="1" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 100 100" className={className} role="img" aria-label={label}>
      {positiveWeights.map((weight, index) => {
        const ratio = Math.max(0, weight.weight) / total;
        const startAngle = cursor * 360;
        const endAngle = (cursor + ratio) * 360;
        cursor += ratio;
        const color = weight.assetType === "CASH" ? CASH_COLOR : pieColorForIndex(index);

        if (ratio >= 0.9999) {
          return <circle key={`${weight.stockCode}-${index}`} cx="50" cy="50" r="49" fill={color} />;
        }

        const start = polarPoint(50, 50, 49, startAngle);
        const end = polarPoint(50, 50, 49, endAngle);
        const largeArc = endAngle - startAngle > 180 ? 1 : 0;
        const path = [
          "M 50 50",
          `L ${start.x.toFixed(3)} ${start.y.toFixed(3)}`,
          `A 49 49 0 ${largeArc} 1 ${end.x.toFixed(3)} ${end.y.toFixed(3)}`,
          "Z",
        ].join(" ");

        return <path key={`${weight.stockCode}-${index}`} d={path} fill={color} />;
      })}
      <circle cx="50" cy="50" r="49" fill="none" stroke="rgb(var(--color-line))" strokeWidth="1" />
    </svg>
  );
}

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
  const { t } = useTranslation("portfolioPage");
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, toggle } = useTheme();
  const { investLevel } = useDictionary();
  const reportUserName = useReportUserName();
  // 로그인 여부: Portfolio Manager 잠금 오버레이 + 분석 실행 가드에 사용.
  // 토큰 상태는 마운트 시 한 번 평가하면 충분 — 로그인 후엔 /login → /feature/3 으로
  // 다시 마운트되므로 자연스럽게 갱신된다.
  const loggedIn = isLoggedIn();
  const goLogin = () => {
    sessionStorage.setItem("qaima_redirect", location.pathname + location.search);
    navigate("/login");
  };
  const [isOpen, setIsOpen] = useState(false);
  const [selectedMarket, setSelectedMarket] = useState<"domestic" | "overseas">("domestic");
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
  const [cashLimitManual, setCashLimitManual] = useState(false);

  // 설문에서 복귀했으면(sessionStorage) 그 값을, 없으면 마지막 저장값(localStorage)을 초기값으로 사용
  useEffect(() => {
    let alive = true;
    const applyRiskGamma = (value: number, useStoredCash: boolean) => {
      if (!alive) return;
      const clamped = clampRiskGamma(value);
      const storedCashLimit = useStoredCash ? readStoredFeature3CashLimit() : null;
      const manual = useStoredCash ? readStoredFeature3CashLimitManual() : false;
      setRiskGamma(clamped);
      setRiskGammaInput(formatRiskGamma(clamped));
      setCashLimit(storedCashLimit ?? autoCashLimitForRiskScore(clamped));
      setCashLimitManual(manual && storedCashLimit !== null);
    };

    const fromSurvey = sessionStorage.getItem(SURVEY_RESULT_STORAGE_KEY);
    if (fromSurvey !== null) {
      const parsed = Number(fromSurvey);
      if (Number.isFinite(parsed)) {
        syncFeature3RiskDefaults(parsed);
        applyRiskGamma(parsed, false);
      }
      sessionStorage.removeItem(SURVEY_RESULT_STORAGE_KEY);
      return () => {
        alive = false;
      };
    }

    const saved = readStoredFeature3RiskGamma();
    if (saved !== null) {
      applyRiskGamma(saved, true);
      return () => {
        alive = false;
      };
    }

    if (!loggedIn) {
      return () => {
        alive = false;
      };
    }

    getMyRiskProfile()
      .then((profile) => {
        if (profile.defaultRiskGamma === null) return;
        applyRiskGamma(profile.defaultRiskGamma, false);
      })
      .catch(() => {});

    return () => {
      alive = false;
    };
  }, [loggedIn]);

  useEffect(() => {
    // 환율 조회는 인증이 필요한 엔드포인트라 비로그인 시 401 → apiClient 가
    // 자동으로 /login 으로 보낸다. 비로그인 둘러보기를 허용하려면 호출 자체를 건너뛴다.
    if (!loggedIn) {
      setUsdKrwRate(null);
      setExchangeRateError("");
      return;
    }
    let alive = true;
    fetchFeature2MacroRates()
      .then((res) => {
        if (!alive) return;
        setUsdKrwRate(res.data?.usdKrw ?? null);
        setExchangeRateError(res.data?.usdKrw ? "" : t("assetRatio.exchangeRateNone"));
      })
      .catch(() => {
        if (!alive) return;
        setUsdKrwRate(null);
        setExchangeRateError(t("assetRatio.exchangeRateFailed"));
      });
    return () => {
      alive = false;
    };
  }, [loggedIn]);

  const commitRiskGamma = (v: number) => {
    const clamped = clampRiskGamma(v);
    setRiskGamma(clamped);
    setRiskGammaInput(formatRiskGamma(clamped));
    storeFeature3RiskGamma(clamped);
    if (!cashLimitManual) {
      const nextCashLimit = autoCashLimitForRiskScore(clamped);
      setCashLimit(nextCashLimit);
      storeFeature3CashLimit(nextCashLimit, false);
    }
  };

  const commitCashLimit = (v: number) => {
    const clamped = clampCashLimit(v);
    setCashLimit(clamped);
    setCashLimitManual(true);
    storeFeature3CashLimit(clamped, true);
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
      if (!cashLimitManual) {
        setCashLimit(autoCashLimitForRiskScore(clamped));
      }
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
  // 초기값은 빈 상태로 시작한다 — 시드 더미가 있으면 비로그인 사용자에게도 차트가 채워져
  // "이건 누구 포트폴리오지?" 라는 혼란을 준다. 로그인 사용자의 저장된 포트폴리오를
  // 서버에서 불러오는 로직은 백엔드에 해당 GET 엔드포인트가 생기면 여기에 붙인다.
  const [domesticRows, setDomesticRows] = useState<HoldingRow[]>([]);
  const [overseasRows, setOverseasRows] = useState<HoldingRow[]>([]);
  const [domesticCash, setDomesticCash] = useState(0);
  const [overseasCash, setOverseasCash] = useState(0);
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [savingPortfolio, setSavingPortfolio] = useState(false);
  const [portfolioSaveMessage, setPortfolioSaveMessage] = useState("");

  // 현재 선택된 마켓에 맞는 rows / setter
  const rows = selectedMarket === "domestic" ? domesticRows : overseasRows;
  const setRows = selectedMarket === "domestic" ? setDomesticRows : setOverseasRows;
  const cashAmount = selectedMarket === "domestic" ? domesticCash : overseasCash;
  const setCashAmount = selectedMarket === "domestic" ? setDomesticCash : setOverseasCash;
  const cashCurrency = selectedMarket === "domestic" ? "KRW" : "USD";
  const cashLabel = selectedMarket === "domestic" ? t("manager.cashDomestic") : t("manager.cashOverseas");
  const exchangeRate = usdKrwRate?.value ?? null;
  const cashValueKRW = selectedMarket === "domestic" ? cashAmount : exchangeRate ? cashAmount * exchangeRate : 0;
  const cashValueDisplay = Math.max(0, cashAmount);
  const riskyValue = rows.reduce((sum, r) => sum + r.quantity * r.avgPrice, 0);
  useEffect(() => {
    if (!loggedIn) return;
    let alive = true;

    fetchMyDefaultPortfolio()
      .then((portfolio) => {
        if (!alive) return;
        setDomesticRows((portfolio.holdings ?? []).map((holding, index) => ({
          id: index + 1,
          name: holding.stockName,
          stockCode: holding.stockCode,
          quantity: holding.quantity,
          avgPrice: holding.averagePrice,
        })));
        setDomesticCash(portfolio.cashAmount ?? 0);
      })
      .catch(() => {
        if (alive) {
          setPortfolioSaveMessage(t("manager.loadFailed"));
        }
      });

    return () => {
      alive = false;
    };
  }, [loggedIn]);

  // 총 금액 계산 (국내: 원, 해외: 달러 기준이라고 가정)
  const totalKRW =
    selectedMarket === "domestic"
      ? riskyValue + cashValueKRW
      : exchangeRate ? riskyValue * exchangeRate + cashValueKRW : 0;

  const totalUSD =
    selectedMarket === "domestic"
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
        label: row.name.trim() || t("manager.unnamedStock"),
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
  const [pdfExporting, setPdfExporting] = useState(false);
  const [isPortfolioZoomOpen, setIsPortfolioZoomOpen] = useState(false);
  const llmVendor = "GPT-5 mini";
  const portfolioPdfRef = useRef<HTMLElement | null>(null);
  const portfolioReportRef = useRef<HTMLDivElement | null>(null);
  const reportMeta = analysisResult
    ? {
        featureType: "FEATURE3" as const,
        subjectLabel: t("result.subjectLabel"),
        subjectDetail: `${rows[0]?.name || analysisResult.currentPortfolio.weights[0]?.companyName || t("result.subjectLabel")} 외 ${Math.max(0, rows.length - 1)}개 종목`,
        generatedAt: new Date().toISOString(),
        analysisModel: llmVendor,
        investLevel,
        userName: reportUserName,
        analysisWindow: t(`analysisOptions.analysisWindow.${selectedWindow.label}` as `analysisOptions.analysisWindow.${string}`, selectedWindow.label),
        dataAsOf: analysisResult.freshness?.newestDataAt ?? analysisResult.freshness?.priceSeriesAsOf ?? null,
        riskProfile: analysisResult.policy.riskProfile.profileType,
        priceBasis: t(`pricePolicy.${analysisResult.policy.pricePolicy.used}` as `pricePolicy.${string}`, analysisResult.policy.pricePolicy.used),
        covarianceModel: analysisResult.advanced?.covarianceDiagnostics?.usedCovarianceModel
          ? t(`covarianceModel.${analysisResult.advanced.covarianceDiagnostics.usedCovarianceModel}` as `covarianceModel.${string}`, analysisResult.advanced.covarianceDiagnostics.usedCovarianceModel)
          : null,
      }
    : null;

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
    if (!loggedIn) {
      goLogin();
      return;
    }
    const validRows = rows.filter((r) => r.name.trim() !== "");
    if (validRows.length === 0) {
      setErr(t("runBanner.minOneStock"));
      return;
    }
    if (riskGamma === null) {
      setErr(t("runBanner.needRiskProfileError"));
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
        investLevel,
        holdings: validRows.map((r) => ({
          stockCode: r.stockCode ?? r.name.trim(),
          companyName: r.name.trim(),
          quantity: r.quantity,
          avgPrice: r.avgPrice,
          currency: selectedMarket === "domestic" ? "KRW" : "USD",
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
      setErr(getApiErrorMessage(e, t("runBanner.analyzeFailed")));
    } finally {
      // 성공/실패 무관하게 서버 잔액과 동기화 (백엔드가 실패 시 환불 처리하므로
      // 환불된 잔액이 UI 에 즉시 반영되도록).
      refreshTokenBalance().catch(() => {});
      setLoading(false);
    }
  };

  const toggleOpen = () => setIsOpen((prev) => !prev);

  const handleSelect = (value: "domestic" | "overseas") => {
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
    document.body.classList.toggle("qaima-portfolio-zoom-active", isPortfolioZoomOpen);
    return () => {
      document.body.classList.remove("qaima-portfolio-zoom-active");
    };
  }, [isPortfolioZoomOpen]);

  useEffect(() => {
    const reportNode = portfolioReportRef.current;
    if (!reportNode || !analysisResult) return;

    const sections = Array.from(reportNode.children).filter(
      (child): child is HTMLElement => child instanceof HTMLElement,
    );

    if (pdfExporting) {
      sections.forEach((section) => {
        section.classList.add("is-visible");
        section.style.transitionDelay = "0ms";
      });
      return;
    }

    const revealVisibleSections = () => {
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
      sections.forEach((section) => {
        if (section.classList.contains("is-visible")) return;
        const rect = section.getBoundingClientRect();
        if (rect.top < viewportHeight + 240 && rect.bottom > -240) {
          section.classList.add("is-visible");
        }
      });
    };

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
      { threshold: 0.01, rootMargin: "240px 0px 240px 0px" },
    );

    sections.forEach((section) => observer.observe(section));
    requestAnimationFrame(revealVisibleSections);

    window.addEventListener("scroll", revealVisibleSections, { passive: true });
    window.addEventListener("resize", revealVisibleSections);
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", revealVisibleSections);
      window.removeEventListener("resize", revealVisibleSections);
    };
  }, [analysisResult, analysisTab, pdfExporting]);

  const handlePortfolioDownloadClick = async () => {
    if (!analysisResult || !portfolioPdfRef.current) return;

    setPdfExporting(true);
    try {
      await waitForPdfCaptureReady();
      await downloadElementAsPdf(
        portfolioPdfRef.current,
        `portfolio_${analysisTab.toLowerCase()}_analysis.pdf`,
        qaimaLogo,
      );
    } catch (e) {
      clientLog.error("Portfolio PDF generation failed", e);
    } finally {
      setPdfExporting(false);
    }
  };

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

  const handleSavePortfolio = async () => {
    if (!loggedIn) {
      goLogin();
      return;
    }
    setSavingPortfolio(true);
    setPortfolioSaveMessage("");
    try {
      const saved = await replaceMyDefaultPortfolio({
        cashAmount: Math.max(0, cashAmount),
        holdings: rows
          .filter((row) => row.name.trim() !== "" && row.quantity > 0 && row.avgPrice > 0)
          .map((row) => ({
            stockCode: (row.stockCode ?? row.name).trim(),
            stockName: row.name.trim(),
            quantity: row.quantity,
            averagePrice: row.avgPrice,
          })),
      });
      setDomesticRows((saved.holdings ?? []).map((holding, index) => ({
        id: index + 1,
        name: holding.stockName,
        stockCode: holding.stockCode,
        quantity: holding.quantity,
        avgPrice: holding.averagePrice,
      })));
      setDomesticCash(saved.cashAmount ?? 0);
      setSelectedMarket("domestic");
      setPortfolioSaveMessage(t("manager.saveSuccess"));
      setSaveModalOpen(false);
    } catch (e) {
      setPortfolioSaveMessage(getApiErrorMessage(e, t("manager.saveFailed")));
    } finally {
      setSavingPortfolio(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg md:ml-[84px]">
      {saveModalOpen && createPortal(
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center px-4">
          <div className="w-full max-w-sm rounded-2xl bg-surface border border-line shadow-card p-5">
            <h3 className="text-base font-bold text-ink tracking-tight">{t("saveModal.title")}</h3>
            <p className="mt-2 text-sm text-ink-3">{t("saveModal.body")}</p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSaveModalOpen(false)}
                disabled={savingPortfolio}
                className="px-4 py-2 rounded-lg border border-line text-sm font-semibold text-ink hover:bg-bg-sunk disabled:opacity-60"
              >
                {t("saveModal.cancel")}
              </button>
              <button
                type="button"
                onClick={handleSavePortfolio}
                disabled={savingPortfolio}
                className="px-4 py-2 rounded-lg bg-accent text-white text-sm font-semibold hover:opacity-90 disabled:opacity-60"
              >
                {savingPortfolio ? t("saveModal.saving") : t("saveModal.confirm")}
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
      <div className={`qaima-stagger ${isPortfolioZoomOpen ? "qaima-zoom-host" : ""} max-w-full sm:max-w-3xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 lg:px-6 py-4 sm:py-6 flex flex-col gap-4 sm:gap-6`}>
        {/* 헤더 */}
        <header className="flex items-center justify-between">
          <div>
            <div className="text-xs font-medium text-ink-3 tracking-tight">
              Portfolio · Analysis
            </div>
            <h1 className="mt-1 text-3xl font-bold text-ink tracking-tighter">
              {t("header.title")}
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={toggle}
              aria-label={theme === "dark" ? t("header.lightMode") : t("header.darkMode")}
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
                      <span className="font-semibold text-ink tracking-tight">{t(`market.${selectedMarket}`)}</span>
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
                          onClick={() => handleSelect("domestic")}
                          className="w-full px-2 py-1.5 text-center text-sm rounded-t-lg text-ink hover:bg-bg-sunk"
                        >
                          {t("market.domestic")}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSelect("overseas")}
                          className="w-full px-2 py-1.5 text-center text-sm rounded-b-lg text-ink hover:bg-bg-sunk"
                        >
                          {t("market.overseas")}
                        </button>
                      </div>
                    )}
                  </div>

                  {/* 타이틀: 좌우 내용 너비가 변해도 항상 카드 정중앙 고정 (밀림 방지) */}
                  <p className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-lg font-bold text-ink tracking-tight whitespace-nowrap pointer-events-none">
                    {selectedMarket === "domestic" ? t("assetRatio.titleDomestic") : t("assetRatio.titleOverseas")}
                  </p>

                  {/* 총 금액 / 환율 */}
                  <div className="flex-shrink-0 flex flex-col items-end gap-0.5">
                    <p className="font-medium text-sm font-mono tabular tracking-tight text-ink">
                      {selectedMarket === "domestic" || exchangeRate
                        ? `${Math.round(totalKRW).toLocaleString()}${t("assetRatio.won")}`
                        : "-"}
                    </p>
                    <p className="text-xs text-ink-3 font-mono tabular">
                      {selectedMarket === "overseas" || exchangeRate
                        ? `${totalUSD.toLocaleString(undefined, { maximumFractionDigits: 2 })}${t("assetRatio.dollar")}`
                        : "-"}
                    </p>
                    <p className="text-[11px] text-ink-4">
                      {exchangeRate
                        ? `환율 ${exchangeRate.toLocaleString()} · ${usdKrwRate?.date ?? usdKrwRate?.source ?? ""}`
                        : exchangeRateError || t("assetRatio.exchangeRateLoading")}
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
                          aria-label={selectedMarket === "domestic" ? t("assetRatio.titleDomestic") : t("assetRatio.titleOverseas")}
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
                          <p className="text-[10px] text-ink-4">{t("assetRatio.centerLabel")}</p>
                          <p className="mt-0.5 text-sm font-bold text-ink font-mono tabular leading-tight truncate">
                            {selectedMarket === "domestic"
                              ? `${Math.round(totalKRW).toLocaleString()}${t("assetRatio.won")}`
                              : `$${totalUSD.toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="w-full sm:w-auto sm:min-w-[200px] sm:max-w-[260px] min-w-0 flex flex-col gap-2 max-h-56 overflow-y-auto">
                      {holdingAllocations.length === 0 ? (
                        <div className="text-center sm:text-left">
                          <p className="text-sm font-medium text-ink-3">{t("assetRatio.emptyTitle")}</p>
                          <p className="mt-1 text-xs text-ink-4">
                            {loggedIn
                              ? t("assetRatio.emptyBodyLoggedIn")
                              : t("assetRatio.emptyBodyGuest")}
                          </p>
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
              <div className="relative w-full rounded-2xl bg-surface border border-line shadow-card">
                <div className="px-6 pt-5 pb-3 flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-ink tracking-tight">Portfolio Manager</h2>
                    <p className="text-sm mt-0.5 text-ink-3">{t("manager.subtitle")}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setSaveModalOpen(true)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors border border-line text-ink hover:bg-bg-sunk"
                    >
                      <Save size={14} />
                      {t("manager.save")}
                    </button>
                    <button
                      type="button"
                      onClick={handleAddRow}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors bg-accent text-white hover:opacity-90"
                    >
                      <Plus size={14} />
                      {t("manager.addStock")}
                    </button>
                  </div>
                </div>

                <div className="px-4 pb-4">
                  {/* 헤더 행 */}
                  <div className="grid grid-cols-[2fr,1.2fr,1.8fr,0.8fr] rounded-xl bg-bg-sunk">
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">{t("manager.col.name")}</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">{t("manager.col.quantity")}</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">{t("manager.col.avgPrice")}</span>
                    </div>
                    <div className="px-4 py-3 flex items-center justify-center">
                      <span className="text-xs uppercase font-bold tracking-wider text-ink-3">{t("manager.col.delete")}</span>
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
                        <span className="text-xs text-ink-4">{t("manager.cashFixed")}</span>
                      </div>
                    </div>
	                </div>
                  {portfolioSaveMessage && (
                    <div className="px-4 pb-4 text-sm text-ink-3">{portfolioSaveMessage}</div>
                  )}

                {/* 비로그인 잠금 오버레이: Portfolio Manager 영역 전체를 블러로 덮고 로그인 유도 */}
                {!loggedIn && (
                  <div className="absolute inset-0 z-20 rounded-2xl bg-surface/55 backdrop-blur-md flex flex-col items-center justify-center text-center px-6 py-8 gap-3">
                    <div className="w-12 h-12 grid place-items-center rounded-full bg-accent-soft text-accent shadow-card">
                      <Lock size={22} />
                    </div>
                    <div>
                      <p className="text-base font-bold text-ink tracking-tight">
                        {t("manager.lock.title")}
                      </p>
                      <p className="mt-1 text-sm text-ink-2 max-w-xs">
                        {t("manager.lock.body")}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={goLogin}
                      className="mt-1 px-5 py-2 rounded-lg bg-accent text-white text-sm font-semibold hover:opacity-90 transition-opacity"
                    >
                      {t("manager.lock.button")}
                    </button>
                  </div>
                )}
	              </div>

            </section>
          </div>

          {/* 둘째 행: 분석 옵션 (전체 너비, 가로 배치) */}
          <div className="w-full rounded-2xl p-5 bg-surface border border-line shadow-card">
            <h2 className="text-base font-bold text-ink tracking-tight mb-4">{t("analysisOptions.title")}</h2>

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
                    <span className="text-sm font-semibold text-accent">{t("analysisOptions.baseOption.label")}</span>
                    <span className="ml-2 text-xs text-ink-4">{t("analysisOptions.baseOption.locked")}</span>
                    <div className="mt-1 text-xs leading-relaxed text-ink-3">
                      <p>{t("analysisOptions.baseOption.desc0")}</p>
                      <p>{t("analysisOptions.baseOption.desc1")}</p>
                    </div>
                  </div>
                </div>

                {/* 추가 분석 옵션 (2열 그리드) */}
                <div>
                  <p className="text-xs mb-2 text-ink-4">{t("analysisOptions.extraLabel")}</p>
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
                        <span className="text-sm font-medium text-ink-2">{t(`analysisOptions.extraOption.${opt.key}.label` as `analysisOptions.extraOption.${string}.label`)}</span>
                        <div className="group relative flex-shrink-0">
                          <Info size={14} className="transition-colors text-ink-4 group-hover:text-ink-3" />
                          <div className="absolute left-5 top-0 w-64 p-2.5 text-xs rounded-lg shadow-lg opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-30 leading-relaxed bg-ink text-bg">
                            {[0, 1].map((i) => (
                              <p key={i} className={i > 0 ? "mt-1" : ""}>{t(`analysisOptions.extraOption.${opt.key}.desc${i}` as `analysisOptions.extraOption.${string}.desc0`)}</p>
                            ))}
                          </div>
                        </div>
                      </label>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="text-xs mb-2 text-ink-4">{t("analysisOptions.periodLabel")}</p>
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
                        {t(`analysisOptions.analysisWindow.${window.label}` as `analysisOptions.analysisWindow.${string}`, window.label)}
                      </button>
                    ))}
                    <span className="ml-1 text-xs text-ink-4">{t("analysisOptions.freqDay")}</span>
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
                      <span className="text-sm font-semibold text-accent">{t("riskProfile.title")}</span>
                      <span className="text-xs text-ink-4">{t("riskProfile.required")}</span>
                    </div>
                    <button
                      type="button"
                      onClick={handleGoSurvey}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors text-accent bg-surface border border-accent/30 hover:bg-accent/10"
                    >
                      <ClipboardList size={14} />
                      {t("riskProfile.surveyButton")}
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
                        <span>{t("riskProfile.sliderConservative")}</span>
                        <span>{t("riskProfile.sliderAggressive")}</span>
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
	                    {t("riskProfile.desc")}
	                  </p>
	                </div>

	                <div className="flex flex-col gap-3 px-3 py-3 rounded-lg bg-bg-sunk border border-line">
	                  <div className="flex items-center justify-between flex-wrap gap-2">
	                    <div className="flex items-center gap-1.5">
	                      <span className="text-sm font-semibold text-ink">{t("riskProfile.cashLimitTitle")}</span>
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
	                        onChange={(e) => commitCashLimit(Number(e.target.value))}
	                        className="w-full cursor-pointer accent-accent"
	                      />
	                      <div className="flex justify-between text-[11px] text-ink-3">
	                        <span>{t("riskProfile.cashLimitLeft")}</span>
	                        <span>{t("riskProfile.cashLimitRight")}</span>
	                      </div>
	                    </div>
	                    <span className="w-20 inline-block text-center text-sm font-semibold rounded-lg py-1.5 text-ink bg-surface border border-line font-mono tabular">
	                      {formatPct(cashLimit, 0)}
	                    </span>
	                  </div>

	                  <p className="text-xs leading-relaxed text-ink-3">
	                    {t("riskProfile.cashLimitDesc")}
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
                {t("runBanner.tag")}
              </div>
              <h3 className="text-lg font-bold text-ink tracking-tight">
                {t("runBanner.title")}
              </h3>
              <p className="text-sm text-ink-3 mt-1">
                {t("runBanner.subtitle")}
              </p>
              {!loading && loggedIn && riskGamma === null && (
                <p className="text-xs text-ink-4 mt-1.5">{t("runBanner.needRiskProfile")}</p>
              )}
              {!loading && !loggedIn && (
                <p className="text-xs text-ink-4 mt-1.5">{t("runBanner.needLogin")}</p>
              )}
              {err && <p className="text-xs text-danger mt-1.5">{err}</p>}
            </div>
            <div className="flex flex-col items-start sm:items-end gap-1 flex-shrink-0">
              <div className="flex flex-col sm:flex-row sm:items-center gap-3">
                <InvestLevelBadge />
                <div className="flex items-center gap-3">
                  <button
                  type="button"
                  onClick={() => void handleAnalyzeClick()}
                  disabled={loading || (loggedIn && riskGamma === null)}
                  className="px-5 py-2.5 rounded-xl bg-accent text-white font-semibold text-sm
                             hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity tracking-tight"
                >
                  {loading ? t("runBanner.analyzing") : t("runBanner.viewButton")}
                  </button>
                </div>
              </div>
              {!loading && loggedIn && riskGamma === null && (
                <p className="text-xs text-ink-4">{t("runBanner.needRiskProfile")}</p>
              )}
              {err && <p className="text-xs text-danger">{err}</p>}
            </div>
          </section>

          {overlayPreview && (
            <section className="rounded-2xl border border-accent/30 shadow-card p-5 bg-surface">
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-ink">{t("overlayPreview.title")}</h3>
                  <p className="mt-1 text-sm text-ink-3">{overlayPreviewMessage(overlayPreview.userMessage)}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-ink-4">{t("overlayPreview.estimatedCharge")}</p>
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
                      <span className="text-xs font-bold text-ink-4">{t(`cacheStatus.${overlay.cacheStatus ?? ""}` as `cacheStatus.${string}`, overlay.cacheStatus ?? "")}</span>
                    </div>
                    <p className="mt-1 text-xs text-ink-3">{overlayPreviewMessage(overlay.userMessage)}</p>
                    <p className="mt-2 text-[11px] text-ink-4">
                      {overlay.cacheStatus === "MISS"
                        ? t("overlayPreview.newAfterAnalysis")
                        : overlay.cacheAsOf
                          ? overlayCachePolicies[overlay.overlayType] === "FORCE_REFRESH"
                            ? t("overlayPreview.refreshScheduled", { date: overlay.cacheAsOf })
                            : overlay.cacheStatus === "AVAILABLE"
                              ? t("overlayPreview.existingBase", { date: overlay.cacheAsOf })
                              : t("overlayPreview.existingBase", { date: overlay.cacheAsOf })
                          : overlay.cacheStatus === "AVAILABLE"
                            ? t("overlayPreview.currentClassBase")
                            : t("overlayPreview.pendingBase")}
                    </p>
                    {overlay.policySelectable !== false && overlay.cacheStatus === "HIT" && (
                      <div className="mt-3 grid grid-cols-1 gap-1.5">
                        {[
                          { value: "REUSE_AVAILABLE" as const, label: t("overlayPreview.reuse") },
                          { value: "FORCE_REFRESH" as const, label: t("overlayPreview.forceRefresh") },
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
                  {t("overlayPreview.cancel")}
                </button>
                <button
                  type="button"
                  onClick={() => void handleAnalyzeClick(true)}
                  className="px-4 py-2 rounded-lg text-sm font-semibold bg-ink text-bg hover:opacity-90"
                >
                  {t("overlayPreview.confirm")}
                </button>
              </div>
            </section>
          )}

          {/* 하단 전체 너비: 분석 결과 영역 */}
          {!analysisResult && !loading && (
            <div className="w-full rounded-2xl border-2 border-dashed flex flex-col items-center justify-center py-24 bg-bg-sunk border-line text-ink-4">
              <p className="text-base font-medium">{t("emptyResult.title")}</p>
              <p className="text-sm mt-1">{t("emptyResult.subtitle")}</p>
            </div>
          )}

          {loading && (
            <div className="w-full rounded-2xl border-2 flex items-center justify-center py-24 bg-bg-sunk border-line">
              <p className="text-base animate-pulse text-ink-3">{t("loading")}</p>
            </div>
          )}

          {/* 3.1 핵심 요약 카드 */}
          {isPortfolioZoomOpen && (
            <div
              className="fixed inset-0 z-[90] bg-ink/50"
              onClick={() => setIsPortfolioZoomOpen(false)}
            />
          )}
          {analysisResult && (
            <section
              ref={portfolioPdfRef}
              className={`qaima-portfolio-report qaima-report-enter flex min-w-0 flex-col gap-5 overflow-hidden ${
                isPortfolioZoomOpen
                  ? "fixed inset-x-4 top-4 bottom-4 z-[100] mx-auto w-auto max-w-6xl overflow-y-auto rounded-2xl bg-bg p-5 sm:p-7 shadow-pop"
                  : "w-full"
              }`}
              onClick={(event) => {
                if (isPortfolioZoomOpen) event.stopPropagation();
              }}
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <h2 className="text-lg font-bold text-ink tracking-tight">{t("result.title")}</h2>
                <div data-pdf-exclude="true" className="flex flex-wrap items-center gap-2">
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
                      {t("result.tabBasic")}
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
                      {t("result.tabAdvanced")}
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={handlePortfolioDownloadClick}
                    aria-label={t("result.downloadPdf")}
                    title={t("result.downloadPdf")}
                    className="w-9 h-9 grid place-items-center rounded-lg border border-line bg-surface text-ink-2 hover:bg-bg-sunk transition-colors"
                  >
                    <Download size={16} />
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsPortfolioZoomOpen((prev) => !prev)}
                    aria-label={isPortfolioZoomOpen ? t("result.zoomClose") : t("result.zoomIn")}
                    title={isPortfolioZoomOpen ? t("result.zoomClose") : t("result.zoomIn")}
                    className="w-9 h-9 grid place-items-center rounded-lg border border-line bg-surface text-ink-2 hover:bg-bg-sunk transition-colors"
                  >
                    {isPortfolioZoomOpen ? <span className="text-lg leading-none">x</span> : <Maximize2 size={16} />}
                  </button>
                </div>
              </div>

              <ReportHeader meta={reportMeta} />

              {analysisTab === "BASIC" && <div ref={portfolioReportRef} className="qaima-scroll-stagger flex min-w-0 flex-col gap-5">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* 카드 1: 위험 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    {t("summary.riskLevelCard")}
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {(analysisResult.currentPortfolio.volatility * 100).toFixed(1)}
                      <span className="text-base font-normal ml-0.5 text-ink-4">%</span>
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">{t("summary.portfolioVolatility")}</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.summary.riskLevel === "LOW"
                        ? "bg-success/10 text-success"
                        : analysisResult.summary.riskLevel === "MID"
                          ? "bg-warn/10 text-warn"
                          : "bg-danger/10 text-danger"
                    }`}
                  >
                    {t(`riskLevel.${analysisResult.summary.riskLevel}` as `riskLevel.${string}`, analysisResult.summary.riskLevel)}
                  </span>
                </div>

                {/* 카드 2: 분산 수준 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    {t("summary.targetVolCard")}
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {(analysisResult.summary.targetVolatility * 100).toFixed(1)}
                      <span className="text-base font-normal ml-0.5 text-ink-4">%</span>
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">
                    {t("summary.targetVolDesc")}
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
                    {t(`suitability.${analysisResult.summary.suitability}` as `suitability.${string}`, analysisResult.summary.suitability)}
                  </span>
                </div>

                {/* 카드 3: 효율성 */}
                <div className="rounded-2xl p-5 flex flex-col gap-3 bg-surface border border-line shadow-card">
                  <p className="text-xs font-bold uppercase tracking-wider text-ink-4">
                    {t("summary.profileCard")}
                  </p>
                  <div className="flex items-end gap-2">
                    <span className="text-3xl font-bold text-ink font-mono tabular tracking-tighter">
                      {analysisResult.policy.riskProfile.profileType}
                    </span>
                  </div>
                  <p className="text-sm text-ink-3">{t("summary.profileDesc")}</p>
                  <span
                    className={`self-start px-3 py-1 rounded-full text-xs font-bold ${
                      analysisResult.summary.volatilityGap <= 0
                        ? "bg-success/10 text-success"
                        : "bg-danger/10 text-danger"
                    }`}
                  >
                    {t("summary.volDiff", { value: (analysisResult.summary.volatilityGap * 100).toFixed(1) })}
                  </span>
                </div>
              </div>

              {analysisResult.explain?.sections?.coreRisk ? (
                <div>
                  {renderExplainSection(analysisResult.explain.sections.coreRisk, undefined, t)}
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
                    <h3 className="text-base font-bold text-ink">{t("volatilitySection.title")}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-3">
                      {t("volatilitySection.desc")}
                      <br></br>
                      {t("volatilitySection.desc2")}
                    </p>
                    <div className="mt-3">
                      {renderExplainSection(analysisResult.explain?.sections?.volatilityAnalysis, { hideTitle: true }, t)}
                    </div>
                    <div className="mt-3 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                      {basicPortfolioCards.map((portfolio) => (
                        <div key={portfolio.type} className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-bold text-ink">
                                {t(`portfolioType.${portfolio.type}` as `portfolioType.${string}`, portfolio.label ?? portfolio.type)}
                              </p>
                              <p className="text-xs mt-1 text-ink-4">{portfolio.userDescription}</p>
                            </div>
                            <span
                              title={t("volatilitySection.desc2")}
                              className={`px-2 py-1 rounded-full text-[11px] font-bold ${
                                portfolio.riskLevel === "LOW"
                                  ? "bg-success/10 text-success"
                                  : portfolio.riskLevel === "MID"
                                    ? "bg-warn/10 text-warn"
                                    : "bg-danger/10 text-danger"
                              }`}
                            >
                              {t(`riskLevel.${portfolio.riskLevel}` as `riskLevel.${string}`, portfolio.riskLevel)}
                            </span>
                          </div>

                          <div className="mt-4 flex items-center gap-4">
                            {pdfExporting ? (
                              <SvgPortfolioPieChart
                                weights={portfolio.weights}
                                label={`${portfolio.label} 비중 차트`}
                                className="w-20 h-20 flex-shrink-0"
                              />
                            ) : (
                              <div
                                className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                style={portfolioPieStyle(portfolio.weights)}
                                aria-label={`${portfolio.label} 비중 차트`}
                              />
                            )}
                            <div className="flex-1 min-w-0">
                              <p className="text-xs text-ink-4">{t("volatilitySection.volatilityLabel")}</p>
                              <p className="text-2xl font-bold text-ink font-mono tabular tracking-tighter">
                                {formatPct(portfolio.volatility)}
                              </p>
                              <p className="mt-1 text-[11px] text-ink-4 font-mono tabular">
                                Sharpe {portfolio.sharpeRatio?.toFixed(2) ?? "-"}
                              </p>
                              {portfolio.targetVolatility !== null && portfolio.targetVolatility !== undefined && (
                                <p className="text-[11px] text-ink-4">
                                  {t("volatilitySection.targetLabel", { value: formatPct(portfolio.targetVolatility) })}
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
	                const portfolioLabel = (type: string, label?: string) =>
	                  t(`portfolioBasicLabel.${type}` as `portfolioBasicLabel.${string}`, label ?? type);
	                const theoreticalPortfolio = analysisResult.advanced?.candidatePortfolios.find((portfolio) => portfolio.type === "THEORETICAL_UTILITY") ?? null;
	                const theoreticalBasicLabel = theoreticalPortfolio?.constraintBinding === "CASH_MAX" ? t("theoreticalCard.cashMaxLabel") : t("theoreticalCard.addCashLabel");
	                const theoreticalFundingLabel = theoreticalPortfolio?.constraintBinding === "CASH_MAX" ? t("theoreticalCard.cashMaxFundingLabel") : t("theoreticalCard.addCashFundingLabel");
	                const theoreticalFundingValue = theoreticalPortfolio?.constraintBinding === "CASH_MAX"
	                  ? t("theoreticalCard.cashPrefix", { value: formatPct(1 - (theoreticalPortfolio.theoreticalRiskyAllocation ?? 1)) })
	                  : formatKRW(theoreticalPortfolio?.additionalRequiredCash);
	                const portfolioCards = [
	                  analysisResult.currentPortfolio,
	                  ...["MIN_VOL", "MAX_SHARPE", "RISK_ALLOCATION", "UTILITY_OPTIMAL"]
                    .map((type) => analysisResult.advanced?.candidatePortfolios.find((portfolio) => portfolio.type === type))
                    .filter((portfolio): portfolio is NonNullable<typeof portfolio> => Boolean(portfolio)),
                ];

                return (
                  <div className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                    <h3 className="text-base font-bold text-ink">{t("efficiencySection.title")}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-3">
                      {t("efficiencySection.desc")} <br></br>
                      {t("efficiencySection.desc2")}
                    </p>
                    <div className="mt-3">
                      {renderExplainSection(analysisResult.explain?.sections?.efficiencyAnalysis, { hideTitle: true }, t)}
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
                            {pdfExporting ? (
                              <SvgPortfolioPieChart
                                weights={portfolio.weights}
                                label={`${portfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                                className="w-20 h-20 flex-shrink-0"
                              />
                            ) : (
                              <div
                                className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                style={portfolioPieStyle(portfolio.weights)}
                                aria-label={`${portfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                              />
                            )}
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
                                        <span className="truncate text-ink-4">{t("efficiencySection.unrealizedReturn")}</span>
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
	                                ? t("theoreticalCard.cashMaxDesc")
	                                : t("theoreticalCard.addCashDesc")}
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
	                          {pdfExporting ? (
	                            <SvgPortfolioPieChart
	                              weights={theoreticalPortfolio.weights}
	                              label={`${theoreticalBasicLabel} 구성비 파이차트`}
	                              className="w-20 h-20 flex-shrink-0"
	                            />
	                          ) : (
	                            <div
	                              className="w-20 h-20 rounded-full border border-line flex-shrink-0"
	                              style={portfolioPieStyle(theoreticalPortfolio.weights)}
	                              aria-label={`${theoreticalBasicLabel} 구성비 파이차트`}
	                            />
	                          )}
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
	                                <p>{t("theoreticalCard.riskyMultiplier")}</p>
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
                        <h3 className="text-base font-bold text-ink">{t("marketAnalysis.title")}</h3>
                        <p className="mt-1 text-sm leading-relaxed text-ink-3">
                          {t("marketAnalysis.desc")}
                        </p>
                      </div>
                      <span className="rounded-md border border-line bg-bg-sunk px-2 py-1 text-[11px] font-mono text-ink-3">
                        {t(`capmStatus.${capm?.status ?? "UNAVAILABLE"}` as `capmStatus.${string}`, capm?.status ?? t("capmStatus.UNAVAILABLE"))}
                      </span>
                    </div>

                    {plainSummary ? (
                      <div className="mt-3 rounded-xl bg-bg-sunk border border-line p-4">
                        <p className="text-xs leading-relaxed text-ink-3">{plainSummary}</p>
                      </div>
                    ) : null}

                    <div className="mt-3 grid grid-cols-1 md:grid-cols-4 gap-2">
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">{t("marketAnalysis.benchmarkCard")}</p>
                        <p className="mt-1 text-sm font-bold text-ink">{benchmark?.benchmarkName ?? benchmark?.benchmarkCode ?? "-"}</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">{t(`benchmarkSource.${benchmark?.source ?? ""}` as `benchmarkSource.${string}`, "-")} · {t("marketAnalysis.missingRate", { value: formatPct(benchmark?.missingRate) })}</p>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">{t("marketAnalysis.appliedCard")}</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{appliedCount}{t("marketAnalysis.appliedCountSuffix")}</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">{t("marketAnalysis.excludedSuffix", { count: excludedCount, partial: capm?.partialAssetCount ?? 0 })}</p>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">{t("marketAnalysis.capmWeightCard")}</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{formatPct(averageCapmWeight)}</p>
                        <div className="mt-2 h-2 rounded-full bg-surface overflow-hidden">
                          <div className="h-full rounded-full bg-accent" style={{ width: `${Math.min(100, Math.max(0, averageCapmWeight * 100))}%` }} />
                        </div>
                      </div>
                      <div className="rounded-lg bg-bg-sunk border border-line p-3">
                        <p className="text-[11px] text-ink-4">{t("marketAnalysis.confidenceCard")}</p>
                        <p className="mt-1 text-sm font-mono font-bold text-ink">{formatPct(averageBlendConfidence)}</p>
                        <p className="mt-0.5 text-[11px] text-ink-4">{t("marketAnalysis.confidenceDesc")}</p>
                      </div>
                    </div>

                    {capmAssets.length ? (
                      <div className="mt-3 grid grid-cols-1 xl:grid-cols-2 gap-3">
                        <div className="rounded-lg bg-bg-sunk border border-line p-3">
                          <h4 className="text-xs font-bold text-ink">{t("marketAnalysis.historicalExpected")}</h4>
                          <div className="mt-3 flex flex-col gap-3">
                            {capmAssets.slice(0, 8).map((asset) => {
                              const capmWeight = Math.min(1, Math.max(0, asset.capmWeight ?? 0));
                              const historicalWeight = Math.min(1, Math.max(0, asset.historicalWeight ?? (1 - capmWeight)));
                              return (
                                <div key={`capm-basic-bar-${asset.stockCode}`}>
                                  <div className="flex items-center justify-between gap-2 text-[11px]">
                                    <span className="font-semibold text-ink truncate">{asset.companyName ?? asset.stockCode}</span>
                                    <span className="font-mono tabular text-ink-4">
                                      {t("marketAnalysis.blendRatioLabel", { historical: formatPct(historicalWeight, 0), capm: formatPct(capmWeight, 0) })}
                                    </span>
                                  </div>
                                  <div className="mt-1 flex h-2 rounded-full bg-surface overflow-hidden">
                                    <div className="h-full bg-slate-400" style={{ width: `${historicalWeight * 100}%` }} />
                                    <div className="h-full bg-accent" style={{ width: `${capmWeight * 100}%` }} />
                                  </div>
                                  <div className="mt-1 grid grid-cols-3 gap-2 text-[10px] text-ink-4">
                                    <span>{t("marketAnalysis.historicalLabel", { value: formatPct(asset.historicalExpectedReturn) })}</span>
                                    <span>{t("marketAnalysis.capmLabel", { value: asset.capmExpectedReturn !== null && asset.capmExpectedReturn !== undefined ? formatPct(asset.capmExpectedReturn) : "-" })}</span>
                                    <span className="text-ink">{t("marketAnalysis.finalLabel", { value: formatPct(asset.blendedExpectedReturn) })}</span>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>

                        <div className="rounded-lg bg-bg-sunk border border-line p-3">
                          <h4 className="text-xs font-bold text-ink">{t("marketAnalysis.summaryTable")}</h4>
                          <div className="mt-2 overflow-x-auto">
                            <table className="w-full min-w-[460px] text-left text-[11px]">
                              <thead className="text-ink-4">
                                <tr>
                                  <th className="py-2 pr-3">{t("marketAnalysis.col.stock")}</th>
                                  <th className="py-2 pr-3">{t("marketAnalysis.col.capmWeight")}</th>
                                  <th className="py-2 pr-3">{t("marketAnalysis.col.finalExpected")}</th>
                                  <th className="py-2 pr-3">{t("marketAnalysis.col.status")}</th>
                                </tr>
                              </thead>
                              <tbody>
                                {capmAssets.slice(0, 8).map((asset) => (
                                  <tr key={`capm-basic-table-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                    <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                    <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.capmWeight)}</td>
                                    <td className="py-2 pr-3 font-mono tabular text-ink">{formatPct(asset.blendedExpectedReturn)}</td>
                                    <td className="py-2 pr-3">{t(`capmAssetStatus.${asset.status ?? ""}` as `capmAssetStatus.${string}`, "-")}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    ) : null}

                    <p className="mt-3 rounded-lg bg-bg-sunk border border-line px-3 py-2 text-[11px] leading-relaxed text-ink-4">
                      {t("marketAnalysis.footnote")}
                    </p>
                  </section>
                );
              })()}

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="rounded-2xl p-5 bg-surface border border-line shadow-card">
                  <h3 className="text-base font-bold text-ink">{t("riskContribution.title")}</h3>
                  <p className="mt-1 text-xs text-ink-4">{t("riskContribution.subtitle")}</p>
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
                  <h3 className="text-base font-bold text-ink">{t("keyInsight.title")}</h3>
                  <div className="mt-4 flex flex-col gap-3">
                    {analysisResult.riskDrivers.map((driver) => (
                      <div key={driver.code} className="rounded-xl bg-bg-sunk border border-line p-3">
                        <div className="flex items-center justify-between gap-2">
                          <p className="text-sm font-bold text-ink">{driver.title}</p>
                          <span className="text-[11px] font-bold text-ink-4">{t(`featureSource.${driver.source}` as `featureSource.${string}`, driver.source)}</span>
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
	                      <h3 className="text-base font-bold text-ink">{t("overlayScenario.title")}</h3>
	                      <p className="mt-1 text-sm leading-relaxed text-ink-3">
	                        {t("overlayScenario.desc")}
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
                    {renderExplainSection(analysisResult.explain?.sections?.overlayObservations, undefined, t)}
                  </div>

                  {analysisResult.overlays.explanations?.length ? (
                    <div className="mt-5 grid grid-cols-1 lg:grid-cols-2 gap-3">
                      {analysisResult.overlays.explanations.map((item, index) => (
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
                          <div className="mt-1 text-xs leading-relaxed text-ink-3">{renderOverlayValue(item.overlayType, item.description, t)}</div>
                        </div>
                      ))}
                    </div>
                  ) : null}

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
	                            <p className="text-sm font-bold text-ink">{t(`portfolioType.${portfolio.type}` as `portfolioType.${string}`, portfolio.label ?? portfolio.type)}</p>
	                            <p className="mt-1 text-xs leading-relaxed text-ink-3">{portfolio.userDescription}</p>
	                          </div>
	                          <span className="text-[11px] font-bold text-ink-4">{t(`riskLevel.${portfolio.riskLevel}` as `riskLevel.${string}`, portfolio.riskLevel)}</span>
	                        </div>
                        <div className="mt-4 flex items-center gap-3">
                          {pdfExporting ? (
                            <SvgPortfolioPieChart
                              weights={portfolio.weights}
                              label={`${portfolio.label} 구성비 파이차트`}
                              className="w-16 h-16 flex-shrink-0"
                            />
                          ) : (
                            <div
                              className="w-16 h-16 rounded-full border border-line flex-shrink-0"
                              style={portfolioPieStyle(portfolio.weights)}
                              aria-label={`${portfolio.label} 구성비 파이차트`}
                            />
                          )}
                          <div className="min-w-0">
                            <p className="text-[11px] text-ink-4">{t("volatilitySection.volatilityLabel")}</p>
	                            <p className="text-lg font-bold font-mono tabular text-ink">{formatPct(portfolio.volatility)}</p>
	                            <p className="mt-0.5 text-[11px] font-mono tabular text-ink-4">
	                              {t("overlayScenario.maxDelta", { value: formatPctPoint(maxAbsDelta) })}
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
                    {renderExplainSection(analysisResult.explain?.sections?.portfolioComparison, undefined, t)}
                  </div>

                  {analysisResult.explain?.sections?.finalJudgement ? (
                    <div className="mt-5">
                      {renderExplainSection(analysisResult.explain.sections.finalJudgement, undefined, t)}
                    </div>
                  ) : analysisResult.explain?.text && !explainSections(analysisResult.explain).length ? (
                    <div className="mt-5 rounded-xl bg-bg-sunk border border-line p-4">
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="text-base font-bold text-ink">{t("explainSummary.title")}</h3>
                        <span className="text-[11px] font-bold text-ink-4">{analysisResult.explain.provider}</span>
                      </div>
                      <p className="mt-3 text-sm leading-relaxed text-ink-3">
                        <DictionaryText text={analysisResult.explain.text} />
                      </p>
                    </div>
                  ) : null}
                </section>
              ) : null}

              {analysisResult.warnings.length > 0 && (
                <div className="rounded-2xl p-4 bg-warn/10 border border-warn/20">
                  <p className="text-sm font-bold text-ink">{t("warnings.title")}</p>
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

              {analysisTab === "ADVANCED" && <div ref={portfolioReportRef} className="qaima-scroll-stagger min-w-0 overflow-hidden rounded-2xl p-3 sm:p-5 bg-surface border border-line shadow-card">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-base font-bold text-ink"><DictionaryText text={t("advanced.title")} /></h3>
                    <p className="mt-1 text-xs text-ink-4">
                      <DictionaryText text={analysisResult.freshness.userMessage ?? ""} />
                    </p>
                  </div>
                  <span className="text-xs font-mono tabular text-ink-4">
                    {t("advanced.includedCount", { count: analysisResult.policy.dataQuality.includedHoldingCount, excluded: analysisResult.policy.dataQuality.excludedHoldingCount })}
                  </span>
                </div>

                <div className="mt-4 grid grid-cols-1 lg:grid-cols-5 gap-3">
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4"><DictionaryText text={t("advanced.priceBase")} /></p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {t(`pricePolicy.${analysisResult.policy.pricePolicy.used}` as `pricePolicy.${string}`, analysisResult.policy.pricePolicy.used)}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4"><DictionaryText text={t("advanced.sampleCount")} /></p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {analysisResult.advanced?.covarianceDiagnostics?.sampleSize ?? 0}{t("advanced.sampleUnit")}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4"><DictionaryText text={t("advanced.commonMissing")} /></p>
                    <p className="mt-1 text-sm font-bold text-ink font-mono tabular">
                      {formatPct(analysisResult.policy.dataQuality.commonMissingRate)}
                    </p>
                    <p className="mt-0.5 text-[11px] text-ink-4">
                      {t("advanced.missingDetail", { threshold: formatPct(analysisResult.policy.dataPolicy.maxCommonMissingRate, 0), count: analysisResult.policy.dataQuality.commonPriceCount })}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4"><DictionaryText text={t("advanced.covModel")} /></p>
                    <p className="mt-1 text-sm font-bold text-ink">
                      {analysisResult.advanced?.covarianceDiagnostics?.usedCovarianceModel
                        ? t(`covarianceModel.${analysisResult.advanced.covarianceDiagnostics.usedCovarianceModel}` as `covarianceModel.${string}`, analysisResult.advanced.covarianceDiagnostics.usedCovarianceModel)
                        : "-"}
                    </p>
                  </div>
                  <div className="rounded-xl bg-bg-sunk border border-line p-3">
                    <p className="text-xs text-ink-4"><DictionaryText text={t("advanced.riskFree")} /></p>
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
                  const renderSclChart = (series: Feature3SclSeries) => {
                    const asset = capmAssets.find((item) => item.stockCode === series.stockCode) ?? null;
                    const points = series.points ?? [];
                    const line = series.line;
                    const marketValues = points.map((point) => point.marketReturn).filter(Number.isFinite);
                    const assetValues = points.map((point) => point.assetReturn).filter(Number.isFinite);
                    const minXRaw = Math.min(...marketValues, -0.01);
                    const maxXRaw = Math.max(...marketValues, 0.01);
                    const minYRaw = Math.min(...assetValues, -0.01);
                    const maxYRaw = Math.max(...assetValues, 0.01);
                    const padX = Math.max((maxXRaw - minXRaw) * 0.12, 0.005);
                    const padY = Math.max((maxYRaw - minYRaw) * 0.12, 0.005);
                    const minX = minXRaw - padX;
                    const maxX = maxXRaw + padX;
                    const minY = minYRaw - padY;
                    const maxY = maxYRaw + padY;
                    const x = (value: number) => 44 + ((value - minX) / Math.max(maxX - minX, 0.0001)) * 292;
                    const y = (value: number) => 168 - ((value - minY) / Math.max(maxY - minY, 0.0001)) * 128;
                    const regression = (marketReturn: number) => (line?.dailyAlpha ?? 0) + (line?.beta ?? 0) * marketReturn;
                    const path = line?.beta !== null && line?.beta !== undefined
                      ? `M ${x(minXRaw).toFixed(1)} ${y(regression(minXRaw)).toFixed(1)} L ${x(maxXRaw).toFixed(1)} ${y(regression(maxXRaw)).toFixed(1)}`
                      : "";

                    return (
                      <div key={`scl-pdf-${series.stockCode}`} className="mt-2 rounded-lg bg-bg-sunk border border-line p-2">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <p className="text-[11px] font-bold text-ink">
                              {series.companyName ?? asset?.companyName ?? series.stockCode}
                            </p>
                            <p className="mt-0.5 text-[10px] text-ink-4">
                              {t("sclSection.regressionDesc", { benchmark: series.benchmarkName ?? asset?.benchmarkName ?? series.benchmarkCode ?? asset?.benchmarkCode ?? t("capmSection.benchmarkCard") })}
                            </p>
                          </div>
                          <div className="flex flex-wrap gap-2 text-[10px] font-mono tabular text-ink-4">
                            <span>β {line?.beta?.toFixed(3) ?? "-"}</span>
                            <span>{t("sclSection.tableCol.dailyAlpha")} {line?.dailyAlpha !== null && line?.dailyAlpha !== undefined ? formatPct(line.dailyAlpha, 3) : "-"}</span>
                            <span>R² {asset?.rSquared?.toFixed(3) ?? "-"}</span>
                          </div>
                        </div>
                        <svg
                          viewBox="0 0 360 198"
                          className="mt-2 h-52 w-full"
                          role="img"
                          aria-label={`${series.companyName ?? series.stockCode} Security Characteristic Line regression chart`}
                        >
                          <line x1="44" y1="168" x2="336" y2="168" stroke="currentColor" className="text-line" />
                          <line x1="44" y1="168" x2="44" y2="40" stroke="currentColor" className="text-line" />
                          <line x1={x(0)} y1="40" x2={x(0)} y2="168" stroke="#cbd5e1" strokeDasharray="3 4" />
                          <line x1="44" y1={y(0)} x2="336" y2={y(0)} stroke="#cbd5e1" strokeDasharray="3 4" />
                          <text x="336" y="158" textAnchor="end" className="fill-ink-4 text-[10px]">{t("sclSection.chartBenchmarkLabel")}</text>
                          <text x="44" y="24" className="fill-ink-4 text-[10px]">{t("sclSection.chartAssetLabel")}</text>
                          <text x="44" y="184" className="fill-ink-4 text-[9px]">{formatPct(minX)}</text>
                          <text x="336" y="184" textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(maxX)}</text>
                          <text x="38" y={y(minY)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(minY)}</text>
                          <text x="38" y={y(maxY)} textAnchor="end" className="fill-ink-4 text-[9px]">{formatPct(maxY)}</text>
                          {path && <path d={path} fill="none" stroke="#dc2626" strokeWidth="2.4" strokeLinecap="round" />}
                          {points.slice(-180).map((point, index) => (
                            <circle
                              key={`scl-pdf-point-${series.stockCode}-${point.date ?? index}`}
                              cx={x(point.marketReturn)}
                              cy={y(point.assetReturn)}
                              r="2.3"
                              fill="#2563eb"
                              opacity="0.42"
                            />
                          ))}
                        </svg>
                        <div className="mt-1 flex flex-wrap gap-3 text-[10px] text-ink-4">
                          <span className="text-accent"><DictionaryText text={t("sclSection.legend.points")} /></span>
                          <span className="text-danger"><DictionaryText text={t("sclSection.legend.line")} /></span>
                          <span><DictionaryText text={t("sclSection.legend.count", { count: Math.min(180, points.length) })} /></span>
                        </div>
                      </div>
                    );
                  };
                  const sclHoverMarket = sclHover ? toSclMarket(sclHover.x) : null;
                  const sclHoverAsset = sclHover ? toSclAsset(sclHover.y) : null;
                  const smlHoverBeta = smlHover ? toSmlBeta(smlHover.x) : null;
                  const smlHoverReturn = smlHover ? toSmlReturn(smlHover.y) : null;
                  return (
                    <div className="mt-4 rounded-xl bg-bg-sunk border border-line p-3">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <h4 className="text-sm font-bold text-ink">{t("capmSection.title")}</h4>
                          <p className="mt-1 text-xs leading-relaxed text-ink-4">
                            {t("capmSection.desc")}
                          </p>
                        </div>
                        <span className="rounded-md border border-line bg-surface px-2 py-1 text-[11px] font-mono text-ink-3">
                          {t(`capmStatus.${capm?.status ?? "UNAVAILABLE"}` as `capmStatus.${string}`, capm?.status ?? t("capmStatus.UNAVAILABLE"))}
                        </span>
                      </div>
                      <div className="mt-3 grid grid-cols-1 md:grid-cols-4 gap-2">
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">{t("capmSection.benchmarkCard")}</p>
                          <p className="mt-1 text-xs font-bold text-ink">
                            {benchmark?.mode === "MULTI_BENCHMARK"
                              ? t("capmSection.multiBenchmark", { count: benchmark.benchmarks?.length ?? 0 })
                              : primaryBenchmark?.benchmarkName ?? primaryBenchmark?.benchmarkCode ?? "-"}
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {benchmark?.mode ?? "SINGLE_BENCHMARK"} · {t(`benchmarkSource.${primaryBenchmark?.source ?? "UNAVAILABLE"}` as `benchmarkSource.${string}`, primaryBenchmark?.source ?? "-")}
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">{t("capmSection.missingCard")}</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">{formatPct(primaryBenchmark?.missingRate)}</p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {primaryBenchmark?.availablePriceCount ?? 0}/{primaryBenchmark?.expectedTradingDayCount ?? 0}
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">{t("capmSection.appliedCard")}</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">
                            {(capm?.appliedAssetCount ?? 0) + (capm?.partialAssetCount ?? 0)}{t("marketAnalysis.appliedCountSuffix")}
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {t("capmSection.excludedCount", { count: capm?.excludedAssetCount ?? 0 })}
                          </p>
                        </div>
                        <div className="rounded-lg bg-surface border border-line p-2">
                          <p className="text-[11px] text-ink-4">{t("capmSection.avgWeightCard")}</p>
                          <p className="mt-1 text-xs font-mono font-bold text-ink">
                            {formatPct(typeof expectedPolicy?.averageCapmWeight === "number" ? expectedPolicy.averageCapmWeight : 0)}
                          </p>
                          <p className="mt-0.5 text-[11px] text-ink-4">
                            {t("capmSection.confidenceLabel", { value: formatPct(typeof expectedPolicy?.averageBlendConfidence === "number" ? expectedPolicy.averageBlendConfidence : 0) })}
                          </p>
                        </div>
                      </div>
                      {capmAssets.length ? (
                        <div className="mt-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <h5 className="text-xs font-bold text-ink">{t("capmSection.finalER")}</h5>
                            <p className="text-[11px] text-ink-4">
                              {t("capmSection.blendDesc")}
                            </p>
                          </div>
                          <div className="mt-2 overflow-x-auto">
                          <table className="w-full min-w-[900px] text-left text-[11px]">
                            <thead className="text-ink-4">
                              <tr>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.stock")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.benchmark")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.status")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.sample")}</th>
                                <th className="py-2 pr-3">β</th>
                                <th className="py-2 pr-3">{t("sclSection.tableCol.annualAlpha")}</th>
                                <th className="py-2 pr-3">R²</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.historicalER")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.capmER")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.finalER2")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.capmWeight")}</th>
                                <th className="py-2 pr-3">{t("capmSection.tableCol.weightReason")}</th>
                              </tr>
                            </thead>
                            <tbody>
                              {capmAssets.map((asset) => (
                                <tr key={`capm-${asset.stockCode}`} className="border-t border-line text-ink-3">
                                  <td className="py-2 pr-3 font-medium text-ink">{asset.companyName ?? asset.stockCode}</td>
                                  <td className="py-2 pr-3">{asset.benchmarkName ?? asset.benchmarkCode ?? "-"}</td>
                                  <td className="py-2 pr-3">{asset.status ? t(`capmAssetStatus.${asset.status}` as `capmAssetStatus.${string}`, asset.status) : "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.commonSampleSize ?? 0}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.beta?.toFixed(3) ?? "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.annualAlpha !== null && asset.annualAlpha !== undefined ? formatPct(asset.annualAlpha) : "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.rSquared?.toFixed(3) ?? "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.historicalExpectedReturn)}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{asset.capmExpectedReturn !== null && asset.capmExpectedReturn !== undefined ? formatPct(asset.capmExpectedReturn) : "-"}</td>
                                  <td className="py-2 pr-3 font-mono tabular text-ink">{formatPct(asset.blendedExpectedReturn)}</td>
                                  <td className="py-2 pr-3 font-mono tabular">{formatPct(asset.capmWeight)}</td>
                                  <td className="py-2 pr-3">{capmWeightReasonText(asset, t)}</td>
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
                              <h5 className="text-xs font-bold text-ink">{t("sclSection.title")}</h5>
                              <span className="text-[10px] text-ink-4">{t("sclSection.alphaNote")}</span>
                            </div>
                            {pdfExporting ? (
                              selectableSclSeries.length ? (
                                <div className="mt-2 flex flex-col gap-3">
                                  {selectableSclSeries.map((series) => renderSclChart(series))}
                                </div>
                              ) : (
                                <div className="mt-2 rounded-lg bg-bg-sunk border border-line p-3 text-[11px] text-ink-4">
                                  {t("sclSection.noData")}
                                </div>
                              )
                            ) : (
                              <>
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
                                      {t("sclSection.regressionDesc", { benchmark: activeSclSeries.benchmarkName ?? activeSclAsset?.benchmarkName ?? activeSclSeries.benchmarkCode ?? activeSclAsset?.benchmarkCode ?? t("capmSection.benchmarkCard") })}
                                    </p>
                                  </div>
                                  <div className="flex flex-wrap gap-2 text-[10px] font-mono tabular text-ink-4">
                                    <span>β {sclLine?.beta?.toFixed(3) ?? "-"}</span>
                                    <span>{t("sclSection.tableCol.dailyAlpha")} {sclLine?.dailyAlpha !== null && sclLine?.dailyAlpha !== undefined ? formatPct(sclLine.dailyAlpha, 3) : "-"}</span>
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
                                  <text x="336" y="158" textAnchor="end" className="fill-ink-4 text-[10px]">{t("sclSection.chartBenchmarkLabel")}</text>
                                  <text x="44" y="24" className="fill-ink-4 text-[10px]">{t("sclSection.chartAssetLabel")}</text>
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
                                        {t("sclSection.benchmarkHover", { value: formatPct(sclHoverMarket, 2) })}
                                      </text>
                                      <rect x="0" y={Math.min(146, Math.max(42, sclHover.y - 10))} width="62" height="20" rx="5" fill="#111827" opacity="0.92" />
                                      <text x="31" y={Math.min(160, Math.max(56, sclHover.y + 4))} textAnchor="middle" className="fill-white text-[9px] font-mono">
                                        {t("sclSection.stockHover", { value: formatPct(sclHoverAsset, 1) })}
                                      </text>
                                    </g>
                                  )}
                                </svg>
                                <div className="mt-1 flex flex-wrap gap-3 text-[10px] text-ink-4">
                                  <span className="text-accent"><DictionaryText text={t("sclSection.legend.points")} /></span>
                                  <span className="text-danger"><DictionaryText text={t("sclSection.legend.line")} /></span>
                                  <span><DictionaryText text={t("sclSection.legend.count", { count: Math.min(180, sclPoints.length) })} /></span>
                                </div>
                              </div>
                            ) : (
                              <div className="mt-2 rounded-lg bg-bg-sunk border border-line p-3 text-[11px] text-ink-4">
                                {t("sclSection.noData")}
                              </div>
                            )}
                              </>
                            )}
                            <div className="mt-2 overflow-x-auto">
                              <table className="w-full min-w-[560px] text-left text-[11px]">
                                <thead className="text-ink-4">
                                  <tr>
                                    <th className="py-2 pr-3">{t("sclSection.tableCol.stock")}</th>
                                    <th className="py-2 pr-3">β</th>
                                    <th className="py-2 pr-3">{t("sclSection.tableCol.dailyAlpha")}</th>
                                    <th className="py-2 pr-3">{t("sclSection.tableCol.annualAlpha")}</th>
                                    <th className="py-2 pr-3">R²</th>
                                    <th className="py-2 pr-3">{t("sclSection.tableCol.sample")}</th>
                                    <th className="py-2 pr-3">{t("sclSection.tableCol.status")}</th>
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
                                      <td className="py-2 pr-3">{asset.status ? t(`capmAssetStatus.${asset.status}` as `capmAssetStatus.${string}`, asset.status) : "-"}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                            <p className="mt-2 text-[11px] text-ink-4">
                              {t("sclSection.footnote")}
                            </p>
                          </div>
                          <div className="rounded-lg bg-surface border border-line p-3">
                            <div className="flex items-center justify-between gap-2">
                              <h5 className="text-xs font-bold text-ink">{t("smlSection.title")}</h5>
                              <span className="text-[10px] text-ink-4">{t("smlSection.axisNote")}</span>
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
                                      {group.benchmarkName ?? group.benchmarkCode ?? t("capmSection.benchmarkCard")}
                                    </button>
                                  );
                                })}
                              </div>
                            ) : null}
                            {activeSmlGroup ? (
                              <div className="mt-2 rounded-md bg-bg-sunk border border-line px-2 py-1 text-[10px] text-ink-4">
                                {(activeSmlGroup.benchmarkName ?? activeSmlGroup.benchmarkCode ?? t("capmSection.benchmarkCard"))} · {t(`benchmarkSource.${activeSmlGroup.source ?? "UNAVAILABLE"}` as `benchmarkSource.${string}`, activeSmlGroup.source ?? "-")} · {activeSmlGroup.availablePriceCount ?? 0} · {t("smlSection.missingRateDetail", { value: formatPct(activeSmlGroup.missingRate) })}
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
                              <text x="336" y="158" textAnchor="end" className="fill-ink-4 text-[10px]">{t("smlSection.chartBetaLabel")}</text>
                              <text x="44" y="24" className="fill-ink-4 text-[10px]">{t("smlSection.chartReturnLabel")}</text>
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
                                    {t("smlSection.betaHover", { value: smlHoverBeta.toFixed(2) })}
                                  </text>
                                  <rect x="0" y={Math.min(146, Math.max(42, smlHover.y - 10))} width="58" height="20" rx="5" fill="#111827" opacity="0.92" />
                                  <text x="29" y={Math.min(160, Math.max(56, smlHover.y + 4))} textAnchor="middle" className="fill-white text-[9px] font-mono">
                                    {t("smlSection.erHover", { value: formatPct(smlHoverReturn, 0) })}
                                  </text>
                                </g>
                              )}
                            </svg>
                            <div className="mt-2 overflow-x-auto">
                              <table className="w-full min-w-[520px] text-left text-[11px]">
                                <thead className="text-ink-4">
                                  <tr>
                                    <th className="py-2 pr-3">{t("smlSection.tableCol.stock")}</th>
                                    <th className="py-2 pr-3">β</th>
                                    <th className="py-2 pr-3">{t("smlSection.tableCol.capmER")}</th>
                                    <th className="py-2 pr-3">{t("smlSection.tableCol.historicalER")}</th>
                                    <th className="py-2 pr-3">{t("smlSection.tableCol.finalER")}</th>
                                    <th className="py-2 pr-3">{t("smlSection.tableCol.capmWeight")}</th>
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
                              {t("smlSection.footnote")}
                            </p>
                          </div>
                        </div>
                      ) : null}
                      <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                        <p className="text-ink">
                          {t("capmSection.capmWeightNote")}
                        </p>
                        <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-x-4 gap-y-1.5">
                          <p>{t("capmSection.capmWeightFactors.commonSample")}</p>
                          <p>{t("capmSection.capmWeightFactors.benchmarkSource")}</p>
                          <p>{t("capmSection.capmWeightFactors.coverage")}</p>
                          <p>{t("capmSection.capmWeightFactors.rSquared")}</p>
                          <p>{t("capmSection.capmWeightFactors.correlation")}</p>
                          <p>{t("capmSection.capmWeightFactors.volatility")}</p>
                          <p>{t("capmSection.capmWeightFactors.historicalWeight")}</p>
                        </div>
                      </div>
                    </div>
                  );
                })()}

                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[640px] text-left text-xs">
                    <thead className="text-ink-4">
                      <tr>
                        <th className="py-2 pr-3">{t("advanced.dataTable.col.stock")}</th>
                        <th className="py-2 pr-3">{t("advanced.dataTable.col.priceCount")}</th>
                        <th className="py-2 pr-3">{t("advanced.dataTable.col.missingRate")}</th>
                        <th className="py-2 pr-3">{t("advanced.dataTable.col.dataSource")}</th>
                        <th className="py-2 pr-3">{t("advanced.dataTable.col.cacheStatus")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {analysisResult.policy.dataQuality.priceSeries.map((series) => (
                        <tr key={series.stockCode} className="border-t border-line text-ink-3">
                          <td className="py-2 pr-3 font-medium text-ink">{series.companyName ?? series.stockCode}</td>
                          <td className="py-2 pr-3 font-mono tabular">{series.availablePriceCount}</td>
                          <td className="py-2 pr-3 font-mono tabular">{formatPct(series.missingRate)}</td>
                          <td className="py-2 pr-3">{t(`marketDataSource.${series.source ?? "UNAVAILABLE"}` as `marketDataSource.${string}`, series.source ?? "-")}</td>
                          <td className="py-2 pr-3">{t(`cacheStatus.${series.cacheStatus ?? "UNAVAILABLE"}` as `cacheStatus.${string}`, series.cacheStatus ?? "-")}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {analysisResult.advanced?.frontier?.length ? (
                  <div className="mt-5">
                    <div className="flex items-center justify-between gap-3">
                      <h4 className="text-sm font-bold text-ink">{t("frontier.title")}</h4>
                      <p className="text-xs text-ink-4">
                        {t("frontier.desc")}
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
                      const advancedPortfolioLabel = (type: string, label?: string) =>
                        t(`advancedPortfolioLabel.${type}` as `advancedPortfolioLabel.${string}`, label ?? type);
                      const advancedMarkerLabel = (type: string) =>
                        t(`advancedMarkerLabel.${type}` as `advancedMarkerLabel.${string}`, type);
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
                        <div className="mt-3 overflow-x-auto rounded-xl bg-bg-sunk border border-line p-3">
                          <svg
                            viewBox={`0 0 ${viewW} ${viewH}`}
                            className="h-[420px] min-w-[760px] w-full cursor-crosshair sm:h-[640px]"
                            role="img"
                            aria-label={t("frontier.ariaLabel")}
                            onMouseMove={handleFrontierHover}
                            onMouseLeave={() => setFrontierHover(null)}
                          >
                            <line x1={plotLeft} y1={plotBottom} x2={plotRight} y2={plotBottom} stroke="currentColor" className="text-line" />
                            <line x1={plotLeft} y1={plotBottom} x2={plotLeft} y2={plotTop} stroke="currentColor" className="text-line" />
                            <text x={plotRight} y={viewH - 24} textAnchor="end" className="fill-ink-4 text-[12px]">{t("frontier.volatilityAxis")}</text>
                            <text x={16} y={plotTop - 18} className="fill-ink-4 text-[12px]">{t("frontier.returnAxis")}</text>
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
                                  {activeRiskFree ? t("frontier.tooltipRiskFree") : activeMarker ? advancedPortfolioLabel(activeMarker.portfolio.type, activeMarker.portfolio.label) : t("frontier.tooltipCoord")}
                                </text>
                                <text x={tooltipX + 12} y={tooltipY + 38} className="fill-slate-300 text-[11px] font-mono">
                                  {activeRiskFree ? `σ ${formatPct(0)} · r_f ${formatPct(riskFreeRate)}` : `σ ${formatPct(hoverVol)} · E[R] ${formatPct(hoverReturn)}`}
                                </text>
                                <text x={tooltipX + 12} y={tooltipY + 57} className="fill-sky-300 text-[11px] font-mono">
                                  {activeRiskFree ? t("frontier.tooltipRfLabel") : `EF ${activeFrontier ? `${formatPct(activeFrontier.point.volatility)} / ${formatPct(graphReturn(activeFrontier.point))}` : "-"}`}
                                </text>
                                {activeRiskFree && (
                                  <text x={tooltipX + 12} y={tooltipY + 76} className="fill-slate-300 text-[11px]">
                                    {t("frontier.tooltipRfNote")}
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
                            <span>{t("frontier.legend.frontier")}</span>
	                            <span>{t("frontier.legend.riskFree")}</span>
	                            <span className="text-success">{t("frontier.legend.cal")}</span>
                            <span className="text-purple-600">{t("frontier.legend.indifference")}</span>
                            <span className="text-success">{t("frontier.legend.maxSharpe")}</span>
                            <span className="text-orange-600">{t("frontier.legend.riskAllocation")}</span>
                            <span className="text-danger">{t("frontier.legend.utilityOptimal")}</span>
                            <span className="text-purple-600">{t("frontier.legend.theoreticalUtility")}</span>
                            <span className="text-accent">{t("frontier.legend.minVol")}</span>
                            <span>{t("frontier.legend.current")}</span>
                          </div>
                          <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                            {t("frontier.efNote")}
                          </div>
	                          <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
	                            <p className="font-semibold text-ink">{t("frontier.markerBasisTitle")}</p>
	                            <div className="mt-1.5 grid grid-cols-1 lg:grid-cols-2 gap-x-4 gap-y-2">
	                              <p>
	                                {t("frontier.markerBasis.frontier")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.maxSharpe")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.riskAllocation")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.utilityOptimal")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.theoreticalUtility")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.minVol")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.indifference")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.current")}
	                              </p>
	                              <p>
	                                {t("frontier.markerBasis.cal")}
	                              </p>
	                            </div>
	                          </div>
                          {utilityValue !== null && (
                            <div className="mt-2 rounded-lg bg-surface border border-line px-3 py-2 text-[11px] text-ink-3">
                              {t("frontier.indifferenceCurveNote", { gamma: riskAversionGamma.toFixed(2) })}
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
                            <h5 className="text-sm font-bold text-ink">{t("frontier.efficiencyTitle")}</h5>
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
                                    {pdfExporting ? (
                                      <SvgPortfolioPieChart
                                        weights={portfolio.weights}
                                        label={`${advancedPortfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                                        className="w-20 h-20 flex-shrink-0"
                                      />
                                    ) : (
                                      <div
                                        className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                        style={portfolioPieStyle(portfolio.weights)}
                                        aria-label={`${advancedPortfolioLabel(portfolio.type, portfolio.label)} 구성비 파이차트`}
                                      />
                                    )}
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
                                                <span className="truncate text-ink-4">{t("efficiencySection.unrealizedReturn")}</span>
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
                                    <p className="text-xs font-bold text-ink">{t("theoreticalAdvanced.title")}</p>
                                    <p className="mt-1 text-[11px] leading-relaxed text-ink-4">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX"
                                        ? t("theoreticalAdvanced.cashMaxDesc")
                                        : t("theoreticalAdvanced.addCashDesc")}
                                    </p>
                                  </div>
                                  <div className="text-right">
                                    <p className="text-[10px] text-ink-4">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX" ? t("theoreticalAdvanced.cashMaxFundingLabel") : t("theoreticalAdvanced.addCashFundingLabel")}
                                    </p>
                                    <p className="font-mono tabular text-sm font-bold text-danger">
                                      {theoreticalUtility.constraintBinding === "CASH_MAX"
                                        ? t("theoreticalAdvanced.cashPrefix", { value: formatPct(1 - (theoreticalUtility.theoreticalRiskyAllocation ?? 1)) })
                                        : formatKRW(theoreticalUtility.additionalRequiredCash)}
                                    </p>
                                  </div>
                                </div>
                                <div className="mt-3 flex items-center gap-3">
                                  {pdfExporting ? (
                                    <SvgPortfolioPieChart
                                      weights={theoreticalUtility.weights}
                                      label="이론적 효용접점 구성비 파이차트"
                                      className="w-20 h-20 flex-shrink-0"
                                    />
                                  ) : (
                                    <div
                                      className="w-20 h-20 rounded-full border border-line flex-shrink-0"
                                      style={portfolioPieStyle(theoreticalUtility.weights)}
                                      aria-label="이론적 효용접점 구성비 파이차트"
                                    />
                                  )}
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
                                        <p>{t("theoreticalCard.riskyMultiplier")}</p>
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
