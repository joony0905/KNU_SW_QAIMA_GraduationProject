import { useEffect, useMemo, useRef, useState } from "react";
import { usePdfExportReveal } from "../contexts/PdfExportContext";

export type FinancialTimelinePeriod = "A" | "H" | "Q";

export type FinancialTimelineDatum = {
  label: string;
  revenue: number | null;
  operatingIncome: number | null;
  netIncome: number | null;
  operatingMargin: number | null;
};

type Props = {
  title?: string;
  subtitle?: string;
  period: FinancialTimelinePeriod;
  points: FinancialTimelineDatum[];
};

const COLORS = {
  revenue: "#dc2626",
  operatingIncome: "#2563eb",
  netIncome: "#16a34a",
  operatingMargin: "#d97706",
} as const;

const fmtAmount = (value: number | null | undefined) => {
  if (value == null) return "-";
  const abs = Math.abs(value);
  const sign = value < 0 ? "-" : "";
  if (abs >= 1e12) return `${sign}${(abs / 1e12).toFixed(1)}조`;
  if (abs >= 1e8) return `${sign}${(abs / 1e8).toFixed(0)}억`;
  return `${sign}${abs.toLocaleString("ko-KR")}`;
};

const fmtPercent = (value: number | null | undefined) => {
  if (value == null) return "-";
  return `${value.toFixed(1)}%`;
};

const periodLabel = (period: FinancialTimelinePeriod) => {
  switch (period) {
    case "Q":
      return "분기 기준";
    case "H":
      return "반기 기준";
    default:
      return "연간 기준";
  }
};

