import { useEffect, useRef, useState } from "react";
import { usePdfExportReveal } from "../contexts/PdfExportContext";
import type { AnalysisResponse } from "../types/analysis";
import { formatKstDateTimeDisplay } from "../utils/kst";

type Snapshot = AnalysisResponse["metrics"]["marketSnapshot"];

type Props = {
  snapshot: Snapshot;
};

type MetricItem = {
  label: string;
  englishLabel?: string;
  value: number | null | undefined;
  kind: "ratio" | "multiple" | "amount" | "count";
};

type Section = {
  title: string;
  englishTitle?: string;
  color: string;
  items: MetricItem[];
};

const NEGATIVE_COLOR = "#dc2626";

const fmtValue = (value: number | null | undefined, kind: MetricItem["kind"]) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  if (kind === "ratio") return `${value.toFixed(2)}%`;
  if (kind === "multiple") return `${value.toFixed(2)}배`;
  if (kind === "amount") {
    if (abs >= 1e12) return `${(value / 1e12).toFixed(2)}조`;
    if (abs >= 1e8) return `${(value / 1e8).toFixed(0)}억`;
  }
  return value.toLocaleString("ko-KR");
};

const normalize = (item: MetricItem) => {
  const value = item.value;
  if (value == null || !Number.isFinite(value)) return null;
  const abs = Math.abs(value);

  switch (item.label) {
    case "PER":
      return Math.min(abs / 35, 1);
    case "PBR":
      return Math.min(abs / 6, 1);
    case "PSR":
      return Math.min(abs / 10, 1);
    case "시가총액":
      return Math.min(abs / 500_000_000_000_000, 1);
    case "ROE":
    case "ROA":
    case "영업이익률":
    case "순이익률":
      return Math.min(abs / 40, 1);
    case "부채비율":
      return Math.min(abs / 300, 1);
    case "유동비율":
    case "당좌비율":
      return Math.min(abs / 250, 1);
    case "이자보상배율":
      return Math.min(abs / 20, 1);
    case "매출 성장률":
    case "EPS 성장률":
      return Math.min(abs / 80, 1);
    case "잉여현금흐름":
      return Math.min(abs / 50_000_000_000_000, 1);
    case "EPS(TTM)":
      return Math.min(abs / 20_000, 1);
    case "BPS":
      return Math.min(abs / 200_000, 1);
    default:
      if (item.kind === "ratio") return Math.min(abs / 100, 1);
      if (item.kind === "multiple") return Math.min(abs / 30, 1);
      if (item.kind === "amount") return Math.min(abs / 1_000_000_000_000, 1);
      return Math.min(abs / 1_000_000, 1);
  }
};

const buildSections = (snapshot: Snapshot): Section[] => [
  {
    title: "밸류에이션",
    englishTitle: "Valuation",
    color: "#2563eb",
    items: [
      { label: "PER", value: snapshot.valuation?.per, kind: "multiple" },
      { label: "PBR", value: snapshot.valuation?.pbr, kind: "multiple" },
      { label: "PSR", value: snapshot.valuation?.psr, kind: "multiple" },
      { label: "시가총액", englishLabel: "Market Cap", value: snapshot.valuation?.marketCap, kind: "amount" },
    ],
  },
  {
    title: "수익성",
    englishTitle: "Profitability",
    color: "#16a34a",
    items: [
      { label: "ROE", value: snapshot.profitability?.roe, kind: "ratio" },
      { label: "ROA", value: snapshot.profitability?.roa, kind: "ratio" },
      { label: "영업이익률", englishLabel: "Operating Margin", value: snapshot.profitability?.operatingMargin, kind: "ratio" },
      { label: "순이익률", englishLabel: "Net Margin", value: snapshot.profitability?.netMargin, kind: "ratio" },
    ],
  },
  {
    title: "안정성",
    englishTitle: "Stability",
    color: "#d97706",
    items: [
      { label: "부채비율", englishLabel: "Debt Ratio", value: snapshot.stability?.debtRatio, kind: "ratio" },
      { label: "유동비율", englishLabel: "Current Ratio", value: snapshot.stability?.currentRatio, kind: "ratio" },
      { label: "당좌비율", englishLabel: "Quick Ratio", value: snapshot.stability?.quickRatio, kind: "ratio" },
      { label: "이자보상배율", englishLabel: "Interest Coverage", value: snapshot.stability?.interestCoverageRatio, kind: "multiple" },
    ],
  },
  {
    title: "성장성",
    englishTitle: "Growth",
    color: "#7c3aed",
    items: [
      { label: "매출 성장률", englishLabel: "Revenue Growth", value: snapshot.growth?.revenueGrowth, kind: "ratio" },
      { label: "EPS 성장률", englishLabel: "EPS Growth", value: snapshot.growth?.epsGrowth, kind: "ratio" },
      { label: "잉여현금흐름", englishLabel: "Free Cash Flow", value: snapshot.growth?.freeCashFlow, kind: "amount" },
    ],
  },
  {
    title: "주당 지표",
    englishTitle: "Per Share",
    color: "#0f766e",
    items: [
      { label: "EPS(TTM)", value: snapshot.perShare?.eps, kind: "count" },
      { label: "BPS", value: snapshot.perShare?.bps, kind: "count" },
    ],
  },
];

