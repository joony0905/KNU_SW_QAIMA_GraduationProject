import { isoToUtcTimestamp } from "./time";

type Point = { t: string; value: number | null };
type EmaDict = Record<string, Point[]>;

export const toLineSeries = (points: Point[] = []) => {
  return points
    .filter(p => p.value !== null)
    .map(p => ({
      time: isoToUtcTimestamp(p.t),
      value: p.value as number,
    }));
};

export const toEmaLineSeriesMap = (ema?: EmaDict) => {
  if (!ema) return {};

  return Object.fromEntries(
    Object.entries(ema).map(([period, points]) => [
      period,
      toLineSeries(points),
    ])
  );
};