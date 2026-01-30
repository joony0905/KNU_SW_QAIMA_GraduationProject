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

/**
 * epoch time 보정
 * - 10자리: seconds → 그대로 사용
 * - 13자리: milliseconds → seconds로 변환
 */
const toEpochSeconds = (t: number) => {
  return t > 10_000_000_000 ? Math.floor(t / 1000) : t;
};

export const toChartCandles = (candles: Candle[]): ChartCandle[] =>
  candles
    .map((candle) => ({
      time: toEpochSeconds(Number(candle.t)),
      open: Number(candle.o),
      high: Number(candle.h),
      low: Number(candle.l),
      close: Number(candle.c),
    }))
    .sort((a, b) => a.time - b.time);

export const toChartVolumes = (candles: Candle[]): ChartVolume[] =>
  candles
    .map((candle) => ({
      time: toEpochSeconds(Number(candle.t)),
      value: Number(candle.v),
      color:
        Number(candle.c) >= Number(candle.o)
          ? "rgba(239, 68, 68, 0.6)" // 상승
          : "rgba(59, 130, 246, 0.6)", // 하락
    }))
    .sort((a, b) => a.time - b.time);
