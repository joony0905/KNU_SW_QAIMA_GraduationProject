import { useMemo, useState } from "react";
import type { BaseRateSeriesPoint } from "../types/feature2";

type Props = {
  points: BaseRateSeriesPoint[];
};

const WIDTH = 760;
const HEIGHT = 220;
const PADDING_X = 28;
const PADDING_TOP = 18;
const PADDING_BOTTOM = 34;
const LINE_COLOR = "#7c3aed";
const FILL_COLOR = "rgba(124, 58, 237, 0.16)";

const fmtRate = (value: number | null | undefined, unit?: string | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}${unit ?? ""}`;
};

export default function BaseRateStepChart({ points }: Props) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const validPoints = useMemo(
    () => points.filter((point) => point.value != null && Number.isFinite(point.value)),
    [points],
  );

  const chart = useMemo(() => {
    if (validPoints.length === 0) return null;

    const values = validPoints.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;
    const innerWidth = WIDTH - PADDING_X * 2;
    const innerHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;

    const toX = (index: number) => PADDING_X + (innerWidth * index) / Math.max(validPoints.length - 1, 1);
    const toY = (value: number) => PADDING_TOP + ((max - value) / range) * innerHeight;
    const baselineY = PADDING_TOP + innerHeight;

    let stepPath = "";
    let areaPath = "";

    validPoints.forEach((point, index) => {
      const x = toX(index);
      const y = toY(point.value);

      if (index === 0) {
        stepPath = `M${x.toFixed(2)},${y.toFixed(2)}`;
        areaPath = `M${x.toFixed(2)},${baselineY.toFixed(2)} L${x.toFixed(2)},${y.toFixed(2)}`;
        return;
      }

      stepPath += ` H${x.toFixed(2)} V${y.toFixed(2)}`;
      areaPath += ` H${x.toFixed(2)} V${y.toFixed(2)}`;
    });

    areaPath += ` V${baselineY.toFixed(2)} Z`;

    const tickValues = [max, max - range / 2, min];

    return {
      stepPath,
      areaPath,
      tickValues,
      toY,
      toX,
    };
  }, [validPoints]);

  if (!chart) {
    return (
      <div className="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-8 text-sm text-zinc-500">
        기준금리 추이 데이터가 없습니다.
      </div>
    );
  }

  const latest = validPoints[validPoints.length - 1];
  const first = validPoints[0];
  const hasRange = first.date !== latest.date;
  const hoveredPoint = hoveredIndex == null ? null : validPoints[hoveredIndex] ?? null;
  const labels = [0, Math.floor((validPoints.length - 1) / 2), validPoints.length - 1]
    .filter((index, position, arr) => arr.indexOf(index) === position);

  return (
    <div className="mt-2 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4">
      <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 sm:text-sm">
        <div>
          <p className="text-zinc-500">최신 금리</p>
          <p className="font-semibold" style={{ color: LINE_COLOR }}>{fmtRate(latest.value, latest.unit)}</p>
        </div>
        <div>
          <p className="text-zinc-500">변화 폭</p>
          <p className="font-medium text-zinc-800">{hasRange ? fmtRate(latest.value - first.value, latest.unit) : "-"}</p>
        </div>
        <div>
          <p className="text-zinc-500">{hasRange ? "시작일" : "관측일"}</p>
          <p className="font-medium text-zinc-800">{first.date}</p>
        </div>
        <div>
          <p className="text-zinc-500">기준일</p>
          <p className="font-medium text-zinc-800">{hasRange ? latest.date : "-"}</p>
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
              <text x={2} y={chart.toY(tick) + 4} fontSize="11" fill="#71717a">
                {fmtRate(tick, latest.unit)}
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
                {hoveredPoint.date}
              </text>
            </g>
          )}

          <path d={chart.areaPath} fill={FILL_COLOR} stroke="none" />
          <path d={chart.stepPath} fill="none" stroke={LINE_COLOR} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />

          {labels.map((index) => (
            <text
              key={`${validPoints[index]?.date ?? index}`}
              x={PADDING_X + ((WIDTH - PADDING_X * 2) * index) / Math.max(validPoints.length - 1, 1)}
              y={HEIGHT - 10}
              textAnchor="middle"
              fontSize="11"
              fill="#71717a"
            >
              {validPoints[index]?.date}
            </text>
          ))}

          {validPoints.map((point, index) => {
            const x = chart.toX(index);
            const prev = index === 0 ? PADDING_X : (chart.toX(index - 1) + x) / 2;
            const next = index === validPoints.length - 1 ? WIDTH - PADDING_X : (x + chart.toX(index + 1)) / 2;
            return (
              <rect
                key={`hover-${point.date}`}
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
    </div>
  );
}
