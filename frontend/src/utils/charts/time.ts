export const isoToUtcTimestamp = (iso: string): number => {
  return Math.floor(new Date(iso).getTime() / 1000);
};