// frontend/src/components/TradingViewWidget.tsx
import React, { memo, useEffect, useMemo, useRef, useState } from "react";
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  LineSeries,
  CrosshairMode,
  type IChartApi,
  type ISeriesApi,
  type Time,
  type CandlestickData,
  type HistogramData,
  type LineData,
  type MouseEventParams,
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

interface Props {
  candles: Candle[];
  indicators?: IndicatorData | null;
  showSubPanes: boolean; // 분석 전 false, 분석 후 true
  onRequestMoreHistory?: () => void;
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
      time: toEpochSeconds(Number(c.t)) as Time,
      open: Number((c as any).o),
      high: Number((c as any).h),
      low: Number((c as any).l),
      close: Number((c as any).c),
    }))
    .sort((a, b) => Number(a.time) - Number(b.time));

const mapVolumes = (candles: Candle[]): HistogramData<Time>[] =>
  candles
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as Time,
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
      time: isoToEpochSeconds(p.t) as Time,
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
  } catch {
    // ensureNotNull 방어
  }
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
   - indicator t(ISO)가 candle t(epoch)와 조금이라도 다르면
     과거로 갈수록 어긋남이 커져 보일 수 있다.
   - 해결: indicator 각 포인트의 시간을 "가장 가까운 candle time"으로 스냅
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

/* =========================
   Component
========================= */

function TradingViewWidget({ candles, indicators, showSubPanes, onRequestMoreHistory }: Props) {
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

  // indicators registry
  const emaSeriesRef = useRef<Map<string, ISeriesApi<"Line">>>(new Map());
  const bbSeriesRef = useRef<{ upper?: ISeriesApi<"Line">; mid?: ISeriesApi<"Line">; lower?: ISeriesApi<"Line"> }>({});
  const stochSeriesRef = useRef<{ k?: ISeriesApi<"Line">; d?: ISeriesApi<"Line">; ob80?: ISeriesApi<"Line">; os20?: ISeriesApi<"Line"> }>({});

  // ready flags (Value is null 방지)
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

      try {
        emaSeriesRef.current.forEach((s) => chart.removeSeries(s));
        emaSeriesRef.current.clear();
        Object.values(bbSeriesRef.current).forEach((s) => s && chart.removeSeries(s));
        bbSeriesRef.current = {};
      } catch {}

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
              vertLine: { labelVisible: false }, // sub pane 라벨 숨김
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
              vertLine: { labelVisible: false }, // sub pane 라벨 숨김
              horzLine: { labelVisible: false },
            },
            rightPriceScale: { borderColor: "#e5e7eb" },
            timeScale: { timeVisible: true, visible: true }, // 맨 아래만 라벨
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
        .map((p) => ({ time: isoToEpochSeconds(p.t) as Time, value: Number(p.upper) }))
    );
    mid.setData(
      bb
        .filter((p) => p.mid !== null && p.mid !== undefined)
        .map((p) => ({ time: isoToEpochSeconds(p.t) as Time, value: Number(p.mid) }))
    );
    lower.setData(
      bb
        .filter((p) => p.lower !== null && p.lower !== undefined)
        .map((p) => ({ time: isoToEpochSeconds(p.t) as Time, value: Number(p.lower) }))
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
      .map((p, i) => ({ time: snappedTimes[i] as Time, value: p.k }))
      .filter((p) => p.value !== null && p.value !== undefined)
      .map((p) => ({ time: p.time, value: Number(p.value) }));

    const dData = st
      .map((p, i) => ({ time: snappedTimes[i] as Time, value: p.d }))
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
     Layout
     - 분석 전: price만 보임
     - 분석 후: 6:2:2 (grid)
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