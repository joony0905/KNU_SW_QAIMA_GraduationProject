import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
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

const isEnglish = (language?: string | null) => (language ?? "").toLowerCase().startsWith("en");

const formatUnit = (unit?: string | null, language?: string | null) => {
  if (!unit) return "";
  if (!isEnglish(language)) return unit;
  if (unit === "연%") return "%";
  return unit;
};

const fmtRate = (value: number | null | undefined, unit?: string | null, language?: string | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}${formatUnit(unit, language)}`;
};

export default function BaseRateStepChart({ points }: Props) {
  const { t, i18n } = useTranslation("analysisPanel");
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
      <div className="mt-2 rounded-2xl border border-line bg-bg-sunk px-4 py-8 text-sm text-ink-3">
        {t("baseRateChart.empty")}
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
    <div className="mt-2 rounded-2xl border border-line bg-bg-sunk px-4 py-4">
      <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 sm:text-sm">
        <div>
          <p className="text-ink-3">{t("baseRateChart.latestRate")}</p>
          <p className="font-semibold" style={{ color: LINE_COLOR }}>{fmtRate(latest.value, latest.unit, i18n.language)}</p>
        </div>
        <div>
          <p className="text-ink-3">{t("baseRateChart.change")}</p>
          <p className="font-medium text-ink-2">{hasRange ? fmtRate(latest.value - first.value, latest.unit, i18n.language) : "-"}</p>
        </div>
        <div>
          <p className="text-ink-3">{hasRange ? t("baseRateChart.startDate") : t("baseRateChart.observedDate")}</p>
          <p className="font-medium text-ink-2">{first.date}</p>
        </div>
        <div>
          <p className="text-ink-3">{t("baseRateChart.asOf")}</p>
          <p className="font-medium text-ink-2">{hasRange ? latest.date : "-"}</p>
        </div>
      </div>

      <div className="mt-4 overflow-hidden sm:overflow-x-auto">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="w-full min-w-0 sm:min-w-[680px]"
          onMouseLeave={() => setHoveredIndex(null)}
        >
          {chart.tickValues.map((tick) => (
            <g key={tick}>
              <line
                x1={PADDING_X}
                x2={WIDTH - PADDING_X}
                y1={chart.toY(tick)}
                y2={chart.toY(tick)}
                style={{ stroke: "rgb(var(--color-line))" }}
                strokeDasharray="4 4"
              />
              <text x={2} y={chart.toY(tick) + 4} fontSize="11" style={{ fill: "rgb(var(--color-ink-3))" }}>
                {fmtRate(tick, latest.unit, i18n.language)}
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
                style={{ stroke: "rgb(var(--color-ink))" }}
                strokeDasharray="6 6"
              />
              <text
                x={chart.toX(hoveredIndex!)}
                y={HEIGHT - 10}
                textAnchor="middle"
                fontSize="11"
                style={{ fill: "rgb(var(--color-ink))" }}
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
              style={{ fill: "rgb(var(--color-ink-3))" }}
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
