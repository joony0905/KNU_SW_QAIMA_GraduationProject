import { useMemo, useState } from "react";
import type { ShortSellingSeriesPoint } from "../types/feature2";

type Props = {
  points: ShortSellingSeriesPoint[];
};

const WIDTH = 760;
const HEIGHT = 260;
const PADDING_X = 28;
const PADDING_TOP = 18;
const PADDING_BOTTOM = 34;
const VOLUME_COLOR = "#2563eb";
const AMOUNT_COLOR = "#d97706";
const AMOUNT_FILL = "rgba(217, 119, 6, 0.18)";

const fmtPct = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
};

export default function ShortSellingTrendChart({ points }: Props) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const validPoints = useMemo(
    () => points.filter((point) => point.shortVolumeRatio != null || point.shortAmountRatio != null),
    [points],
  );

  const chart = useMemo(() => {
    if (validPoints.length === 0) return null;

    const values = validPoints.flatMap((point) => [
      point.shortVolumeRatio ?? null,
      point.shortAmountRatio ?? null,
    ]).filter((value): value is number => value != null && Number.isFinite(value));

    if (values.length === 0) return null;

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const innerWidth = WIDTH - PADDING_X * 2;
    const innerHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;

    const toX = (index: number) => PADDING_X + (innerWidth * index) / Math.max(validPoints.length - 1, 1);
    const toY = (value: number) => PADDING_TOP + ((max - value) / range) * innerHeight;

    const buildPath = (selector: (point: ShortSellingSeriesPoint) => number | null) =>
      validPoints.reduce((acc, point, index) => {
        const value = selector(point);
        if (value == null || !Number.isFinite(value)) return acc;
        const command = acc ? "L" : "M";
        return `${acc} ${command}${toX(index).toFixed(2)},${toY(value).toFixed(2)}`.trim();
      }, "");

    const buildAreaPath = (selector: (point: ShortSellingSeriesPoint) => number | null) => {
      const coords = validPoints.flatMap((point, index) => {
        const value = selector(point);
        if (value == null || !Number.isFinite(value)) return [];
        return [{
          x: toX(index),
          y: toY(value),
        }];
      });

      if (coords.length === 0) return "";

      const line = coords
        .map((coord, index) => `${index === 0 ? "M" : "L"}${coord.x.toFixed(2)},${coord.y.toFixed(2)}`)
        .join(" ");

      const baselineY = PADDING_TOP + innerHeight;
      const first = coords[0];
      const last = coords[coords.length - 1];

      return `${line} L${last.x.toFixed(2)},${baselineY.toFixed(2)} L${first.x.toFixed(2)},${baselineY.toFixed(2)} Z`;
    };

    const buildDots = (selector: (point: ShortSellingSeriesPoint) => number | null) =>
      validPoints.flatMap((point, index) => {
        const value = selector(point);
        if (value == null || !Number.isFinite(value)) return [];
        return [{
          x: toX(index),
          y: toY(value),
          reportDate: point.reportDate,
        }];
      });

    const tickValues = [max, max - range / 2, min];
    return {
      volumePath: buildPath((point) => point.shortVolumeRatio),
      amountPath: buildPath((point) => point.shortAmountRatio),
      amountAreaPath: buildAreaPath((point) => point.shortAmountRatio),
      volumeDots: buildDots((point) => point.shortVolumeRatio),
      amountDots: buildDots((point) => point.shortAmountRatio),
      tickValues,
      toY,
      toX,
    };
  }, [validPoints]);

  if (!chart) {
    return (
      <div className="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-8 text-sm text-zinc-500">
        공매도 추이 데이터가 없습니다.
      </div>
    );
  }

  const latest = validPoints[validPoints.length - 1];
  const first = validPoints[0];
  const hoveredPoint = hoveredIndex == null ? null : validPoints[hoveredIndex] ?? null;
  const labels = [0, Math.floor((validPoints.length - 1) / 2), validPoints.length - 1]
    .filter((index, position, arr) => arr.indexOf(index) === position);

  return (
    <div className="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4">
      <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 sm:text-sm">
        <div>
          <p className="text-zinc-500">최신 거래량 비율</p>
          <p className="font-semibold" style={{ color: VOLUME_COLOR }}>{fmtPct(latest.shortVolumeRatio)}</p>
        </div>
        <div>
          <p className="text-zinc-500">최신 거래대금 비율</p>
          <p className="font-semibold" style={{ color: AMOUNT_COLOR }}>{fmtPct(latest.shortAmountRatio)}</p>
        </div>
        <div>
          <p className="text-zinc-500">시작일</p>
          <p className="font-medium text-zinc-800">{first.reportDate}</p>
        </div>
        <div>
          <p className="text-zinc-500">기준일</p>
          <p className="font-medium text-zinc-800">{latest.reportDate}</p>
        </div>
      </div>

      <div className="mt-4 overflow-x-auto">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="min-w-[680px] w-full"
          onMouseLeave={() => setHoveredIndex(null)}
        >
          {chart.tickValues.map((tick) => (
            <g key={tick}>
              <line
                x1={PADDING_X}
                x2={WIDTH - PADDING_X}
                y1={chart.toY(tick)}
                y2={chart.toY(tick)}
                stroke="#e4e4e7"
                strokeDasharray="4 4"
              />
              <text
                x={2}
                y={chart.toY(tick) + 4}
                fontSize="11"
                fill="#71717a"
              >
                {fmtPct(tick)}
              </text>
            </g>
          ))}

          {hoveredPoint && (
            <g pointerEvents="none">
              <line
                x1={chart.toX(hoveredIndex!)}
                x2={chart.toX(hoveredIndex!)}
                y1={PADDING_TOP}
                y2={HEIGHT - PADDING_BOTTOM}
                stroke="#111827"
                strokeDasharray="6 6"
              />
              <text
                x={chart.toX(hoveredIndex!)}
                y={HEIGHT - 10}
                textAnchor="middle"
                fontSize="11"
                fill="#111827"
                fontWeight="600"
              >
                {hoveredPoint.reportDate}
              </text>
            </g>
          )}

          <path d={chart.amountAreaPath} fill={AMOUNT_FILL} stroke="none" />
          <path
            d={chart.amountPath}
            fill="none"
            stroke={AMOUNT_COLOR}
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            opacity="0.8"
          />
          <path
            d={chart.volumePath}
            fill="none"
            stroke={VOLUME_COLOR}
            strokeWidth="3.5"
            strokeLinejoin="round"
            strokeLinecap="round"
          />

          {chart.amountDots.map((dot) => (
            <circle
              key={`amount-${dot.reportDate}-${dot.x}`}
              cx={dot.x}
              cy={dot.y}
              r="2.1"
              fill={AMOUNT_COLOR}
              opacity="0.7"
            />
          ))}

          {chart.volumeDots.map((dot) => (
            <circle
              key={`volume-${dot.reportDate}-${dot.x}`}
              cx={dot.x}
              cy={dot.y}
              r="2.8"
              fill={VOLUME_COLOR}
              opacity="1"
            />
          ))}

          {labels.map((index) => (
            <text
              key={`${validPoints[index]?.reportDate ?? index}`}
              x={PADDING_X + ((WIDTH - PADDING_X * 2) * index) / Math.max(validPoints.length - 1, 1)}
              y={HEIGHT - 10}
              textAnchor="middle"
              fontSize="11"
              fill="#71717a"
            >
              {validPoints[index]?.reportDate}
            </text>
          ))}

          {validPoints.map((point, index) => {
            const x = chart.toX(index);
            const prev = index === 0 ? PADDING_X : (chart.toX(index - 1) + x) / 2;
            const next = index === validPoints.length - 1 ? WIDTH - PADDING_X : (x + chart.toX(index + 1)) / 2;
            return (
              <rect
                key={`hover-${point.reportDate}`}
                x={prev}
                y={PADDING_TOP}
                width={Math.max(next - prev, 1)}
                height={HEIGHT - PADDING_TOP - PADDING_BOTTOM}
                fill="transparent"
                onMouseEnter={() => setHoveredIndex(index)}
                onFocus={() => setHoveredIndex(index)}
              />
            );
          })}
        </svg>
      </div>

      <div className="mt-3 flex flex-wrap gap-4 text-xs sm:text-sm">
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: VOLUME_COLOR }} />
          <span className="text-zinc-600">공매도 거래량 비율 선</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: AMOUNT_COLOR }} />
          <span className="text-zinc-600">공매도 거래대금 비율 레이어</span>
        </div>
      </div>
    </div>
  );
}
