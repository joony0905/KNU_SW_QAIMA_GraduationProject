export const isoToUtcTimestamp = (iso: string): number => {
  return Math.floor(new Date(iso).getTime() / 1000);
};

export const toLineSeries = (points: { t: string; value: number | null }[]): { time: number; value: number }[] => {
  return points
    .filter((point) => point.value !== null)
    .map((point) => ({
      time: isoToUtcTimestamp(point.t),
      value: point.value as number,
    }));
};
