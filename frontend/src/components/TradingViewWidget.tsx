// frontend/src/components/TradingViewWidget.tsx
import React, { memo, useEffect, useMemo, useRef, useState } from "react";
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
  onRequestMoreHistory?: () => void;
  markerMode?: MarkerMode;
}

/** epoch ms / s 자동 보정 */
const toEpochSeconds = (t: number) => (t > 10_000_000_000 ? Math.floor(t / 1000) : t);
/** ISO string -> epoch seconds */
const isoToEpochSeconds = (iso: string) => Math.floor(new Date(iso).getTime() / 1000);

/* =========================
   Candle / Volume Mapper
========================= */

const mapCandles = (candles: Candle[]): CandlestickData<Time>[] =>
  candles
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as UTCTimestamp,
      open: Number((c as any).o),
      high: Number((c as any).h),
      low: Number((c as any).l),
      close: Number((c as any).c),
    }))
    .sort((a, b) => Number(a.time) - Number(b.time));

const mapVolumes = (candles: Candle[]): HistogramData<Time>[] =>
  candles
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as UTCTimestamp,
      value: Number((c as any).v),
      color:
        Number((c as any).c) >= Number((c as any).o)
          ? "rgba(239, 68, 68, 0.6)"
          : "rgba(59, 130, 246, 0.6)",
    }))
    .sort((a, b) => Number(a.time) - Number(b.time));

/* =========================
   Indicator mappers
========================= */

const toLineSeries = (points: { t: string; value: number | null }[]): LineData<Time>[] =>
  points
    .filter((p) => p.value !== null && p.value !== undefined)
    .map((p) => ({
      time: isoToEpochSeconds(p.t) as UTCTimestamp,
      value: Number(p.value),
    }));

