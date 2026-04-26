import { useEffect, useState } from "react";
import { getTokenBalance, subscribeTokenBalance } from "../api/billingStore";

export function useTokenBalance(): number {
  const [balance, setBalance] = useState<number>(() => getTokenBalance());

  useEffect(() => {
    return subscribeTokenBalance(() => setBalance(getTokenBalance()));
  }, []);

  return balance;
}
