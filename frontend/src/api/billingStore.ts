// 토큰 잔량을 보관하는 mock 스토어.
// 백엔드 결제 API 연동 전까지 localStorage 기반으로 동작한다.

const LS_KEY = "qaima_token_balance";
const DEFAULT_BALANCE = 50;

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
