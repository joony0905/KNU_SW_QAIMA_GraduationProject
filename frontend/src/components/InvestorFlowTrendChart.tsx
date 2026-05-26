import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { InvestorFlowPoint } from "../types/feature2";

const isEnglish = (language?: string | null) => (language ?? "").toLowerCase().startsWith("en");

const formatValue = (value?: number | null, language?: string | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  if (isEnglish(language)) {
    if (abs >= 100_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}T KRW`;
    if (abs >= 10_000) return `${sign}${(abs / 1_000).toFixed(1)}B KRW`;
    return `${sign}${Math.round(abs).toLocaleString("en-US")}M KRW`;
  }
  if (abs >= 100_000_000) return `${sign}${(abs / 100_000_000).toFixed(1)}조`;
  if (abs >= 10_000) return `${sign}${(abs / 10_000).toFixed(1)}억`;
  return `${sign}${Math.round(abs).toLocaleString("ko-KR")}백만`;
};

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  return value.slice(5, 10).replace("-", ".");
};

const toNum = (value?: number | null) => (value == null || !Number.isFinite(value) ? 0 : value);

const chartThemeClass = [
  "[--flow-panel-bg:#fafafa]",
  "[--flow-panel-border:#e4e4e7]",
  "[--flow-muted:#71717a]",
  "[--flow-text:#3f3f46]",
  "[--flow-axis:#a1a1aa]",
  "[--flow-grid:#e4e4e7]",
  "[--flow-guide:#18181b]",
  "[--flow-foreign-pos:#f43f5e]",
  "[--flow-foreign-neg:#60a5fa]",
  "[--flow-institution-pos:#0284c7]",
  "[--flow-institution-neg:#93c5fd]",
  "dark:[--flow-panel-bg:rgb(var(--color-bg-sunk))]",
  "dark:[--flow-panel-border:rgb(var(--color-line))]",
  "dark:[--flow-muted:rgb(var(--color-ink-3))]",
  "dark:[--flow-text:rgb(var(--color-ink-2))]",
  "dark:[--flow-axis:rgb(var(--color-line-strong))]",
  "dark:[--flow-grid:rgb(var(--color-line))]",
  "dark:[--flow-guide:rgb(var(--color-ink-2))]",
  "dark:[--flow-foreign-pos:rgb(var(--color-rise))]",
  "dark:[--flow-foreign-neg:rgb(var(--color-fall))]",
  "dark:[--flow-institution-pos:rgb(var(--color-accent))]",
  "dark:[--flow-institution-neg:rgb(var(--color-fall-soft))]",
].join(" ");

export default function InvestorFlowTrendChart({
  points,
  height = 220,
}: {
  points: InvestorFlowPoint[];
  height?: number;
}) {
  const { t, i18n } = useTranslation("analysisPanel");
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const chart = useMemo(() => {
    const rows = (points ?? []).filter((point) => point?.tradeDate);
    if (rows.length === 0) return null;

    const width = 760;
    const paddingX = 42;
    const paddingTop = 18;
    const paddingBottom = 34;
    const innerWidth = width - paddingX * 2;
    const innerHeight = height - paddingTop - paddingBottom;
    const maxAbs = Math.max(
      1,
      ...rows.flatMap((point) => [
        Math.abs(toNum(point.foreignNetBuyValueMillion)),
        Math.abs(toNum(point.institutionNetBuyValueMillion)),
      ]),
    );
    const zeroY = paddingTop + innerHeight / 2;
    const scale = (innerHeight / 2) / maxAbs;
    const step = innerWidth / Math.max(rows.length, 1);
    const barWidth = Math.max(4, Math.min(16, step * 0.28));
    const toX = (index: number) => paddingX + step * index + step / 2;
    const toY = (value: number) => zeroY - value * scale;
    return {
      rows,
      width,
      paddingX,
      paddingTop,
      paddingBottom,
      innerHeight,
      zeroY,
      maxAbs,
      barWidth,
      toX,
      toY,
    };
  }, [height, points]);

  if (!chart) {
    return (
      <div className={`rounded-lg border px-4 py-8 text-center text-sm ${chartThemeClass} border-[var(--flow-panel-border)] bg-[var(--flow-panel-bg)] text-[var(--flow-muted)]`}>
        {t("investorFlowChart.empty")}
      </div>
    );
  }

  const labels = [0, Math.floor((chart.rows.length - 1) / 2), chart.rows.length - 1]
    .filter((index, position, arr) => index >= 0 && arr.indexOf(index) === position);
  const hovered = hoveredIndex == null ? null : chart.rows[hoveredIndex] ?? null;

  return (
    <div className={`rounded-lg border px-3 py-3 ${chartThemeClass} border-[var(--flow-panel-border)] bg-[var(--flow-panel-bg)]`}>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-3 text-[11px] font-medium text-[var(--flow-muted)]">
          <span className="inline-flex items-center gap-1">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: "var(--flow-foreign-pos)" }} />
            {t("investorFlowChart.foreign")}
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: "var(--flow-institution-pos)" }} />
            {t("investorFlowChart.institution")}
          </span>
        </div>
        {hovered && (
          <span className="text-[11px] font-semibold text-[var(--flow-text)]">
            {t("investorFlowChart.hover", {
              date: formatDate(hovered.tradeDate),
              foreign: formatValue(hovered.foreignNetBuyValueMillion, i18n.language),
              institution: formatValue(hovered.institutionNetBuyValueMillion, i18n.language),
            })}
          </span>
        )}
      </div>
      <div className="overflow-hidden sm:overflow-x-auto">
        <svg
          viewBox={`0 0 ${chart.width} ${height}`}
          className="w-full min-w-0 sm:min-w-[620px]"
          onMouseLeave={() => setHoveredIndex(null)}
        >
          <line
            x1={chart.paddingX}
            x2={chart.width - chart.paddingX}
            y1={chart.zeroY}
            y2={chart.zeroY}
            stroke="var(--flow-axis)"
            strokeWidth="1"
          />
          {[-chart.maxAbs, chart.maxAbs].map((tick) => (
            <g key={tick}>
              <line
                x1={chart.paddingX}
                x2={chart.width - chart.paddingX}
                y1={chart.toY(tick)}
                y2={chart.toY(tick)}
                stroke="var(--flow-grid)"
                strokeDasharray="4 4"
              />
              <text x={2} y={chart.toY(tick) + 4} fontSize="11" fill="var(--flow-muted)">
                {formatValue(tick, i18n.language)}
              </text>
            </g>
          ))}
          {chart.rows.map((point, index) => {
            const x = chart.toX(index);
            const foreign = toNum(point.foreignNetBuyValueMillion);
            const institution = toNum(point.institutionNetBuyValueMillion);
            const foreignY = chart.toY(foreign);
            const institutionY = chart.toY(institution);
            return (
              <g key={`${point.tradeDate}-${index}`}>
                <rect
                  x={x - chart.barWidth - 1}
                  y={Math.min(chart.zeroY, foreignY)}
                  width={chart.barWidth}
                  height={Math.max(1, Math.abs(chart.zeroY - foreignY))}
                  fill={foreign >= 0 ? "var(--flow-foreign-pos)" : "var(--flow-foreign-neg)"}
                  opacity="0.9"
                />
                <rect
                  x={x + 1}
                  y={Math.min(chart.zeroY, institutionY)}
                  width={chart.barWidth}
                  height={Math.max(1, Math.abs(chart.zeroY - institutionY))}
                  fill={institution >= 0 ? "var(--flow-institution-pos)" : "var(--flow-institution-neg)"}
                  opacity="0.9"
                />
                <rect
                  x={x - chart.barWidth - 3}
                  y={chart.paddingTop}
                  width={chart.barWidth * 2 + 6}
                  height={chart.innerHeight}
                  fill="transparent"
                  onMouseEnter={() => setHoveredIndex(index)}
                />
              </g>
            );
          })}
          {hoveredIndex != null && (
            <line
              x1={chart.toX(hoveredIndex)}
              x2={chart.toX(hoveredIndex)}
              y1={chart.paddingTop}
              y2={height - chart.paddingBottom}
              stroke="var(--flow-guide)"
              strokeDasharray="6 6"
            />
          )}
          {labels.map((index) => (
            <text
              key={`label-${index}`}
              x={chart.toX(index)}
              y={height - 10}
              fontSize="11"
              fill="var(--flow-muted)"
              textAnchor="middle"
            >
              {formatDate(chart.rows[index]?.tradeDate)}
            </text>
          ))}
        </svg>
      </div>
    </div>
  );
}
