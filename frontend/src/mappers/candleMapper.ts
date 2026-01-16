import type { Candle } from "../types/candle";

export interface ChartCandle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface ChartVolume {
  time: number;
  value: number;
  color: string;
}

export const toChartCandles = (candles: Candle[]): ChartCandle[] =>
  candles
    .map((candle) => ({
      time: candle.t,
      open: candle.o,
      high: candle.h,
      low: candle.l,
      close: candle.c,
    }))
    .sort((a, b) => a.time - b.time);

export const toChartVolumes = (candles: Candle[]): ChartVolume[] =>
  candles
    .map((candle) => ({
      time: candle.t,
      value: candle.v,
      color:
        candle.c >= candle.o ? "rgba(239, 68, 68, 0.6)" : "rgba(59, 130, 246, 0.6)",
    }))
    .sort((a, b) => a.time - b.time);
