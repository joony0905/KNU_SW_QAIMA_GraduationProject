// frontend/src/components/TokenBalanceBadge.tsx
import { Coins } from "lucide-react";
import { useTokenBalance } from "../hooks/useTokenBalance";
import { useBilling } from "../contexts/BillingContext";

export default function TokenBalanceBadge() {
  const balance = useTokenBalance();
  const { openBilling } = useBilling();

  return (
    <button
      type="button"
      onClick={openBilling}
      className="
        inline-flex items-center gap-1.5
        px-3 py-1.5
        rounded-full
        border border-accent/30 bg-accent-soft
        text-accent
        hover:bg-accent/15 hover:border-accent/50
        transition-colors
      "
      aria-label="토큰 잔량 확인 및 충전"
    >
      <Coins size={14} className="text-accent" />
      <span className="text-xs sm:text-sm font-semibold tabular font-mono">
        {balance.toLocaleString()}
      </span>
      <span className="text-[10px] sm:text-xs text-accent/70">토큰</span>
    </button>
  );
}
