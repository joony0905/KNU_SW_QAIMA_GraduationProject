import api from "./apiClient";
import type { ApiResponse } from "../types/common/api";

const LS_KEY = "qaima_token_balance";
const DEFAULT_BALANCE = 0;

const listeners = new Set<() => void>();

const readFromStorage = (): number => {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (raw === null) return DEFAULT_BALANCE;
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed < 0) return DEFAULT_BALANCE;
    return Math.floor(parsed);
  } catch {
    return DEFAULT_BALANCE;
  }
};

let cached: number | null = null;
let refreshPromise: Promise<number> | null = null;

type CreditLedgerResponse = {
  balanceAfter: number;
};

export const getTokenBalance = (): number => {
  if (cached === null) {
    cached = readFromStorage();
  }
  return cached;
};

export const setTokenBalance = (next: number): void => {
  const safe = Number.isFinite(next) && next >= 0 ? Math.floor(next) : 0;
  cached = safe;
  try {
    localStorage.setItem(LS_KEY, String(safe));
  } catch {
    // localStorage 불가 환경은 메모리 캐시만 사용
  }
  listeners.forEach((fn) => fn());
};

export const refreshTokenBalance = async (): Promise<number> => {
  if (!refreshPromise) {
    refreshPromise = api
      .get<ApiResponse<{ creditBalance: number }>>("/credits/balance")
      .then((res) => {
        const next = Number(res.data?.data?.creditBalance);
        const safe = Number.isFinite(next) && next >= 0 ? Math.floor(next) : 0;
        cached = safe;
        try {
          localStorage.setItem(LS_KEY, String(safe));
        } catch {
          // localStorage 불가 환경은 메모리 캐시만 사용
        }
        listeners.forEach((fn) => fn());
        return safe;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

export const tempChargeTokens = async (amount: number, reason = "TEMP_FRONTEND_CHARGE"): Promise<number> => {
  const safeAmount = Number.isFinite(amount) && amount > 0 ? Math.floor(amount) : 0;
  if (safeAmount <= 0) {
    return getTokenBalance();
  }

  const res = await api.post<ApiResponse<CreditLedgerResponse>>("/credits/temp-charge", {
    amount: safeAmount,
    reason,
  });
  const next = Number(res.data?.data?.balanceAfter);
  const safe = Number.isFinite(next) && next >= 0 ? Math.floor(next) : 0;
  cached = safe;
  try {
    localStorage.setItem(LS_KEY, String(safe));
  } catch {
    // localStorage 불가 환경은 메모리 캐시만 사용
  }
  listeners.forEach((fn) => fn());
  return safe;
};

export const addTokens = (delta: number): number => {
  const next = getTokenBalance() + Math.floor(delta);
  setTokenBalance(next);
  return next;
};

export const subscribeTokenBalance = (fn: () => void): (() => void) => {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
};
