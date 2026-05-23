import { memo, useEffect, useMemo, useRef, useState } from "react";
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  LineSeries,
  CrosshairMode,
  createSeriesMarkers,
  type ISeriesMarkersPluginApi,
  type UTCTimestamp,
  type IChartApi,
  type ISeriesApi,
  type Time,
  type CandlestickData,
  type HistogramData,
  type LineData,
  type MouseEventParams,
  type SeriesMarker,
} from "lightweight-charts";

import type { Candle } from "../types/candle";
import { useTheme } from "../hooks/useTheme";
import { clientLog } from "../utils/clientLog";

/**
 * indicators는 "result.metrics.indicators"를 page에서 분리해 내려주는 형태 (indicatorData)
 *
 * 확정 구조:
 * - ema: { 20: [...], 60: [...], 120: [...] }
 * - bb20_2: [{ t, mid, upper, lower }, ...]
 * - stoch14_3_3: [{ t, k, d }, ...]
 * - warnings: string[]
 */
export type IndicatorData = {
  ema: Record<string, { t: string; value: number | null }[]> | null;
  bb20_2: { t: string; mid: number | null; upper: number | null; lower: number | null }[] | null;
  stoch14_3_3: { t: string; k: number | null; d: number | null }[] | null;
  warnings: string[];
};

type MarkerMode = "sto_ema" | "bb_sto" | "both" | "triple";

interface Props {
  candles: Candle[];
  indicators?: IndicatorData | null;
  showSubPanes: boolean; // 분석 전, 분석 후
  showIndicators?: boolean;
  showMarkers?: boolean;
  onRequestMoreHistory?: () => void;
  markerMode?: MarkerMode;

  // 부모와 공유할 KST day key
  hoveredDayKey?: string | null;
  onHoverDayKeyChange?: (dayKey: string | null) => void;
}

/** epoch ms / s 자동 보정 */
const toEpochSeconds = (t: number) => (t > 10_000_000_000 ? Math.floor(t / 1000) : t);
/** ISO string -> epoch seconds */
const isoToEpochSeconds = (iso: string) => Math.floor(new Date(iso).getTime() / 1000);

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

const normalizeSeriesByTime = <T extends { time: Time }>(items: T[]): T[] => {
  const byTime = new Map<number, T>();

  for (const item of items) {
    const time = Number(item.time);
    if (!Number.isFinite(time)) continue;
    byTime.set(time, item);
  }

  return Array.from(byTime.values()).sort((a, b) => Number(a.time) - Number(b.time));
};

/* =========================
   Candle / Volume Mapper
========================= */

const normalizeCandles = (candles: Candle[]): Candle[] => {
  const byTime = new Map<number, Candle>();
  for (const candle of candles) {
    const time = toEpochSeconds(Number(candle.t));
    byTime.set(time, {
      ...candle,
      t: time,
    });
  }
  return Array.from(byTime.values()).sort((a, b) => Number(a.t) - Number(b.t));
};

const mapCandles = (candles: Candle[]): CandlestickData<Time>[] =>
  normalizeCandles(candles)
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as UTCTimestamp,
      open: Number((c as any).o),
      high: Number((c as any).h),
      low: Number((c as any).l),
      close: Number((c as any).c),
    }));

const mapVolumes = (
  candles: Candle[],
  colors: { up: string; down: string },
): HistogramData<Time>[] =>
  normalizeCandles(candles)
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as UTCTimestamp,
      value: Number((c as any).v),
      color:
        Number((c as any).c) >= Number((c as any).o) ? colors.up : colors.down,
    }));

/* =========================
   Indicator mappers
========================= */

const toLineSeries = (
  points: { t: string; value: number | null }[],
  candleTimes?: number[]
): LineData<Time>[] =>
  normalizeSeriesByTime(
    points
      .filter((p) => p.value !== null && p.value !== undefined)
      .map((p) => ({
        time: snapIsoToCandleTime(p.t, candleTimes) as UTCTimestamp,
        value: Number(p.value),
      }))
  );

const toEmaLineSeriesMap = (
  ema: Record<string, { t: string; value: number | null }[]>,
  candleTimes?: number[]
) => {
  const out: Record<string, LineData<Time>[]> = {};
  Object.entries(ema).forEach(([period, pts]) => {
    out[String(period)] = toLineSeries(pts ?? [], candleTimes);
  });
  return out;
};

/* =========================
   Crosshair helpers
========================= */

function safeClearCrosshair(chart: IChartApi | null) {
  if (!chart) return;
  try {
    (chart as any).clearCrosshairPosition?.();
  } catch {}
}

function safeSetCrosshair(chart: IChartApi | null, time: Time, price: number, series: ISeriesApi<any> | null) {
  if (!chart || !series) return;
  try {
    (chart as any).setCrosshairPosition(price, time, series);
  } catch {}
}

/* =========================
   timeScale safe wrappers
========================= */

function getVisibleTimeRangeSafe(chart: IChartApi) {
  try {
    const ts: any = chart.timeScale();
    if (typeof ts.getVisibleRange === "function") return ts.getVisibleRange();
  } catch {}
  return null;
}

function setVisibleTimeRangeSafe(chart: IChartApi, range: any): boolean {
  if (!range || range.from == null || range.to == null) return false;
  try {
    const ts: any = chart.timeScale();
    if (typeof ts.setVisibleRange === "function") {
      ts.setVisibleRange(range);
      return true;
    }
  } catch {}
  return false;
}

