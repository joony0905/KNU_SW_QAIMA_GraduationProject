import React, { useEffect, useRef, memo } from "react";
import type { Candle } from "../types/candle";
import { toChartCandles, toChartVolumes } from "../mappers/candleMapper";

declare global {
  interface Window {
    LightweightCharts?: {
      createChart: (container: HTMLElement, options: Record<string, unknown>) => {
        addCandlestickSeries: (options?: Record<string, unknown>) => {
          setData: (data: Array<Record<string, unknown>>) => void;
        };
        addHistogramSeries: (options?: Record<string, unknown>) => {
          setData: (data: Array<Record<string, unknown>>) => void;
        };
        timeScale: () => { fitContent: () => void };
        applyOptions: (options: Record<string, unknown>) => void;
        remove: () => void;
      };
    };
  }
}

interface TradingViewWidgetProps {
  candles: Candle[];
}

const loadLightweightCharts = () =>
  new Promise<NonNullable<Window["LightweightCharts"]>>((resolve, reject) => {
    if (window.LightweightCharts) {
      resolve(window.LightweightCharts);
      return;
    }

    const script = document.createElement("script");
    script.src =
      "https://unpkg.com/lightweight-charts/dist/lightweight-charts.standalone.production.js";
    script.async = true;
    script.onload = () => {
      if (window.LightweightCharts) {
        resolve(window.LightweightCharts);
      } else {
        reject(new Error("Lightweight Charts 로드 실패"));
      }
    };
    script.onerror = () => reject(new Error("Lightweight Charts 스크립트 에러"));
    document.body.appendChild(script);
  });

function TradingViewWidget({ candles }: TradingViewWidgetProps) {
  const container = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<ReturnType<
    NonNullable<Window["LightweightCharts"]>["createChart"]
  > | null>(null);
  const candleSeriesRef = useRef<{
    setData: (data: Array<Record<string, unknown>>) => void;
  } | null>(null);
  const volumeSeriesRef = useRef<{
    setData: (data: Array<Record<string, unknown>>) => void;
  } | null>(null);

  useEffect(() => {
    let resizeObserver: ResizeObserver | null = null;
    let isMounted = true;

    const initChart = async () => {
      if (!container.current) return;
      try {
        const charts = await loadLightweightCharts();
        if (!isMounted || !container.current) return;

        chartRef.current = charts.createChart(container.current, {
          layout: {
            background: { color: "#ffffff" },
            textColor: "#1f2937",
          },
          grid: {
            vertLines: { color: "#f3f4f6" },
            horzLines: { color: "#f3f4f6" },
          },
          timeScale: { timeVisible: true },
          rightPriceScale: { borderColor: "#e5e7eb" },
        });

        candleSeriesRef.current = chartRef.current.addCandlestickSeries({
          upColor: "#ef4444",
          downColor: "#3b82f6",
          borderVisible: false,
          wickUpColor: "#ef4444",
          wickDownColor: "#3b82f6",
        });

        volumeSeriesRef.current = chartRef.current.addHistogramSeries({
          color: "rgba(148, 163, 184, 0.6)",
          priceFormat: {
            type: "volume",
          },
          priceScaleId: "",
          scaleMargins: {
            top: 0.8,
            bottom: 0,
          },
        });

        resizeObserver = new ResizeObserver(() => {
          if (!chartRef.current || !container.current) return;
          chartRef.current.applyOptions({
            width: container.current.clientWidth,
            height: container.current.clientHeight,
          });
        });
        resizeObserver.observe(container.current);
      } catch (error) {
        console.error(error);
      }
    };

    initChart();

    return () => {
      isMounted = false;
      resizeObserver?.disconnect();
      chartRef.current?.remove();
      chartRef.current = null;
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return;
    const candleData = toChartCandles(candles);
    const volumeData = toChartVolumes(candles);

    candleSeriesRef.current.setData(candleData);
    volumeSeriesRef.current.setData(volumeData);
    chartRef.current?.timeScale().fitContent();
  }, [candles]);

  return (
    <div
      ref={container}
      className="tradingview-widget-container"
      style={{ width: "100%", height: "100%" }}
    />
  );
}

export default memo(TradingViewWidget);
