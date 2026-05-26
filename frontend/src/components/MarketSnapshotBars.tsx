import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { usePdfExportReveal } from "../contexts/PdfExportContext";
import type { AnalysisResponse } from "../types/analysis";
import { formatKstDateTimeDisplay } from "../utils/kst";

type Snapshot = AnalysisResponse["metrics"]["marketSnapshot"];

type Props = {
  snapshot: Snapshot;
};

type MetricItem = {
  key: string;
  label: string;
  value: number | null | undefined;
  kind: "ratio" | "multiple" | "amount" | "count";
};

type Section = {
  sectionKey: string;
  title: string;
  color: string;
  items: MetricItem[];
};

const NEGATIVE_COLOR = "#dc2626";

const fmtValue = (
  value: number | null | undefined,
  kind: MetricItem["kind"],
  t: TFunction<"marketSnapshot">,
) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  if (kind === "ratio") return `${value.toFixed(2)}%`;
  if (kind === "multiple") return `${value.toFixed(2)}${t("bars.units.multiple")}`;
  if (kind === "amount") {
    if (abs >= 1e12) return `${(value / 1e12).toFixed(2)}${t("bars.units.trillion")}`;
    if (abs >= 1e8) return `${(value / 1e8).toFixed(0)}${t("bars.units.hundredMillion")}`;
  }
  return value.toLocaleString("ko-KR");
};

const normalize = (item: MetricItem) => {
  const value = item.value;
  if (value == null || !Number.isFinite(value)) return null;
  const abs = Math.abs(value);

  switch (item.key) {
    case "PER": return Math.min(abs / 35, 1);
    case "PBR": return Math.min(abs / 6, 1);
    case "PSR": return Math.min(abs / 10, 1);
    case "marketCap": return Math.min(abs / 500_000_000_000_000, 1);
    case "ROE":
    case "ROA":
    case "operatingMargin":
    case "netMargin":
      return Math.min(abs / 40, 1);
    case "debtRatio": return Math.min(abs / 300, 1);
    case "currentRatio":
    case "quickRatio":
      return Math.min(abs / 250, 1);
    case "interestCoverage": return Math.min(abs / 20, 1);
    case "revenueGrowth":
    case "epsGrowth":
      return Math.min(abs / 80, 1);
    case "freeCashFlow": return Math.min(abs / 50_000_000_000_000, 1);
    case "EPS(TTM)": return Math.min(abs / 20_000, 1);
    case "BPS": return Math.min(abs / 200_000, 1);
    default:
      if (item.kind === "ratio") return Math.min(abs / 100, 1);
      if (item.kind === "multiple") return Math.min(abs / 30, 1);
      if (item.kind === "amount") return Math.min(abs / 1_000_000_000_000, 1);
      return Math.min(abs / 1_000_000, 1);
  }
};

const buildSections = (snapshot: Snapshot, t: TFunction<"marketSnapshot">): Section[] => [
  {
    sectionKey: "valuation",
    title: t("bars.sections.valuation"),
    color: "#2563eb",
    items: [
      { key: "PER", label: "PER", value: snapshot.valuation?.per, kind: "multiple" },
      { key: "PBR", label: "PBR", value: snapshot.valuation?.pbr, kind: "multiple" },
      { key: "PSR", label: "PSR", value: snapshot.valuation?.psr, kind: "multiple" },
      { key: "marketCap", label: t("bars.items.marketCap"), value: snapshot.valuation?.marketCap, kind: "amount" },
    ],
  },
  {
    sectionKey: "profitability",
    title: t("bars.sections.profitability"),
    color: "#16a34a",
    items: [
      { key: "ROE", label: "ROE", value: snapshot.profitability?.roe, kind: "ratio" },
      { key: "ROA", label: "ROA", value: snapshot.profitability?.roa, kind: "ratio" },
      { key: "operatingMargin", label: t("bars.items.operatingMargin"), value: snapshot.profitability?.operatingMargin, kind: "ratio" },
      { key: "netMargin", label: t("bars.items.netMargin"), value: snapshot.profitability?.netMargin, kind: "ratio" },
    ],
  },
  {
    sectionKey: "stability",
    title: t("bars.sections.stability"),
    color: "#d97706",
    items: [
      { key: "debtRatio", label: t("bars.items.debtRatio"), value: snapshot.stability?.debtRatio, kind: "ratio" },
      { key: "currentRatio", label: t("bars.items.currentRatio"), value: snapshot.stability?.currentRatio, kind: "ratio" },
      { key: "quickRatio", label: t("bars.items.quickRatio"), value: snapshot.stability?.quickRatio, kind: "ratio" },
      { key: "interestCoverage", label: t("bars.items.interestCoverage"), value: snapshot.stability?.interestCoverageRatio, kind: "multiple" },
    ],
  },
  {
    sectionKey: "growth",
    title: t("bars.sections.growth"),
    color: "#7c3aed",
    items: [
      { key: "revenueGrowth", label: t("bars.items.revenueGrowth"), value: snapshot.growth?.revenueGrowth, kind: "ratio" },
      { key: "epsGrowth", label: t("bars.items.epsGrowth"), value: snapshot.growth?.epsGrowth, kind: "ratio" },
      { key: "freeCashFlow", label: t("bars.items.freeCashFlow"), value: snapshot.growth?.freeCashFlow, kind: "amount" },
    ],
  },
  {
    sectionKey: "perShare",
    title: t("bars.sections.perShare"),
    color: "#0f766e",
    items: [
      { key: "EPS(TTM)", label: "EPS(TTM)", value: snapshot.perShare?.eps, kind: "count" },
      { key: "BPS", label: "BPS", value: snapshot.perShare?.bps, kind: "count" },
    ],
  },
];

export default function MarketSnapshotBars({ snapshot }: Props) {
  const { t } = useTranslation("marketSnapshot");
  const sections = buildSections(snapshot, t);
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
        <h3 className="text-base sm:text-lg font-semibold text-ink">{t("bars.title")}</h3>
        <p className="text-sm text-ink-3">{t("bars.subtitle")}</p>
        <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-ink-2">
          <div>{t("bars.asOf")} {formatKstDateTimeDisplay(snapshot.asOf) || "-"}</div>
          <div>{t("bars.currency")} {snapshot.currency ?? "-"}</div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 lg:grid-cols-2 gap-4">
        {sections.map((section, sectionIndex) => (
          <div
            key={section.sectionKey}
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
              <h4 className="text-sm font-semibold text-ink">{section.title}</h4>
            </div>

            <div className="mt-3 flex flex-col gap-3">
              {section.items.map((item, index) => {
                const ratio = normalize(item);
                const widthPercent = ratio == null ? 0 : Math.max(ratio * 100, 6);
                const isNegativeGrowth =
                  section.sectionKey === "growth"
                  && (item.key === "revenueGrowth" || item.key === "epsGrowth")
                  && item.value != null
                  && item.value < 0;
                const isNegativeCashFlow =
                  section.sectionKey === "growth"
                  && item.key === "freeCashFlow"
                  && item.value != null
                  && item.value < 0;
                const fillColor = isNegativeGrowth || isNegativeCashFlow
                  ? NEGATIVE_COLOR
                  : section.color;
                return (
                  <div key={`${section.sectionKey}-${item.key}`}>
                    <div className="mb-1 flex items-center justify-between gap-3 text-sm">
                      <span className="text-ink-2">{item.label}</span>
                      <span className="font-medium text-ink">
                        {fmtValue(item.value, item.kind, t)}
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