function subscribeVisibleRangeChangeSafe(chart: IChartApi, cb: () => void): () => void {
  const ts: any = chart.timeScale();
  if (typeof ts.subscribeVisibleTimeRangeChange === "function") {
    ts.subscribeVisibleTimeRangeChange(cb);
    return () => {
      try {
        ts.unsubscribeVisibleTimeRangeChange(cb);
      } catch {}
    };
  }
  if (typeof ts.subscribeVisibleLogicalRangeChange === "function") {
    ts.subscribeVisibleLogicalRangeChange(cb);
    return () => {
      try {
        ts.unsubscribeVisibleLogicalRangeChange(cb);
      } catch {}
    };
  }
  return () => {};
}

/* =========================
   Align indicator times to candle times
========================= */

function alignTimesToCandles(points: { t: string }[], candleTimes: number[]): number[] {
  if (!points || points.length === 0 || !candleTimes || candleTimes.length === 0) return [];

  const nearest = (target: number) => {
    let lo = 0;
    let hi = candleTimes.length - 1;

    while (lo < hi) {
      const mid = Math.floor((lo + hi) / 2);
      if (candleTimes[mid] < target) lo = mid + 1;
      else hi = mid;
    }

    const i = lo;
    const a = candleTimes[i];
    const b = i > 0 ? candleTimes[i - 1] : a;
    return Math.abs(a - target) < Math.abs(target - b) ? a : b;
  };

  return points.map((p) => nearest(isoToEpochSeconds(p.t)));
}

function snapIsoToCandleTime(iso: string, candleTimes?: number[]): number {
  const raw = isoToEpochSeconds(iso);
  if (!candleTimes?.length) return raw;

  let lo = 0;
  let hi = candleTimes.length - 1;

  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (candleTimes[mid] < raw) lo = mid + 1;
    else hi = mid;
  }

  const i = lo;
  const next = candleTimes[i];
  const prev = i > 0 ? candleTimes[i - 1] : next;
  return Math.abs(next - raw) < Math.abs(raw - prev) ? next : prev;
}

function snapIsoToCandleTimeMap<T extends { t: string }>(
  points: T[],
  candleTimes: number[],
  pick: (p: T) => number | null | undefined
): Map<number, number> {
  const out = new Map<number, number>();
  if (!points?.length || !candleTimes?.length) return out;

  const snapped = alignTimesToCandles(points, candleTimes);
  for (let i = 0; i < points.length; i++) {
    const v = pick(points[i]);
    if (v === null || v === undefined) continue;
    out.set(snapped[i], Number(v));
  }
  return out;
}

function snapEmaPeriodToMap(
  ema: Record<string, { t: string; value: number | null }[]> | null | undefined,
  period: string,
  candleTimes: number[]
): Map<number, number> {
  const pts = ema?.[period] ?? [];
  return snapIsoToCandleTimeMap(pts, candleTimes, (p) => p.value);
}

/* =========================
   Component
========================= */

