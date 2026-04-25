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
        border border-sky-200 bg-sky-50
        text-sky-700
        hover:bg-sky-100 hover:border-sky-300
        transition-colors
      "
      aria-label="토큰 잔량 확인 및 충전"
    >
      <Coins size={14} className="text-sky-600" />
      <span className="text-xs sm:text-sm font-semibold tabular-nums">
        {balance.toLocaleString()}
      </span>
      <span className="text-[10px] sm:text-xs text-sky-500">토큰</span>
    </button>
  );
}
