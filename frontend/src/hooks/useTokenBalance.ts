import { useEffect, useState } from "react";
import { getTokenBalance, refreshTokenBalance, subscribeTokenBalance } from "../api/billingStore";
import { isLoggedIn } from "../utils/auth";

export function useTokenBalance(): number {
  const [balance, setBalance] = useState<number>(() => getTokenBalance());

  useEffect(() => {
    if (isLoggedIn()) {
      refreshTokenBalance().catch(() => {
        // 인증 상태 전환 중이면 기존 캐시를 유지한다.
      });
    }
    return subscribeTokenBalance(() => setBalance(getTokenBalance()));
  }, []);

  return balance;
}
