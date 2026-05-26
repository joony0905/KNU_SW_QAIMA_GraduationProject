import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation("analysisPanel");
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
      <div className="mt-2 rounded-2xl border border-line bg-bg-sunk px-4 py-8 text-sm text-ink-3">
        {t("shortSellingChart.empty")}
      </div>
    );
  }

  const latest = validPoints[validPoints.length - 1];
  const first = validPoints[0];
  const hoveredPoint = hoveredIndex == null ? null : validPoints[hoveredIndex] ?? null;
  const labels = [0, Math.floor((validPoints.length - 1) / 2), validPoints.length - 1]
    .filter((index, position, arr) => arr.indexOf(index) === position);

  return (
    <div className="mt-2 rounded-2xl border border-line bg-bg-sunk px-4 py-4">
      <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 sm:text-sm">
        <div>
          <p className="text-ink-3">{t("shortSellingChart.latestVolumeRatio")}</p>
          <p className="font-semibold" style={{ color: VOLUME_COLOR }}>{fmtPct(latest.shortVolumeRatio)}</p>
        </div>
        <div>
          <p className="text-ink-3">{t("shortSellingChart.latestAmountRatio")}</p>
          <p className="font-semibold" style={{ color: AMOUNT_COLOR }}>{fmtPct(latest.shortAmountRatio)}</p>
        </div>
        <div>
          <p className="text-ink-3">{t("shortSellingChart.startDate")}</p>
          <p className="font-medium text-ink-2">{first.reportDate}</p>
        </div>
        <div>
          <p className="text-ink-3">{t("shortSellingChart.asOf")}</p>
          <p className="font-medium text-ink-2">{latest.reportDate}</p>
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
              <text
                x={2}
                y={chart.toY(tick) + 4}
                fontSize="11"
                style={{ fill: "rgb(var(--color-ink-3))" }}
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
              style={{ fill: "rgb(var(--color-ink-3))" }}
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
          <span className="text-ink-2">{t("shortSellingChart.volumeRatioLine")}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: AMOUNT_COLOR }} />
          <span className="text-ink-2">{t("shortSellingChart.amountRatioLayer")}</span>
        </div>
      </div>
    </div>
  );
}