function TradingViewWidget({
  candles,
  indicators,
  showSubPanes,
  showIndicators = true,
  showMarkers = true,
  onRequestMoreHistory,
  markerMode = "both",
  hoveredDayKey,
  onHoverDayKeyChange,
}: Props) {
  const wrapRef = useRef<HTMLDivElement | null>(null);

  // containers
  const priceElRef = useRef<HTMLDivElement | null>(null);
  const stochElRef = useRef<HTMLDivElement | null>(null);
  const volumeElRef = useRef<HTMLDivElement | null>(null);

  // charts
  const priceChartRef = useRef<IChartApi | null>(null);
  const stochChartRef = useRef<IChartApi | null>(null);
  const volumeChartRef = useRef<IChartApi | null>(null);

  // base series
  const candleSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);

  // markers
  const candleMarkersRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null);

  // indicators registry
  const emaSeriesRef = useRef<Map<string, ISeriesApi<"Line">>>(new Map());
  const bbSeriesRef = useRef<{ upper?: ISeriesApi<"Line">; mid?: ISeriesApi<"Line">; lower?: ISeriesApi<"Line"> }>({});
  const stochSeriesRef = useRef<{
    k?: ISeriesApi<"Line">;
    d?: ISeriesApi<"Line">;
    ob80?: ISeriesApi<"Line">;
    os20?: ISeriesApi<"Line">;
  }>({});

  // ready flags
  const volumeReadyRef = useRef(false);
  const stochReadyRef = useRef(false);

  // history loader throttle
  const lastHistoryReqAtRef = useRef<number>(0);
  const onRequestMoreHistoryRef = useRef<(() => void) | undefined>(onRequestMoreHistory);
  useEffect(() => {
    onRequestMoreHistoryRef.current = onRequestMoreHistory;
  }, [onRequestMoreHistory]);

  // fitContent only once
  const didInitFitRef = useRef(false);

  // sync guard
  const syncingCrosshairRef = useRef(false);
  const syncingRangeRef = useRef(false);
  const subPaneSyncCleanupRef = useRef<(() => void) | null>(null);

  // pane ready tick
  const [subPaneReadyTick, setSubPaneReadyTick] = useState(0);

  // theme-aware candle/volume colors (시세 토큰 --color-rise / --color-fall과 매칭)
  const { theme } = useTheme();

  // 차트 캔버스 배경/격자/축 색 — 다크모드 대응 (카드 surface 색과 맞춤)
  const chartTheme = useMemo(
    () =>
      theme === "dark"
        ? {
            bg: "#202024",
            text: "#d4d4d4",
            grid: "#303035",
            border: "#3a3a40",
          }
        : {
            bg: "#ffffff",
            text: "#1f2937",
            grid: "#f3f4f6",
            border: "#e5e7eb",
          },
    [theme],
  );
  const candleColors = useMemo(() => {
    if (theme === "dark") {
      return {
        up: "#f87171",
        down: "#60a5fa",
        upVolume: "rgba(248, 113, 113, 0.55)",
        downVolume: "rgba(96, 165, 250, 0.55)",
      };
    }
    return {
      up: "#dc2626",
      down: "#2563eb",
      upVolume: "rgba(220, 38, 38, 0.55)",
      downVolume: "rgba(37, 99, 235, 0.55)",
    };
  }, [theme]);

  // candle time list (sorted epoch seconds)
  const candleTimeList = useMemo(() => {
    return Array.from(
      new Set(
        (candles ?? [])
          .map((c) => toEpochSeconds(Number(c.t)))
          .filter((x) => Number.isFinite(x))
      )
    ).sort((a, b) => a - b);
  }, [candles]);

  /**
   *  KST day key -> 실제 candle time/close
   */
  const candleDayMap = useMemo(() => {
    const map = new Map<string, { time: UTCTimestamp; close: number }>();

    for (const c of candles ?? []) {
      const t = toEpochSeconds(Number((c as any).t));
      const close = Number((c as any).c);

      if (!Number.isFinite(t) || !Number.isFinite(close)) continue;

      const key = toKstDayKeyFromEpochSec(t);
      map.set(key, {
        time: t as UTCTimestamp,
        close,
      });
    }

    return map;
  }, [candles]);

  const wrapperClass = useMemo(() => {
    if (showSubPanes) return "w-full h-full min-h-0 grid grid-rows-[6fr_2fr_2fr]";
    return "w-full h-full min-h-0 grid grid-rows-1";
  }, [showSubPanes]);

  const paneBaseClass = "min-h-0 w-full";

  const resizeChartToElement = (
    chart: IChartApi | null,
    element: HTMLDivElement | null,
    minHeight = 60,
  ) => {
    if (!chart || !element) return;
    const rect = element.getBoundingClientRect();
    const width = Math.floor(rect.width || element.clientWidth || 600);
    const height = Math.floor(rect.height || element.clientHeight || minHeight);
    if (width < 80 || height < minHeight) return;
    chart.applyOptions({ width, height });
  };

  const resizeChartsToLayout = () => {
    resizeChartToElement(priceChartRef.current, priceElRef.current, 80);
    if (showSubPanes) {
      resizeChartToElement(stochChartRef.current, stochElRef.current, 60);
      resizeChartToElement(volumeChartRef.current, volumeElRef.current, 60);
    }
  };

  /* =========================
     timeRange sync
  ========================= */
  const syncTimeRangeFromPrice = () => {
    const pc = priceChartRef.current;
    const sc = stochChartRef.current;
    const vc = volumeChartRef.current;
    if (!pc || !sc || !vc) return;

    if (!volumeReadyRef.current || !stochReadyRef.current) return;

    const tr = getVisibleTimeRangeSafe(pc);
    if (!tr || tr.from == null || tr.to == null) return;

    syncingRangeRef.current = true;
    try {
      setVisibleTimeRangeSafe(sc, tr);
      setVisibleTimeRangeSafe(vc, tr);
    } finally {
      syncingRangeRef.current = false;
    }
  };

  /* =========================
     Init Price Chart
  ========================= */
  useEffect(() => {
    const el = priceElRef.current;
    if (!el) return;
    if (priceChartRef.current) return;

    const r = el.getBoundingClientRect();
    const w = Math.floor(r.width || el.clientWidth || 600);
    const h = Math.floor(r.height || el.clientHeight || 420);

    const chart = createChart(el, {
      width: w,
      height: h,
      layout: { background: { color: chartTheme.bg }, textColor: chartTheme.text },
      grid: { vertLines: { color: chartTheme.grid }, horzLines: { color: chartTheme.grid } },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { labelVisible: true },
        horzLine: { labelVisible: true },
      },
      rightPriceScale: { borderColor: chartTheme.border },
      timeScale: { timeVisible: true, visible: true },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: candleColors.up,
      downColor: candleColors.down,
      borderVisible: false,
      wickUpColor: candleColors.up,
      wickDownColor: candleColors.down,
      priceFormat: { type: "price", precision: 0, minMove: 1 },
    });

    try {
      candleMarkersRef.current = createSeriesMarkers(candleSeries, [], {
        autoScale: false,
      });
    } catch (e) {
      clientLog.warn("TradingView marker creation failed", e);
      candleMarkersRef.current = null;
    }

    priceChartRef.current = chart;
    candleSeriesRef.current = candleSeries;

    const onVisibleRangeChange = (range: any) => {
      const cb = onRequestMoreHistoryRef.current;
      if (!cb || !range) return;

      const left = typeof range.from === "number" ? range.from : null;
      if (left === null) return;

      const now = Date.now();
      if (now - lastHistoryReqAtRef.current < 1000) return;

      if (left < 10) {
        lastHistoryReqAtRef.current = now;
        cb();
      }
    };
    chart.timeScale().subscribeVisibleLogicalRangeChange(onVisibleRangeChange);

    const ro = new ResizeObserver(() => {
      const el2 = priceElRef.current;
      const ch = priceChartRef.current;
      if (!el2 || !ch) return;
      const rr = el2.getBoundingClientRect();
      const ww = Math.floor(rr.width);
      const hh = Math.floor(rr.height);
      if (ww < 80 || hh < 80) return;
      ch.applyOptions({ width: ww, height: hh });
    });
    ro.observe(el);

    return () => {
      try {
        chart.timeScale().unsubscribeVisibleLogicalRangeChange(onVisibleRangeChange);
      } catch {}
      try {
        ro.disconnect();
      } catch {}

      try {
        emaSeriesRef.current.forEach((s) => chart.removeSeries(s));
        emaSeriesRef.current.clear();
        Object.values(bbSeriesRef.current).forEach((s) => s && chart.removeSeries(s));
        bbSeriesRef.current = {};
      } catch {}

      try {
        candleMarkersRef.current?.detach?.();
      } catch {}
      candleMarkersRef.current = null;

      chart.remove();
      priceChartRef.current = null;
      candleSeriesRef.current = null;
      didInitFitRef.current = false;
    };
  }, []);

  /* =========================
     Toggle Sub Panes
  ========================= */
  useEffect(() => {
    const pc = priceChartRef.current;
    if (!pc) return;

    if (!showSubPanes) {
      subPaneSyncCleanupRef.current?.();
      subPaneSyncCleanupRef.current = null;
      volumeReadyRef.current = false;
      stochReadyRef.current = false;

      if (stochChartRef.current) {
        const sc = stochChartRef.current;
        try {
          Object.values(stochSeriesRef.current).forEach((s) => s && sc.removeSeries(s));
        } catch {}
        try {
          (sc as any).__ro?.disconnect?.();
        } catch {}
        try {
          sc.remove();
        } catch {}
        stochChartRef.current = null;
        stochSeriesRef.current = {};
      }

      if (volumeChartRef.current) {
        const vc = volumeChartRef.current;
        try {
          if (volumeSeriesRef.current) vc.removeSeries(volumeSeriesRef.current);
        } catch {}
        try {
          (vc as any).__ro?.disconnect?.();
        } catch {}
        try {
          vc.remove();
        } catch {}
        volumeChartRef.current = null;
        volumeSeriesRef.current = null;
      }

      requestAnimationFrame(() => {
        resizeChartToElement(priceChartRef.current, priceElRef.current, 80);
      });
      return;
    }

    let disposed = false;
    const rafId = requestAnimationFrame(() => {
      if (disposed) return;
      let createdAny = false;

      if (!stochChartRef.current && stochElRef.current) {
        const el = stochElRef.current;
        const r = el.getBoundingClientRect();
        const w = Math.floor(r.width || el.clientWidth || 600);
        const h = Math.floor(r.height || el.clientHeight || 140);

        if (w >= 80 && h >= 60) {
          const sc = createChart(el, {
            width: w,
            height: h,
            layout: { background: { color: chartTheme.bg }, textColor: chartTheme.text },
            grid: { vertLines: { color: chartTheme.grid }, horzLines: { color: chartTheme.grid } },
            crosshair: {
              mode: CrosshairMode.Normal,
              vertLine: { labelVisible: false },
              horzLine: { labelVisible: false },
            },
            rightPriceScale: { borderColor: chartTheme.border },
            timeScale: { timeVisible: true, visible: false },
          });

          sc.priceScale("right").applyOptions({ scaleMargins: { top: 0.15, bottom: 0.15 } });
          stochChartRef.current = sc;
          createdAny = true;

          const ro = new ResizeObserver(() => {
            const el2 = stochElRef.current;
            const ch = stochChartRef.current;
            if (!el2 || !ch) return;
            const rr = el2.getBoundingClientRect();
            const ww = Math.floor(rr.width);
            const hh = Math.floor(rr.height);
            if (ww < 80 || hh < 60) return;
            ch.applyOptions({ width: ww, height: hh });
          });
          ro.observe(el);
          (sc as any).__ro = ro;
        }
      }

      if (!volumeChartRef.current && volumeElRef.current) {
        const el = volumeElRef.current;
        const r = el.getBoundingClientRect();
        const w = Math.floor(r.width || el.clientWidth || 600);
        const h = Math.floor(r.height || el.clientHeight || 120);

        if (w >= 80 && h >= 60) {
          const vc = createChart(el, {
            width: w,
            height: h,
            layout: { background: { color: chartTheme.bg }, textColor: chartTheme.text },
            grid: { vertLines: { color: chartTheme.grid }, horzLines: { color: chartTheme.grid } },
            crosshair: {
              mode: CrosshairMode.Normal,
              vertLine: { labelVisible: false },
              horzLine: { labelVisible: false },
            },
            rightPriceScale: { borderColor: chartTheme.border },
            timeScale: { timeVisible: true, visible: true },
          });

          const vs = vc.addSeries(HistogramSeries, { priceFormat: { type: "volume" }, priceScaleId: "volume" });
          vc.priceScale("volume").applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

          volumeChartRef.current = vc;
          volumeSeriesRef.current = vs;
          createdAny = true;

          const ro = new ResizeObserver(() => {
            const el2 = volumeElRef.current;
            const ch = volumeChartRef.current;
            if (!el2 || !ch) return;
            const rr = el2.getBoundingClientRect();
            const ww = Math.floor(rr.width);
            const hh = Math.floor(rr.height);
            if (ww < 80 || hh < 60) return;
            ch.applyOptions({ width: ww, height: hh });
          });
          ro.observe(el);
          (vc as any).__ro = ro;
        }
      }

      if (createdAny) setSubPaneReadyTick((v) => v + 1);

      const pc2 = priceChartRef.current;
      if (!pc2 || !stochChartRef.current || !volumeChartRef.current) return;

      subPaneSyncCleanupRef.current?.();

      const unsubscribe = subscribeVisibleRangeChangeSafe(pc2, () => {
        if (syncingRangeRef.current) return;
        syncTimeRangeFromPrice();
      });

      const onPc = (param: MouseEventParams<Time>) => {
        if (syncingCrosshairRef.current) return;
        const sc = stochChartRef.current;
        const vc = volumeChartRef.current;
        if (!sc || !vc) return;

        syncingCrosshairRef.current = true;
        try {
          if (!param.time) {
            safeClearCrosshair(sc);
            safeClearCrosshair(vc);
            return;
          }
          const t = param.time as Time;

          safeSetCrosshair(sc, t, 50, stochSeriesRef.current.k ?? stochSeriesRef.current.d ?? null);
          safeSetCrosshair(vc, t, 0, volumeSeriesRef.current);
        } finally {
          syncingCrosshairRef.current = false;
        }
      };

      pc2.subscribeCrosshairMove(onPc);
      resizeChartsToLayout();
      syncTimeRangeFromPrice();

      subPaneSyncCleanupRef.current = () => {
        try {
          unsubscribe();
        } catch {}
        try {
          pc2.unsubscribeCrosshairMove(onPc);
        } catch {}
      };
    });

    return () => {
      disposed = true;
      cancelAnimationFrame(rafId);
      subPaneSyncCleanupRef.current?.();
      subPaneSyncCleanupRef.current = null;
    };
  }, [showSubPanes]);

  /* =========================
     wrapper resize
  ========================= */
  useEffect(() => {
    const wrap = wrapRef.current;
    if (!wrap) return;

    const ro = new ResizeObserver(() => {
      resizeChartsToLayout();
      if (showSubPanes) setSubPaneReadyTick((v) => v + 1);
    });

    ro.observe(wrap);
    return () => ro.disconnect();
  }, [showSubPanes]);

  /* =========================
     Set candle data
  ========================= */
  useEffect(() => {
    const cs = candleSeriesRef.current;
    if (!cs) return;
    if (!candles || candles.length === 0) return;

    cs.setData(mapCandles(candles));

    if (!didInitFitRef.current) {
      priceChartRef.current?.timeScale().fitContent();
      didInitFitRef.current = true;
    }

    if (showSubPanes) syncTimeRangeFromPrice();
  }, [candles, showSubPanes]);

  /* =========================
     Theme-aware candle colors sync
  ========================= */
  useEffect(() => {
    const cs = candleSeriesRef.current;
    if (!cs) return;
    cs.applyOptions({
      upColor: candleColors.up,
      downColor: candleColors.down,
      wickUpColor: candleColors.up,
      wickDownColor: candleColors.down,
    });
  }, [candleColors]);

  /* =========================
     Theme-aware chart canvas sync
  ========================= */
  useEffect(() => {
    const opts = {
      layout: { background: { color: chartTheme.bg }, textColor: chartTheme.text },
      grid: {
        vertLines: { color: chartTheme.grid },
        horzLines: { color: chartTheme.grid },
      },
      rightPriceScale: { borderColor: chartTheme.border },
    };
    try {
      priceChartRef.current?.applyOptions(opts);
      stochChartRef.current?.applyOptions(opts);
      volumeChartRef.current?.applyOptions(opts);
    } catch {}
  }, [chartTheme]);

  /* =========================
     Volume data
  ========================= */
  useEffect(() => {
    if (!showSubPanes) return;
    const vs = volumeSeriesRef.current;
    if (!vs) return;
    if (!candles || candles.length === 0) return;

    vs.setData(
      mapVolumes(candles, {
        up: candleColors.upVolume,
        down: candleColors.downVolume,
      }),
    );
    volumeReadyRef.current = true;

    volumeChartRef.current?.timeScale().fitContent();
    syncTimeRangeFromPrice();
  }, [showSubPanes, subPaneReadyTick, candles, candleColors]);

  /* =========================
     EMA
  ========================= */
  useEffect(() => {
    const chart = priceChartRef.current;
    if (!chart) return;

    emaSeriesRef.current.forEach((s) => {
      try {
        chart.removeSeries(s);
      } catch {}
    });
    emaSeriesRef.current.clear();

    if (!showIndicators) return;

    const ema = indicators?.ema;
    if (!ema || Object.keys(ema).length === 0) return;

    const emaSeriesMap = toEmaLineSeriesMap(ema, candleTimeList);

    Object.entries(emaSeriesMap).forEach(([period, data]) => {
      if (!data || data.length === 0) return;

      const color = period === "20" ? "#f59e0b" : period === "60" ? "#10b981" : "#6366f1";
      const series = chart.addSeries(LineSeries, { lineWidth: 2, color });
      series.setData(data);
      emaSeriesRef.current.set(period, series);
    });
  }, [showIndicators, indicators?.ema, candleTimeList]);

  /* =========================
     BB
  ========================= */
  useEffect(() => {
    const chart = priceChartRef.current;
    if (!chart) return;

    Object.values(bbSeriesRef.current).forEach((s) => {
      if (!s) return;
      try {
        chart.removeSeries(s);
      } catch {}
    });
    bbSeriesRef.current = {};

    if (!showIndicators) return;

    const bb = indicators?.bb20_2;
    if (!bb || bb.length === 0) return;

    const upper = chart.addSeries(LineSeries, { color: "rgba(59,130,246,0.6)", lineWidth: 1 });
    const mid = chart.addSeries(LineSeries, { color: "rgba(107,114,128,0.6)", lineWidth: 1 });
    const lower = chart.addSeries(LineSeries, { color: "rgba(59,130,246,0.6)", lineWidth: 1 });

    upper.setData(
      normalizeSeriesByTime(
        bb
          .filter((p) => p.upper !== null && p.upper !== undefined)
          .map((p) => ({ time: snapIsoToCandleTime(p.t, candleTimeList) as UTCTimestamp, value: Number(p.upper) }))
      )
    );
    mid.setData(
      normalizeSeriesByTime(
        bb
          .filter((p) => p.mid !== null && p.mid !== undefined)
          .map((p) => ({ time: snapIsoToCandleTime(p.t, candleTimeList) as UTCTimestamp, value: Number(p.mid) }))
      )
    );
    lower.setData(
      normalizeSeriesByTime(
        bb
          .filter((p) => p.lower !== null && p.lower !== undefined)
          .map((p) => ({ time: snapIsoToCandleTime(p.t, candleTimeList) as UTCTimestamp, value: Number(p.lower) }))
      )
    );

    bbSeriesRef.current = { upper, mid, lower };
  }, [showIndicators, indicators?.bb20_2, candleTimeList]);

  /* =========================
     STO
  ========================= */
  useEffect(() => {
    if (!showSubPanes) return;
    const chart = stochChartRef.current;
    if (!chart) return;

    Object.values(stochSeriesRef.current).forEach((s) => {
      if (!s) return;
      try {
        chart.removeSeries(s);
      } catch {}
    });
    stochSeriesRef.current = {};
    stochReadyRef.current = false;

    if (!showIndicators) return;

    const st = indicators?.stoch14_3_3;
    if (!st || st.length === 0) return;
    if (!candleTimeList || candleTimeList.length === 0) return;

    const snappedTimes = alignTimesToCandles(st, candleTimeList);

    const kSeries = chart.addSeries(LineSeries, { color: "rgba(236,72,153,0.85)", lineWidth: 1 });
    const dSeries = chart.addSeries(LineSeries, { color: "rgba(34,197,94,0.85)", lineWidth: 1 });

    const kData = normalizeSeriesByTime(
      st
        .map((p, i) => ({ time: snappedTimes[i] as UTCTimestamp, value: p.k }))
        .filter((p) => p.value !== null && p.value !== undefined)
        .map((p) => ({ time: p.time, value: Number(p.value) }))
    );

    const dData = normalizeSeriesByTime(
      st
        .map((p, i) => ({ time: snappedTimes[i] as UTCTimestamp, value: p.d }))
        .filter((p) => p.value !== null && p.value !== undefined)
        .map((p) => ({ time: p.time, value: Number(p.value) }))
    );

    kSeries.setData(kData);
    dSeries.setData(dData);

    const ob80 = chart.addSeries(LineSeries, { color: "rgba(148,163,184,0.8)", lineWidth: 1, lineStyle: 2 });
    const os20 = chart.addSeries(LineSeries, { color: "rgba(148,163,184,0.8)", lineWidth: 1, lineStyle: 2 });

    const baseTimes = (kData.length > 0 ? kData : dData).map((p) => p.time);
    if (baseTimes.length > 0) {
      ob80.setData(baseTimes.map((t) => ({ time: t, value: 80 })));
      os20.setData(baseTimes.map((t) => ({ time: t, value: 20 })));
    }

    stochSeriesRef.current = { k: kSeries, d: dSeries, ob80, os20 };
    stochReadyRef.current = true;

    chart.timeScale().fitContent();
    syncTimeRangeFromPrice();
  }, [showSubPanes, subPaneReadyTick, showIndicators, indicators?.stoch14_3_3, candleTimeList]);

  /* =========================
     Markers
  ========================= */
  useEffect(() => {
    const sm = candleMarkersRef.current;
    if (!sm) return;

    if (!showMarkers) {
      try {
        sm.setMarkers([]);
      } catch {}
      return;
    }

    if (!candles?.length || candleTimeList.length === 0) {
      try {
        sm.setMarkers([]);
      } catch {}
      return;
    }

    const sorted = [...candles]
      .map((c) => ({
        t: toEpochSeconds(Number((c as any).t)),
        o: Number((c as any).o),
        h: Number((c as any).h),
        l: Number((c as any).l),
        c: Number((c as any).c),
        v: Number((c as any).v),
      }))
      .filter((x) => Number.isFinite(x.t))
      .sort((a, b) => a.t - b.t);

    if (sorted.length < 3) {
      try {
        sm.setMarkers([]);
      } catch {}
      return;
    }

    const ema20Map = snapEmaPeriodToMap(indicators?.ema ?? null, "20", candleTimeList);
    const ema60Map = snapEmaPeriodToMap(indicators?.ema ?? null, "60", candleTimeList);

    const bb = indicators?.bb20_2 ?? [];
    const bbUpperMap = snapIsoToCandleTimeMap(bb, candleTimeList, (p) => p.upper);
    const bbLowerMap = snapIsoToCandleTimeMap(bb, candleTimeList, (p) => p.lower);

    const st = indicators?.stoch14_3_3 ?? [];
    const stKMap = snapIsoToCandleTimeMap(st, candleTimeList, (p) => p.k);
    const stDMap = snapIsoToCandleTimeMap(st, candleTimeList, (p) => p.d);

    const crossUp = (prevA?: number, prevB?: number, curA?: number, curB?: number) =>
      prevA !== undefined &&
      prevB !== undefined &&
      curA !== undefined &&
      curB !== undefined &&
      prevA <= prevB &&
      curA > curB;

    const crossDown = (prevA?: number, prevB?: number, curA?: number, curB?: number) =>
      prevA !== undefined &&
      prevB !== undefined &&
      curA !== undefined &&
      curB !== undefined &&
      prevA >= prevB &&
      curA < curB;

    const markers: SeriesMarker<UTCTimestamp>[] = [];

    const COOL_DOWN_BARS = 5;
    let lastMarkI = -Infinity;
    let lastBreakI = -Infinity;
    const BREAK_FAIL_WINDOW = 5;

    const volSma20 = (() => {
      const out: (number | null)[] = new Array(sorted.length).fill(null);
      let sum = 0;
      for (let i = 0; i < sorted.length; i++) {
        const vv = Number.isFinite(sorted[i].v) ? sorted[i].v : 0;
        sum += vv;
        if (i >= 20) sum -= Number.isFinite(sorted[i - 20].v) ? sorted[i - 20].v : 0;
        if (i >= 19) out[i] = sum / 20;
      }
      return out;
    })();

    for (let i = 1; i < sorted.length; i++) {
      const cur = sorted[i];
      const prev = sorted[i - 1];
      const t = cur.t as UTCTimestamp;

      const ema20 = ema20Map.get(cur.t);
      const ema60 = ema60Map.get(cur.t);

      const lower = bbLowerMap.get(cur.t);
      const upper = bbUpperMap.get(cur.t);
      const prevLower = bbLowerMap.get(prev.t);
      const prevUpper = bbUpperMap.get(prev.t);

      const k = stKMap.get(cur.t);
      const d = stDMap.get(cur.t);
      const prevK = stKMap.get(prev.t);
      const prevD = stDMap.get(prev.t);

      const cu = crossUp(prevK, prevD, k, d);
      const cd = crossDown(prevK, prevD, k, d);

      const isUpTrend = ema20 !== undefined && ema60 !== undefined ? ema20 >= ema60 : false;
      const isDownTrend = ema20 !== undefined && ema60 !== undefined ? ema20 <= ema60 : false;

      const coolOk = i - lastMarkI >= COOL_DOWN_BARS;

      if (markerMode === "triple") {
        const t1 =
          lower !== undefined &&
          prevLower !== undefined &&
          prev.c <= prevLower &&
          cur.c > lower &&
          cu &&
          (k ?? 999) <= 25 &&
          isUpTrend;

        const volOk =
          volSma20[i] !== null && Number.isFinite(volSma20[i] as number)
            ? cur.v >= (volSma20[i] as number) * 1.3
            : false;

        const t3 =
          upper !== undefined &&
          prevUpper !== undefined &&
          prev.c <= prevUpper &&
          cur.c > upper &&
          isUpTrend &&
          volOk;

        const inBreakWindow = i - lastBreakI >= 1 && i - lastBreakI <= BREAK_FAIL_WINDOW;

        const stoWeak =
          cd ||
          (prevK !== undefined && k !== undefined && k < prevK) ||
          ((k ?? 0) >= 80 && (prevK !== undefined && k !== undefined && k < prevK));

        const breakFailWarn =
          inBreakWindow &&
          upper !== undefined &&
          cur.c < upper &&
          stoWeak;

        const t2weak =
          upper !== undefined &&
          prevUpper !== undefined &&
          prev.c >= prevUpper * 0.995 &&
          cur.c < upper &&
          (cd || (prevK !== undefined && k !== undefined && k < prevK)) &&
          (k ?? -999) >= 70;

        const t2strong =
          upper !== undefined &&
          prevUpper !== undefined &&
          prev.c >= prevUpper &&
          cur.c < upper &&
          cd &&
          (k ?? -999) >= 75 &&
          isDownTrend;

        if (coolOk && t3) {
          markers.push({
            time: t,
            position: "aboveBar",
            shape: "circle",
            color: "#7c3aed",
            text: "FORCE",
          });
          lastMarkI = i;
          lastBreakI = i;
        } else if (coolOk && t1) {
          markers.push({
            time: t,
            position: "belowBar",
            shape: "circle",
            color: "#16a34a",
            text: "ENTRY",
          });
          lastMarkI = i;
        } else if (coolOk && (breakFailWarn || t2weak || t2strong)) {
          markers.push({
            time: t,
            position: "aboveBar",
            shape: "circle",
            color: "#f59e0b",
            text: "WARN",
          });
          lastMarkI = i;
        }

        continue;
      }

      if (markerMode === "sto_ema" || markerMode === "both") {
        const a1 = cu && (k ?? 999) <= 35 && isUpTrend;
        const a2 = cd && (k ?? -999) >= 65 && isDownTrend;

        if (coolOk && a1) {
          markers.push({
            time: t,
            position: "belowBar",
            shape: "arrowUp",
            color: "#2563eb",
            text: "STO↑+EMA",
          });
          lastMarkI = i;
        } else if (coolOk && a2) {
          markers.push({
            time: t,
            position: "aboveBar",
            shape: "arrowDown",
            color: "#dc2626",
            text: "STO↓+EMA",
          });
          lastMarkI = i;
        }
      }

      if (markerMode === "bb_sto" || markerMode === "both") {
        const b1 =
          lower !== undefined &&
          prevLower !== undefined &&
          prev.c <= prevLower &&
          cur.c > lower &&
          cu &&
          (k ?? 999) <= 40;

        const b2 =
          upper !== undefined &&
          prevUpper !== undefined &&
          prev.c >= prevUpper &&
          cur.c < upper &&
          cd &&
          (k ?? -999) >= 60;

        if (coolOk && b1) {
          markers.push({
            time: t,
            position: "belowBar",
            shape: "circle",
            color: "#16a34a",
            text: "BB↓+STO↑",
          });
          lastMarkI = i;
        } else if (coolOk && b2) {
          markers.push({
            time: t,
            position: "aboveBar",
            shape: "circle",
            color: "#f59e0b",
            text: "BB↑+STO↓",
          });
          lastMarkI = i;
        }
      }
    }

    const priority = (m: SeriesMarker<UTCTimestamp>) => {
      if (m.text === "BREAK") return 100;
      if (m.text === "BUY") return 90;
      if (m.text === "WARN") return 80;
      if (m.text?.includes("BB↓")) return 30;
      if (m.text?.includes("STO↑")) return 20;
      return 10;
    };

    const dedup = new Map<number, SeriesMarker<UTCTimestamp>>();
    for (const m of markers) {
      const key = Number(m.time);
      const prev = dedup.get(key);
      if (!prev || priority(m) >= priority(prev)) dedup.set(key, m);
    }

    const finalMarkers = Array.from(dedup.values()).sort((a, b) => Number(a.time) - Number(b.time));

    try {
      sm.setMarkers(finalMarkers);
    } catch (e) {
      clientLog.warn("TradingView marker update failed", e);
    }
  }, [
    showMarkers,
    candles,
    candleTimeList,
    indicators?.ema,
    indicators?.bb20_2,
    indicators?.stoch14_3_3,
    markerMode,
  ]);

  /* =========================
     price chart hover -> parent day key
  ========================= */
  useEffect(() => {
    const chart = priceChartRef.current;
    if (!chart || !onHoverDayKeyChange) return;

    const handler = (param: MouseEventParams<Time>) => {
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

  /* =========================
    external hoveredDayKey -> price chart crosshair sync
  ========================= */
  useEffect(() => {
    const chart = priceChartRef.current;
    const series = candleSeriesRef.current;
    if (!chart || !series) return;

    syncingCrosshairRef.current = true;
    try {
      if (!hoveredDayKey) {
        safeClearCrosshair(chart);

        if (stochChartRef.current) safeClearCrosshair(stochChartRef.current);
        if (volumeChartRef.current) safeClearCrosshair(volumeChartRef.current);
        return;
      }

      const found = candleDayMap.get(hoveredDayKey);
      if (!found) {
        safeClearCrosshair(chart);

        if (stochChartRef.current) safeClearCrosshair(stochChartRef.current);
        if (volumeChartRef.current) safeClearCrosshair(volumeChartRef.current);
        return;
      }

      safeSetCrosshair(chart, found.time, found.close, series);

      if (showSubPanes) {
        safeSetCrosshair(
          stochChartRef.current,
          found.time,
          50,
          stochSeriesRef.current.k ?? stochSeriesRef.current.d ?? null
        );
        safeSetCrosshair(
          volumeChartRef.current,
          found.time,
          0,
          volumeSeriesRef.current
        );
      }
    } finally {
      queueMicrotask(() => {
        syncingCrosshairRef.current = false;
      });
    }
  }, [hoveredDayKey, candleDayMap, showSubPanes]);

  /* =========================
     Layout
  ========================= */
  return (
    <div ref={wrapRef} className={wrapperClass}>
      <div ref={priceElRef} className={paneBaseClass} />

      <div
        ref={stochElRef}
        className={paneBaseClass}
        style={{
          display: showSubPanes ? "block" : "none",
          borderTop: showSubPanes ? `1px solid ${chartTheme.grid}` : "none",
        }}
      />

      <div
        ref={volumeElRef}
        className={paneBaseClass}
        style={{
          display: showSubPanes ? "block" : "none",
          borderTop: showSubPanes ? `1px solid ${chartTheme.grid}` : "none",
        }}
      />
    </div>
  );
}

export default memo(TradingViewWidget);
