import React, { memo, useEffect, useRef } from "react";
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  CrosshairMode,
  type IChartApi,
  type ISeriesApi,
  type CandlestickData,
  type HistogramData,
  type Time,
} from "lightweight-charts";

import type { Candle } from "../types/candle";

/** epoch ms / s 자동 보정 */
const toEpochSeconds = (t: number) =>
  t > 10_000_000_000 ? Math.floor(t / 1000) : t;

const mapCandles = (candles: Candle[]): CandlestickData<Time>[] =>
  candles
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as Time,
      open: Number(c.o),
      high: Number(c.h),
      low: Number(c.l),
      close: Number(c.c),
    }))
    .sort((a, b) => Number(a.time) - Number(b.time));

const mapVolumes = (candles: Candle[]): HistogramData<Time>[] =>
  candles
    .map((c) => ({
      time: toEpochSeconds(Number(c.t)) as Time,
      value: Number(c.v),
      color:
        Number(c.c) >= Number(c.o)
          ? "rgba(239, 68, 68, 0.6)"
          : "rgba(59, 130, 246, 0.6)",
    }))
    .sort((a, b) => Number(a.time) - Number(b.time));

interface Props {
  candles: Candle[];
}

function TradingViewWidget({ candles }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // 기존 차트 제거 (중복 방지)
    containerRef.current.innerHTML = "";

    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth || 600,
      height: containerRef.current.clientHeight || 420,
      layout: {
        background: { color: "#ffffff" },
        textColor: "#1f2937",
      },
      grid: {
        vertLines: { color: "#f3f4f6" },
        horzLines: { color: "#f3f4f6" },
      },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: { borderColor: "#e5e7eb" },
      timeScale: { timeVisible: true },
      watermark: { visible: false }, // 로고 제거
    });

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#ef4444",
      downColor: "#3b82f6",
      borderVisible: false,
      wickUpColor: "#ef4444",
      wickDownColor: "#3b82f6",
      priceFormat: {
        type: "price",
        precision: 0,
        minMove: 1, // 원화 정수
      },
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "",
      scaleMargins: { top: 0.8, bottom: 0 },
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;

    return () => {
      chart.remove();
      chartRef.current = null;
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, []);

  // 데이터 주입
  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return;
    if (!candles || candles.length === 0) return;

    candleSeriesRef.current.setData(mapCandles(candles));
    volumeSeriesRef.current.setData(mapVolumes(candles));
    chartRef.current?.timeScale().fitContent();
  }, [candles]);

  return (
    <div
      ref={containerRef}
      style={{ width: "100%", height: 420 }} // 부모 높이 확정 필수
    />
  );
}

export default memo(TradingViewWidget);