export default function FinancialTimelineChart({
  title = "재무 시계열",
  subtitle = "매출·영업이익·순이익·영업이익률",
  period,
  points,
}: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [hasAnimated, setHasAnimated] = useState(false);
  const forceReveal = usePdfExportReveal();
  const animated = hasAnimated || forceReveal;
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  useEffect(() => {
    setHasAnimated(false);
  }, [points, period]);

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
  }, [points, period]);

  if (!points.length) {
    return (
      <div>
        <h3 className="text-base sm:text-lg font-semibold text-ink">{title}</h3>
        <p className="text-sm text-ink-3 mt-1">{subtitle}</p>
        <p className="text-sm text-ink-3 mt-3">표시할 재무 시계열 데이터가 없습니다.</p>
      </div>
    );
  }

  const width = 760;
  const height = 320;
  const leftPad = 44;
  const rightPad = 48;
  const topPad = 24;
  const bottomPad = 46;
  const chartWidth = width - leftPad - rightPad;
  const chartHeight = height - topPad - bottomPad;
  const groupWidth = chartWidth / points.length;
  const barWidth = Math.max(10, Math.min(20, groupWidth / 5));
  const groupCenterOffset = groupWidth / 2;

  const amountValues = points.flatMap((point) => [
    point.revenue,
    point.operatingIncome,
    point.netIncome,
  ]).filter((v): v is number => typeof v === "number" && Number.isFinite(v));

  const amountMin = Math.min(0, ...amountValues);
  const amountMax = Math.max(0, ...amountValues);
  const amountRange = amountMax - amountMin || 1;
  const amountToY = (value: number) =>
    topPad + ((amountMax - value) / amountRange) * chartHeight;
  const zeroY = amountToY(0);

  const marginValues = points
    .map((point) => point.operatingMargin)
    .filter((v): v is number => typeof v === "number" && Number.isFinite(v));
  const marginMinBase = Math.min(0, ...marginValues);
  const marginMaxBase = Math.max(0, ...marginValues);
  const marginPadding = (marginMaxBase - marginMinBase || 1) * 0.15;
  const marginMin = marginMinBase - marginPadding;
  const marginMax = marginMaxBase + marginPadding;
  const marginRange = marginMax - marginMin || 1;
  const marginToY = (value: number) =>
    topPad + ((marginMax - value) / marginRange) * chartHeight;

  const linePoints = points
    .map((point, index) => {
      if (point.operatingMargin == null || !Number.isFinite(point.operatingMargin)) {
        return null;
      }
      const x = leftPad + index * groupWidth + groupCenterOffset;
      const y = marginToY(point.operatingMargin);
      return `${x},${y}`;
    })
    .filter((value): value is string => value !== null)
    .join(" ");
  const linePointPairs = linePoints
    .split(" ")
    .filter(Boolean)
    .map((pair) => pair.split(",").map(Number))
    .filter((pair) => pair.length === 2 && pair.every((v) => Number.isFinite(v)));
  const lineLength = useMemo(() => {
    if (linePointPairs.length < 2) return 0;
    let total = 0;
    for (let i = 1; i < linePointPairs.length; i += 1) {
      const [x1, y1] = linePointPairs[i - 1];
      const [x2, y2] = linePointPairs[i];
      total += Math.hypot(x2 - x1, y2 - y1);
    }
    return total;
  }, [linePoints]);

  const legendItems = [
    { color: COLORS.revenue, label: "매출" },
    { color: COLORS.operatingIncome, label: "영업이익" },
    { color: COLORS.netIncome, label: "순이익" },
    { color: COLORS.operatingMargin, label: "영업이익률" },
  ];
  const hoveredPoint = hoveredIndex == null ? null : points[hoveredIndex] ?? null;

  return (
    <div
      ref={rootRef}
      style={{
        opacity: animated ? 1 : 0,
        transform: animated ? "translateY(0)" : "translateY(18px)",
        transitionProperty: "opacity, transform",
        transitionDuration: "700ms",
        transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
      }}
    >
      <div className="flex flex-col gap-1">
        <h3 className="text-base sm:text-lg font-semibold text-ink">{title}</h3>
        <p className="text-sm text-ink-3">
          {subtitle} · {periodLabel(period)}
        </p>
      </div>

      <div className="mt-3 overflow-x-auto rounded-2xl border border-line bg-bg-sunk px-3 py-4">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="min-w-[720px] w-full"
          role="img"
          aria-label={`${title} 차트`}
          onMouseLeave={() => setHoveredIndex(null)}
        >
          <line
            x1={leftPad}
            y1={zeroY}
            x2={width - rightPad}
            y2={zeroY}
            style={{ stroke: "rgb(var(--color-line-strong))" }}
            strokeDasharray="4 4"
          />

          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const value = amountMax - amountRange * ratio;
            const y = amountToY(value);
            return (
              <g key={`grid-${ratio}`}>
                <line
                  x1={leftPad}
                  y1={y}
                  x2={width - rightPad}
                  y2={y}
                  style={{ stroke: "rgb(var(--color-line))" }}
                />
                <text
                  x={leftPad - 8}
                  y={y + 4}
                  textAnchor="end"
                  fontSize="10"
                  style={{ fill: "rgb(var(--color-ink-3))" }}
                >
                  {fmtAmount(value)}
                </text>
              </g>
            );
          })}

          {[0, 0.5, 1].map((ratio) => {
            const value = marginMax - marginRange * ratio;
            const y = marginToY(value);
            return (
              <text
                key={`margin-${ratio}`}
                x={width - rightPad + 8}
                y={y + 4}
                fontSize="10"
                style={{ fill: "rgb(var(--color-warn))" }}
              >
                {fmtPercent(value)}
              </text>
            );
          })}

          {hoveredPoint && (
            <g pointerEvents="none">
              <line
                x1={leftPad + hoveredIndex! * groupWidth + groupCenterOffset}
                x2={leftPad + hoveredIndex! * groupWidth + groupCenterOffset}
                y1={topPad}
                y2={height - bottomPad}
                style={{ stroke: "rgb(var(--color-ink))" }}
                strokeDasharray="6 6"
              />
              <text
                x={leftPad + hoveredIndex! * groupWidth + groupCenterOffset}
                y={height - 16}
                textAnchor="middle"
                fontSize="11"
                style={{ fill: "rgb(var(--color-ink))" }}
                fontWeight="600"
              >
                {hoveredPoint.label}
              </text>
            </g>
          )}

          {points.map((point, index) => {
            const groupX = leftPad + index * groupWidth;
            const centerX = groupX + groupCenterOffset;
            const bars = [
              { value: point.revenue, color: COLORS.revenue, offset: -barWidth - 4 },
              { value: point.operatingIncome, color: COLORS.operatingIncome, offset: 0 },
              { value: point.netIncome, color: COLORS.netIncome, offset: barWidth + 4 },
            ];

            return (
              <g key={point.label}>
                {bars.map((bar) => {
                  if (bar.value == null || !Number.isFinite(bar.value)) return null;
                  const y = amountToY(bar.value);
                  const h = Math.abs(zeroY - y);
                  const rectY = Math.min(y, zeroY);
                  return (
                    <rect
                      key={`${point.label}-${bar.color}`}
                      x={centerX + bar.offset - barWidth / 2}
                      y={animated ? rectY : zeroY}
                      width={barWidth}
                      height={animated ? Math.max(h, 1) : 0}
                      rx="3"
                      fill={bar.color}
                      opacity="0.9"
                      style={{
                        transitionProperty: "y, height",
                        transitionDuration: "820ms",
                        transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
                        transitionDelay: `${index * 70}ms`,
                      }}
                    />
                  );
                })}

                {point.operatingMargin != null && Number.isFinite(point.operatingMargin) && (
                  <circle
                    cx={centerX}
                    cy={marginToY(point.operatingMargin)}
                    r="4"
                    fill={COLORS.operatingMargin}
                    stroke="rgb(var(--color-surface))"
                    strokeWidth="1.5"
                    style={{
                      opacity: animated ? 1 : 0,
                      transform: animated ? "scale(1)" : "scale(0.4)",
                      transformOrigin: `${centerX}px ${marginToY(point.operatingMargin)}px`,
                      transitionProperty: "opacity, transform",
                      transitionDuration: "400ms",
                      transitionTimingFunction: "ease-out",
                      transitionDelay: `${300 + index * 55}ms`,
                    }}
                  />
                )}

                <text
                  x={centerX}
                  y={height - 16}
                  textAnchor="middle"
                  fontSize="10"
                  style={{ fill: "rgb(var(--color-ink-3))" }}
                >
                  {point.label}
                </text>
                <rect
                  x={groupX}
                  y={topPad}
                  width={groupWidth}
                  height={chartHeight}
                  fill="transparent"
                  onMouseEnter={() => setHoveredIndex(index)}
                  onFocus={() => setHoveredIndex(index)}
                />
              </g>
            );
          })}

          {linePoints && (
            <polyline
              fill="none"
              stroke={COLORS.operatingMargin}
              strokeWidth="2.5"
              points={linePoints}
              strokeLinejoin="round"
              strokeLinecap="round"
              strokeDasharray={lineLength || undefined}
              strokeDashoffset={animated ? 0 : lineLength || 0}
              style={{
                transitionProperty: "stroke-dashoffset",
                transitionDuration: "1100ms",
                transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
                transitionDelay: "220ms",
              }}
            />
          )}
        </svg>
      </div>

      <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-2">
        {legendItems.map((item) => (
          <div key={item.label} className="flex items-center gap-2">
            <span
              className="inline-block h-3 w-3 rounded-full"
              style={{ backgroundColor: item.color }}
            />
            <span>{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