const toEmaLineSeriesMap = (ema: Record<string, { t: string; value: number | null }[]>) => {
  const out: Record<string, LineData<Time>[]> = {};
  Object.entries(ema).forEach(([period, pts]) => {
    out[String(period)] = toLineSeries(pts ?? []);
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
    if (typeof ts.getVisibleRange === "function") return ts.getVisibleRange(); // { from, to }
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
   Align indicator times to candle times (핵심)
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

function TradingViewWidget({ candles, indicators, showSubPanes, onRequestMoreHistory, markerMode = "both" }: Props) {
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

  // ✅ v5 markers primitive
  const candleMarkersRef = useRef<ISeriesMarkersPluginApi<UTCTimestamp> | null>(null);

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

  // pane ready tick
  const [subPaneReadyTick, setSubPaneReadyTick] = useState(0);

  // candle time list (sorted epoch seconds)
  const candleTimeList = useMemo(() => {
    return (candles ?? [])
      .map((c) => toEpochSeconds(Number(c.t)))
      .filter((x) => Number.isFinite(x))
      .sort((a, b) => a - b);
  }, [candles]);

  const wrapperClass = useMemo(() => {
    if (showSubPanes) return "w-full h-full min-h-0 grid grid-rows-[6fr_2fr_2fr]";
    return "w-full h-full min-h-0 grid grid-rows-1";
  }, [showSubPanes]);

  const paneBaseClass = "min-h-0 w-full";

  /* =========================
     timeRange sync (timestamp 기반)
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
     Init Price Chart (항상 1회 생성)
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
      layout: { background: { color: "#ffffff" }, textColor: "#1f2937" },
      grid: { vertLines: { color: "#f3f4f6" }, horzLines: { color: "#f3f4f6" } },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { labelVisible: true },
        horzLine: { labelVisible: true },
      },
      rightPriceScale: { borderColor: "#e5e7eb" },
      timeScale: { timeVisible: true, visible: true },
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#ef4444",
      downColor: "#3b82f6",
      borderVisible: false,
      wickUpColor: "#ef4444",
      wickDownColor: "#3b82f6",
      priceFormat: { type: "price", precision: 0, minMove: 1 },
    });

    // ✅ attach markers primitive (v5)
    try {
      candleMarkersRef.current = createSeriesMarkers(candleSeries, []);
    } catch (e) {
      console.warn("[markers] createSeriesMarkers failed:", e);
      candleMarkersRef.current = null;
    }

    priceChartRef.current = chart;
    candleSeriesRef.current = candleSeries;

    // history load trigger (logical range는 여기서만 사용)
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

    // resize
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

      // remove indicator series
      try {
        emaSeriesRef.current.forEach((s) => chart.removeSeries(s));
        emaSeriesRef.current.clear();
        Object.values(bbSeriesRef.current).forEach((s) => s && chart.removeSeries(s));
        bbSeriesRef.current = {};
      } catch {}

      // detach markers primitive
      try {
        candleMarkersRef.current?.detach?.();
      } catch {}
      candleMarkersRef.current = null;

      chart.remove();
      priceChartRef.current = null;
      candleSeriesRef.current = null;
      didInitFitRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* =========================
     Toggle Sub Panes
  ========================= */
  useEffect(() => {
    const pc = priceChartRef.current;
    if (!pc) return;

    if (!showSubPanes) {
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

      return;
    }

    const rafId = requestAnimationFrame(() => {
      let createdAny = false;

      // stoch init
      if (!stochChartRef.current && stochElRef.current) {
        const el = stochElRef.current;
        const r = el.getBoundingClientRect();
        const w = Math.floor(r.width || el.clientWidth || 600);
        const h = Math.floor(r.height || el.clientHeight || 140);

        if (w >= 80 && h >= 60) {
          const sc = createChart(el, {
            width: w,
            height: h,
            layout: { background: { color: "#ffffff" }, textColor: "#1f2937" },
            grid: { vertLines: { color: "#f3f4f6" }, horzLines: { color: "#f3f4f6" } },
            crosshair: {
              mode: CrosshairMode.Normal,
              vertLine: { labelVisible: false },
              horzLine: { labelVisible: false },
            },
            rightPriceScale: { borderColor: "#e5e7eb" },
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

      // volume init
      if (!volumeChartRef.current && volumeElRef.current) {
        const el = volumeElRef.current;
        const r = el.getBoundingClientRect();
        const w = Math.floor(r.width || el.clientWidth || 600);
        const h = Math.floor(r.height || el.clientHeight || 120);

        if (w >= 80 && h >= 60) {
          const vc = createChart(el, {
            width: w,
            height: h,
            layout: { background: { color: "#ffffff" }, textColor: "#1f2937" },
            grid: { vertLines: { color: "#f3f4f6" }, horzLines: { color: "#f3f4f6" } },
            crosshair: {
              mode: CrosshairMode.Normal,
              vertLine: { labelVisible: false },
              horzLine: { labelVisible: false },
            },
            rightPriceScale: { borderColor: "#e5e7eb" },
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

      // range sync
      const unsubscribe = subscribeVisibleRangeChangeSafe(pc2, () => {
        if (syncingRangeRef.current) return;
        syncTimeRangeFromPrice();
      });

      // crosshair sync (time만 맞추면 됨)
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

      // initial sync
      syncTimeRangeFromPrice();

      return () => {
        try {
          unsubscribe();
        } catch {}
        try {
          pc2.unsubscribeCrosshairMove(onPc);
        } catch {}
      };
    });

    return () => cancelAnimationFrame(rafId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showSubPanes]);

  /* =========================
     wrapper resize: 창/전체화면 전환 안정화
  ========================= */
  useEffect(() => {
    const wrap = wrapRef.current;
    if (!wrap) return;

    const ro = new ResizeObserver(() => {
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candles]);

  /* =========================
     Volume data (sub pane)
  ========================= */
  useEffect(() => {
    if (!showSubPanes) return;
    const vs = volumeSeriesRef.current;
    if (!vs) return;
    if (!candles || candles.length === 0) return;

    vs.setData(mapVolumes(candles));
    volumeReadyRef.current = true;

    volumeChartRef.current?.timeScale().fitContent();
    syncTimeRangeFromPrice();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showSubPanes, subPaneReadyTick, candles]);

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

    const ema = indicators?.ema;
    if (!ema || Object.keys(ema).length === 0) return;

    const emaSeriesMap = toEmaLineSeriesMap(ema);

    Object.entries(emaSeriesMap).forEach(([period, data]) => {
      if (!data || data.length === 0) return;

      const color = period === "20" ? "#f59e0b" : period === "60" ? "#10b981" : "#6366f1";
      const series = chart.addSeries(LineSeries, { lineWidth: 2, color });
      series.setData(data);
      emaSeriesRef.current.set(period, series);
    });
  }, [indicators?.ema]);

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

    const bb = indicators?.bb20_2;
    if (!bb || bb.length === 0) return;

    const upper = chart.addSeries(LineSeries, { color: "rgba(59,130,246,0.6)", lineWidth: 1 });
    const mid = chart.addSeries(LineSeries, { color: "rgba(107,114,128,0.6)", lineWidth: 1 });
    const lower = chart.addSeries(LineSeries, { color: "rgba(59,130,246,0.6)", lineWidth: 1 });

    upper.setData(
      bb
        .filter((p) => p.upper !== null && p.upper !== undefined)
        .map((p) => ({ time: isoToEpochSeconds(p.t) as UTCTimestamp, value: Number(p.upper) }))
    );
    mid.setData(
      bb
        .filter((p) => p.mid !== null && p.mid !== undefined)
        .map((p) => ({ time: isoToEpochSeconds(p.t) as UTCTimestamp, value: Number(p.mid) }))
    );
    lower.setData(
      bb
        .filter((p) => p.lower !== null && p.lower !== undefined)
        .map((p) => ({ time: isoToEpochSeconds(p.t) as UTCTimestamp, value: Number(p.lower) }))
    );

    bbSeriesRef.current = { upper, mid, lower };
  }, [indicators?.bb20_2]);

  /* =========================
     STO (sub pane) - candle time 스냅 적용
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

    const st = indicators?.stoch14_3_3;
    if (!st || st.length === 0) return;
    if (!candleTimeList || candleTimeList.length === 0) return;

    const snappedTimes = alignTimesToCandles(st, candleTimeList);

    const kSeries = chart.addSeries(LineSeries, { color: "rgba(236,72,153,0.85)", lineWidth: 1 });
    const dSeries = chart.addSeries(LineSeries, { color: "rgba(34,197,94,0.85)", lineWidth: 1 });

    const kData = st
      .map((p, i) => ({ time: snappedTimes[i] as UTCTimestamp, value: p.k }))
      .filter((p) => p.value !== null && p.value !== undefined)
      .map((p) => ({ time: p.time, value: Number(p.value) }));

    const dData = st
      .map((p, i) => ({ time: snappedTimes[i] as UTCTimestamp, value: p.d }))
      .filter((p) => p.value !== null && p.value !== undefined)
      .map((p) => ({ time: p.time, value: Number(p.value) }));

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showSubPanes, subPaneReadyTick, indicators?.stoch14_3_3, candleTimeList]);
  /* =========================
   Markers (v5 primitive)
   - sto_ema: STO + EMA
   - bb_sto : BB + STO
   - both   : 둘 다
   - triple : 3개 필터 강조
     - BREAK : BB upper breakout + EMA uptrend + Volume confirm
     - ENTRY   : BB lower rebound + STO OS cross up + EMA uptrend
     - WARN  : (완화) ① BREAK 이후 실패/눌림 경고 ② 상단리젝+STO 약화(추세 무관) ③ (강) 하락추세+OB cross down
========================= */
useEffect(() => {
  const sm = candleMarkersRef.current;
  if (!sm) return;

  // 분석 전이면 마커 제거
  if (!showSubPanes) {
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

  // candle arrays (sorted)
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

  // snap maps
  const ema20Map = snapEmaPeriodToMap(indicators?.ema ?? null, "20", candleTimeList);
  const ema60Map = snapEmaPeriodToMap(indicators?.ema ?? null, "60", candleTimeList);

  const bb = indicators?.bb20_2 ?? [];
  const bbUpperMap = snapIsoToCandleTimeMap(bb, candleTimeList, (p) => p.upper);
  const bbMidMap = snapIsoToCandleTimeMap(bb, candleTimeList, (p) => p.mid);
  const bbLowerMap = snapIsoToCandleTimeMap(bb, candleTimeList, (p) => p.lower);

  const st = indicators?.stoch14_3_3 ?? [];
  const stKMap = snapIsoToCandleTimeMap(st, candleTimeList, (p) => p.k);
  const stDMap = snapIsoToCandleTimeMap(st, candleTimeList, (p) => p.d);

  // --- helper: cross check
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

  // --- density control
  const COOL_DOWN_BARS = 5; // KOSPI 1D면 5 정도가 무난
  let lastMarkI = -Infinity;

  // --- track last BREAK index (for break-fail warn window)
  let lastBreakI = -Infinity;
  const BREAK_FAIL_WINDOW = 5; // break 이후 1~5봉 내 실패/눌림 경고

  // --- volume confirm용: 20일 평균 거래량
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
    const mid = bbMidMap.get(cur.t);
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

    // =========================
    // triple (강조건 3필터)
    // =========================
    if (markerMode === "triple") {
      // (T1) ENTRY : BB lower rebound + STO OS cross up + EMA uptrend (엄격)
      const t1 =
        lower !== undefined &&
        prevLower !== undefined &&
        prev.c <= prevLower && // 전일 종가가 하단 밴드 아래(또는 닿음)
        cur.c > lower && // 금일 종가가 하단 밴드 위로 복귀
        cu && // K가 D를 상향 돌파
        (k ?? 999) <= 25 && // oversold(엄격)
        isUpTrend; // EMA20 >= EMA60

      // (T3) BREAK: upper 돌파 + 추세 + 거래량 확인 (엄격)
      const volOk =
        volSma20[i] !== null && Number.isFinite(volSma20[i] as number)
          ? cur.v >= (volSma20[i] as number) * 1.3
          : false;

      const t3 =
        upper !== undefined &&
        prevUpper !== undefined &&
        prev.c <= prevUpper && // 전일 상단밴드 아래/근처
        cur.c > upper && // 금일 상단밴드 상향 돌파
        isUpTrend &&
        volOk;

      // =========================
      // WARN 약화 전략 (EMA 상방에서도 경고)
      //  1) breakFailWarn: BREAK 이후 N봉 내 상단밴드 아래 복귀 + STO 약화
      //  2) t2weak: 상단 리젝 + STO 약화(추세 무관, 완화)
      //  3) t2strong: 기존 엄격(하락추세 + OB cross down + upper reject)
      // =========================

      // (W0) BREAK 직후 실패/눌림 경고: break 이후 1~N봉 내 upper 아래 복귀 + STO 약화
      const inBreakWindow = i - lastBreakI >= 1 && i - lastBreakI <= BREAK_FAIL_WINDOW;

      const stoWeak =
        cd || // 하향교차
        (prevK !== undefined && k !== undefined && k < prevK) || // K 하락
        ((k ?? 0) >= 80 && (prevK !== undefined && k !== undefined && k < prevK)); // 과열에서 꺾임 강화

      const breakFailWarn =
        inBreakWindow &&
        upper !== undefined &&
        cur.c < upper && // 상단밴드 아래로 복귀 = 실패/눌림
        stoWeak;

      // (W1) 상단 리젝 + STO 약화 (추세 무관, 완화)
      const t2weak =
        upper !== undefined &&
        prevUpper !== undefined &&
        prev.c >= prevUpper * 0.995 && // "upper 근처/상단"까지 허용
        cur.c < upper && // upper 아래로 복귀
        (cd || (prevK !== undefined && k !== undefined && k < prevK)) && // 교차 or K 하락
        (k ?? -999) >= 70; // 기존 75 -> 70 완화

      // (W2) 기존 엄격 경고(추세 반전 가능성): upper reject + OB cross down + EMA downtrend
      const t2strong =
        upper !== undefined &&
        prevUpper !== undefined &&
        prev.c >= prevUpper && // 전일 상단밴드 위(엄격)
        cur.c < upper &&
        cd &&
        (k ?? -999) >= 75 &&
        isDownTrend;

      // 우선순위: BREAK > ENTRY > WARN(강/약)
      // (주의) BREAK 후 바로 다음봉에 breakFailWarn가 떠도 쿨다운 때문에 막히면 의미가 없어서
      //        breakFailWarn는 coolOk 조건을 유지하되, 필요하면 COOL_DOWN_BARS를 줄이거나
      //        breakFailWarn만 예외로 처리할 수도 있음(현재는 동일 정책).
      if (coolOk && t3) {
        markers.push({
          time: t,
          position: "aboveBar",
          shape: "circle",
          color: "#7c3aed",
          text: "FORCE",
        });
        lastMarkI = i;
        lastBreakI = i; // break tracking
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
        // WARN은 텍스트는 동일하게 유지하고, 내부 우선순위는 breakFail > weak > strong(혹은 반대)로 조절 가능
        // 여기서는 breakFailWarn를 최우선으로 둠.
        markers.push({
          time: t,
          position: "aboveBar",
          shape: "circle",
          color: "#f59e0b",
          text: "WARN",
        });
        lastMarkI = i;
      }

      continue; // triple 모드면 아래 A/B는 스킵
    }

    // =========================
    // A) STO + EMA
    // =========================
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

    // =========================
    // B) BB + STO
    // =========================
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

  // dedup by time (priority)
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
    console.warn("[markers] setMarkers failed:", e);
  }
}, [
  showSubPanes,
  candles,
  candleTimeList,
  indicators?.ema,
  indicators?.bb20_2,
  indicators?.stoch14_3_3,
  markerMode,
]);

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
          borderTop: showSubPanes ? "1px solid #f1f5f9" : "none",
        }}
      />

      <div
        ref={volumeElRef}
        className={paneBaseClass}
        style={{
          display: showSubPanes ? "block" : "none",
          borderTop: showSubPanes ? "1px solid #f1f5f9" : "none",
        }}
      />
    </div>
  );
}

export default memo(TradingViewWidget);