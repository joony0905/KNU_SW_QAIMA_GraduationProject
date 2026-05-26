// frontend/src/components/TokenBalanceBadge.tsx
import { Coins } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useTokenBalance } from "../hooks/useTokenBalance";
import { useBilling } from "../contexts/BillingContext";
export default function TokenBalanceBadge() {
  const { t } = useTranslation("common");
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
      aria-label={t("token.badgeAriaLabel")}
    >
      <Coins size={14} className="text-accent" />
      <span className="text-xs sm:text-sm font-semibold tabular font-mono">
        {balance.toLocaleString()}
      </span>
      <span className="text-[10px] sm:text-xs text-accent/70">{t("token.label")}</span>
    </button>
  );
}
