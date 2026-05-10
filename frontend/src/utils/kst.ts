const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

type DateLike = Date | string | number;

const pad2 = (value: number) => String(value).padStart(2, "0");

const toDate = (input?: DateLike): Date => {
  if (input instanceof Date) {
    return new Date(input.getTime());
  }
  return new Date(input ?? Date.now());
};

const toKstShiftedDate = (input?: DateLike): Date =>
  new Date(toDate(input).getTime() + KST_OFFSET_MS);

const fromKstShiftedDate = (shifted: Date): Date =>
  new Date(shifted.getTime() - KST_OFFSET_MS);

export const shiftKstDays = (input: DateLike, days: number): Date => {
  const shifted = toKstShiftedDate(input);
  shifted.setUTCDate(shifted.getUTCDate() + days);
  return fromKstShiftedDate(shifted);
};

export const shiftKstMonths = (input: DateLike, months: number): Date => {
  const shifted = toKstShiftedDate(input);
  shifted.setUTCMonth(shifted.getUTCMonth() + months);
  return fromKstShiftedDate(shifted);
};

export const shiftKstYears = (input: DateLike, years: number): Date => {
  const shifted = toKstShiftedDate(input);
  shifted.setUTCFullYear(shifted.getUTCFullYear() + years);
  return fromKstShiftedDate(shifted);
};

export const formatKstDate = (input?: DateLike): string => {
  const shifted = toKstShiftedDate(input);
  const year = shifted.getUTCFullYear();
  const month = pad2(shifted.getUTCMonth() + 1);
  const day = pad2(shifted.getUTCDate());
  return `${year}-${month}-${day}`;
};

export const formatKstOffsetDateTime = (input?: DateLike): string => {
  const shifted = toKstShiftedDate(input);
  const year = shifted.getUTCFullYear();
  const month = pad2(shifted.getUTCMonth() + 1);
  const day = pad2(shifted.getUTCDate());
  const hours = pad2(shifted.getUTCHours());
  const minutes = pad2(shifted.getUTCMinutes());
  const seconds = pad2(shifted.getUTCSeconds());
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}+09:00`;
};

export const formatKstDateTimeDisplay = (value?: string | null): string => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
};