export default function MarketSnapshotBars({ snapshot }: Props) {
  const sections = buildSections(snapshot);
  const [hasAnimated, setHasAnimated] = useState(false);
  const forceReveal = usePdfExportReveal();
  const animated = hasAnimated || forceReveal;
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setHasAnimated(false);
  }, [snapshot]);

  useEffect(() => {
    const node = rootRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting) return;
        setHasAnimated(true);
        observer.disconnect();
      },
      {
        threshold: 0.2,
        rootMargin: "0px 0px -10% 0px",
      },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [snapshot]);

  return (
    <div ref={rootRef}>
      <div className="flex flex-col gap-1">
        <h3 className="text-base sm:text-lg font-semibold text-ink">투자지표 개요</h3>
        <p className="text-sm text-ink-3">
          밸류에이션 · 수익성 · 안정성 · 성장성 · 주당 지표
        </p>
        <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-ink-2">
          <div>기준일: {formatKstDateTimeDisplay(snapshot.asOf) || "-"}</div>
          <div>통화: {snapshot.currency ?? "-"}</div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 lg:grid-cols-2 gap-4">
        {sections.map((section, sectionIndex) => (
          <div
            key={section.title}
            className="rounded-2xl border border-line bg-bg-sunk px-4 py-4"
            style={{
              opacity: animated ? 1 : 0,
              transform: animated ? "translateY(0)" : "translateY(18px)",
              transitionProperty: "opacity, transform",
              transitionDuration: "700ms",
              transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
              transitionDelay: `${sectionIndex * 90}ms`,
            }}
          >
            <div className="flex items-center gap-2">
              <span
                className="inline-block h-3 w-3 rounded-full"
                style={{ backgroundColor: section.color }}
              />
              <h4 className="text-sm font-semibold text-ink">
                {section.title}
                {section.englishTitle ? ` (${section.englishTitle})` : ""}
              </h4>
            </div>

            <div className="mt-3 flex flex-col gap-3">
              {section.items.map((item, index) => {
                const ratio = normalize(item);
                const widthPercent = ratio == null ? 0 : Math.max(ratio * 100, 6);
                const isNegativeGrowth =
                  section.title === "성장성"
                  && (item.label === "매출 성장률" || item.label === "EPS 성장률")
                  && item.value != null
                  && item.value < 0;
                const isNegativeCashFlow =
                  section.title === "성장성"
                  && item.label === "잉여현금흐름"
                  && item.value != null
                  && item.value < 0;
                const fillColor = isNegativeGrowth || isNegativeCashFlow
                  ? NEGATIVE_COLOR
                  : section.color;
                return (
                  <div key={`${section.title}-${item.label}`}>
                    <div className="mb-1 flex items-center justify-between gap-3 text-sm">
                      <span className="text-ink-2">
                        {item.label}
                        {item.englishLabel ? ` (${item.englishLabel})` : ""}
                      </span>
                      <span className="font-medium text-ink">
                        {fmtValue(item.value, item.kind)}
                      </span>
                    </div>
                    <div className="h-2.5 w-full overflow-hidden rounded-full bg-line">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${animated ? widthPercent : 0}%`,
                          backgroundColor: fillColor,
                          opacity: ratio == null ? 0.2 : 0.95,
                          transitionProperty: "width, opacity",
                          transitionDuration: "850ms",
                          transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
                          transitionDelay: `${index * 70}ms`,
                        }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
