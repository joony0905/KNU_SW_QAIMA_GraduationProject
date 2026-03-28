import React, { memo, useEffect, useMemo, useRef } from "react";
import {
  createChart,
  LineSeries,
  CrosshairMode,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type Time,
  type UTCTimestamp,
} from "lightweight-charts";

export type RelativeLinePoint = {
  t: string;      // ISO datetime
  value: number;  // rebased %
};

interface Props {
  data: RelativeLinePoint[];
  lineColor?: string;
  title?: string;
  height?: number;

  // 부모와 공유할 KST day key
  hoveredDayKey?: string | null;
  onHoverDayKeyChange?: (dayKey: string | null) => void;
}

const isoToEpochSeconds = (iso: string) =>
  Math.floor(new Date(iso).getTime() / 1000);

/**
 * epoch seconds -> KST day key (YYYY-MM-DD)
 * Asia/Seoul 기준
 */
const toKstDayKeyFromEpochSec = (sec: number) => {
  const kstDate = new Date(sec * 1000 + 9 * 60 * 60 * 1000);
  const y = kstDate.getUTCFullYear();
  const m = String(kstDate.getUTCMonth() + 1).padStart(2, "0");
  const d = String(kstDate.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

function safeClearCrosshair(chart: IChartApi | null) {
  if (!chart) return;
  try {
    (chart as any).clearCrosshairPosition?.();
  } catch {}
}

function safeSetCrosshair(
  chart: IChartApi | null,
  time: Time,
  price: number,
  series: ISeriesApi<"Line"> | null
) {
  if (!chart || !series) return;
  try {
    (chart as any).setCrosshairPosition(price, time, series);
  } catch {}
}

function RelativeLineWidget({
  data,
  lineColor = "#2563eb",
  title,
  height = 320,
  hoveredDayKey,
  onHoverDayKeyChange,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const lineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);

  const syncingCrosshairRef = useRef(false);

  const lineData = useMemo<LineData<Time>[]>(() => {
    return (data ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.value))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.value),
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }, [data]);

  /**
   * KST day key -> 실제 차트 time/value
   * 같은 day key가 여러 개면 마지막 값으로 덮어씀
   */
  const dayMap = useMemo(() => {
    const map = new Map<string, { time: Time; value: number }>();

    for (const p of lineData) {
      const timeNum = Number(p.time);
      const key = toKstDayKeyFromEpochSec(timeNum);
      map.set(key, {
        time: p.time,
        value: Number(p.value),
      });
    }

    return map;
  }, [lineData]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    if (chartRef.current) return;

    const rect = el.getBoundingClientRect();
    const width = Math.floor(rect.width || el.clientWidth || 600);

    const chart = createChart(el, {
      width,
      height,
      layout: {
        background: { color: "#ffffff" },
        textColor: "#1f2937",
      },
      grid: {
        vertLines: { color: "#f3f4f6" },
        horzLines: { color: "#f3f4f6" },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { labelVisible: true },
        horzLine: { labelVisible: true },
      },
      rightPriceScale: {
        borderColor: "#e5e7eb",
      },
      timeScale: {
        timeVisible: true,
        visible: true,
      },
      localization: {
        priceFormatter: (price: number) => `${price.toFixed(2)}%`,
      },
    });

    const lineSeries = chart.addSeries(LineSeries, {
      color: lineColor,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
    });

    chartRef.current = chart;
    lineSeriesRef.current = lineSeries;

    const ro = new ResizeObserver(() => {
      const target = containerRef.current;
      const currentChart = chartRef.current;
      if (!target || !currentChart) return;

      const r = target.getBoundingClientRect();
      const w = Math.floor(r.width);
      const h = Math.floor(r.height);

      if (w < 80 || h < 80) return;

      currentChart.applyOptions({
        width: w,
        height: h,
      });
    });

    ro.observe(el);

    return () => {
      try {
        ro.disconnect();
      } catch {}

      try {
        chart.remove();
      } catch {}

      chartRef.current = null;
      lineSeriesRef.current = null;
    };
  }, [height, lineColor]);

  useEffect(() => {
    const lineSeries = lineSeriesRef.current;
    const chart = chartRef.current;
    if (!lineSeries || !chart) return;

    lineSeries.setData(lineData);
    chart.timeScale().fitContent();
  }, [lineData]);

  /**
   * 차트에서 hover하면 부모에 KST day key 전달
   */
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || !onHoverDayKeyChange) return;

    const handler = (param: any) => {
      if (syncingCrosshairRef.current) return;

      if (!param?.time) {
        onHoverDayKeyChange(null);
        return;
      }

      const key = toKstDayKeyFromEpochSec(Number(param.time));
      onHoverDayKeyChange(key);
    };

    chart.subscribeCrosshairMove(handler);

    return () => {
      try {
        chart.unsubscribeCrosshairMove(handler);
      } catch {}
    };
  }, [onHoverDayKeyChange]);

  /**
   * 외부 hoveredDayKey를 받아 내 차트의 실제 time/value로 crosshair 세팅
   */
  useEffect(() => {
    const chart = chartRef.current;
    const series = lineSeriesRef.current;
    if (!chart || !series) return;

    syncingCrosshairRef.current = true;
    try {
      if (!hoveredDayKey) {
        safeClearCrosshair(chart);
        return;
      }

      const found = dayMap.get(hoveredDayKey);
      if (!found) {
        safeClearCrosshair(chart);
        return;
      }

      safeSetCrosshair(chart, found.time, found.value, series);
    } finally {
      queueMicrotask(() => {
        syncingCrosshairRef.current = false;
      });
    }
  }, [hoveredDayKey, dayMap]);

  return (
    <div className="w-full h-full min-h-0 flex flex-col">
      {title && (
        <div className="px-1 pb-2 text-sm font-medium text-zinc-700">
          {title}
        </div>
      )}
      <div
        ref={containerRef}
        className="w-full flex-1 min-h-0"
        style={{ height }}
      />
    </div>
  );
}

export default memo(RelativeLineWidget);