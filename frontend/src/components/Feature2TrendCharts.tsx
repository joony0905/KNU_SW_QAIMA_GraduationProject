import { useMemo, useState } from "react";
import type { Feature2TrendSeries } from "../types/feature2";

type TrendColor = "zinc" | "rose" | "blue" | "emerald" | "amber" | "violet";

const colorMap: Record<TrendColor, string> = {
  zinc: "#52525b",
  rose: "#e11d48",
  blue: "#2563eb",
  emerald: "#059669",
  amber: "#d97706",
  violet: "#7c3aed",
};

const defaultColors: TrendColor[] = ["blue", "rose", "emerald", "amber", "violet", "zinc"];

const fmtValue = (value: number | null | undefined, unit?: string | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const digits = unit === "원" ? 2 : 2;
  return `${value.toLocaleString("ko-KR", { maximumFractionDigits: digits })}${unit ?? ""}`;
};

const fmtDelta = (value: number | null | undefined, unit?: string | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)}${unit ?? ""}`;
};

const toValidPoints = (series: Feature2TrendSeries) =>
  (series.points ?? []).filter((point) => Number.isFinite(point.value));

export function Sparkline({
  series,
  color = "blue",
}: {
  series: Feature2TrendSeries;
  color?: TrendColor;
}) {
  const points = toValidPoints(series);
  const path = useMemo(() => {
    if (points.length < 2) return "";
    const values = points.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const width = 110;
    const height = 32;
    return points
      .map((point, index) => {
        const x = (width * index) / Math.max(points.length - 1, 1);
        const y = ((max - point.value) / range) * height;
        return `${index === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(" ");
  }, [points]);

  if (!path) {
    return <div className="h-8 w-[110px] rounded bg-bg-sunk" />;
  }

  return (
    <svg viewBox="0 0 110 32" className="h-8 w-[110px]" aria-hidden="true">
      <path
        d={path}
        fill="none"
        stroke={colorMap[color]}
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function MiniTrendRow({
  series,
  color = "blue",
}: {
  series: Feature2TrendSeries;
  color?: TrendColor;
}) {
  const points = toValidPoints(series);
  const first = points[0] ?? null;
  const latest = points[points.length - 1] ?? null;
  const delta = first && latest ? latest.value - first.value : null;
  const deltaClass = delta == null ? "text-ink-3" : delta > 0 ? "text-rise" : delta < 0 ? "text-fall" : "text-ink-3";

  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-line bg-surface px-3 py-2">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-ink">{series.label}</p>
        <p className="text-[11px] text-ink-3">
          {latest ? fmtValue(latest.value, series.unit) : "-"}
          <span className={`ml-2 font-semibold ${deltaClass}`}>{fmtDelta(delta, series.unit)}</span>
        </p>
      </div>
      <Sparkline series={series} color={color} />
    </div>
  );
}

export function MultiLineTrendChart({
  series,
  height = 220,
}: {
  series: Feature2TrendSeries[];
  height?: number;
}) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const validSeries = useMemo(
    () => series
      .map((item) => ({ ...item, points: toValidPoints(item) }))
      .filter((item) => item.points.length > 1),
    [series],
  );

  const chart = useMemo(() => {
    if (validSeries.length === 0) return null;
    const width = 760;
    const paddingX = 34;
    const paddingTop = 18;
    const paddingBottom = 36;
    const values = validSeries.flatMap((item) => item.points.map((point) => point.value));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const maxLength = Math.max(...validSeries.map((item) => item.points.length));
    const innerWidth = width - paddingX * 2;
    const innerHeight = height - paddingTop - paddingBottom;
    const toX = (index: number) => paddingX + (innerWidth * index) / Math.max(maxLength - 1, 1);
    const toY = (value: number) => paddingTop + ((max - value) / range) * innerHeight;
    return {
      width,
      paddingX,
      paddingTop,
      paddingBottom,
      tickValues: [max, max - range / 2, min],
      maxLength,
      toX,
      toY,
    };
  }, [height, validSeries]);

  if (!chart) {
    return (
      <div className="rounded-lg border border-line bg-bg-sunk px-4 py-8 text-sm text-ink-3">
        추세 데이터가 없습니다.
      </div>
    );
  }

  const labels = [0, Math.floor((chart.maxLength - 1) / 2), chart.maxLength - 1]
    .filter((index, position, arr) => arr.indexOf(index) === position);
  const firstSeries = validSeries[0];

  return (
    <div className="rounded-lg border border-line bg-bg-sunk px-3 py-3">
      <div className="mb-3 flex flex-wrap gap-2">
        {validSeries.map((item, index) => (
          <span key={item.key} className="inline-flex items-center gap-1 text-[11px] font-medium text-ink-3">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: colorMap[defaultColors[index % defaultColors.length]] }} />
            {item.label}
          </span>
        ))}
      </div>
      <div className="overflow-hidden sm:overflow-x-auto">
        <svg
          viewBox={`0 0 ${chart.width} ${height}`}
          className="w-full min-w-0 sm:min-w-[620px]"
          onMouseLeave={() => setHoveredIndex(null)}
        >
          {chart.tickValues.map((tick) => (
            <g key={tick}>
              <line
                x1={chart.paddingX}
                x2={chart.width - chart.paddingX}
                y1={chart.toY(tick)}
                y2={chart.toY(tick)}
                stroke="rgb(var(--color-line))"
                strokeDasharray="4 4"
              />
              <text x={2} y={chart.toY(tick) + 4} fontSize="11" fill="rgb(var(--color-ink-3))">
                {fmtValue(tick, validSeries[0]?.unit)}
              </text>
            </g>
          ))}

          {validSeries.map((item, seriesIndex) => {
            const path = item.points
              .map((point, index) => `${index === 0 ? "M" : "L"}${chart.toX(index).toFixed(2)},${chart.toY(point.value).toFixed(2)}`)
              .join(" ");
            return (
              <path
                key={item.key}
                d={path}
                fill="none"
                stroke={colorMap[defaultColors[seriesIndex % defaultColors.length]]}
                strokeWidth="2.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            );
          })}

          {hoveredIndex != null && (
            <line
              x1={chart.toX(hoveredIndex)}
              x2={chart.toX(hoveredIndex)}
              y1={chart.paddingTop}
              y2={height - chart.paddingBottom}
              stroke="rgb(var(--color-ink))"
              strokeDasharray="6 6"
            />
          )}

          {labels.map((index) => (
            <text
              key={`label-${index}`}
              x={chart.toX(index)}
              y={height - 10}
              textAnchor="middle"
              fontSize="11"
              fill="rgb(var(--color-ink-3))"
            >
              {firstSeries?.points[index]?.date ?? ""}
            </text>
          ))}

          {Array.from({ length: chart.maxLength }).map((_, index) => {
            const prev = index === 0 ? chart.paddingX : (chart.toX(index - 1) + chart.toX(index)) / 2;
            const next = index === chart.maxLength - 1 ? chart.width - chart.paddingX : (chart.toX(index) + chart.toX(index + 1)) / 2;
            return (
              <rect
                key={`hover-${index}`}
                x={prev}
                y={chart.paddingTop}
                width={Math.max(next - prev, 1)}
                height={height - chart.paddingTop - chart.paddingBottom}
                fill="transparent"
                onMouseEnter={() => setHoveredIndex(index)}
                onFocus={() => setHoveredIndex(index)}
              />
            );
          })}
        </svg>
      </div>
    </div>
  );
}
