import { useEffect, useState } from "react";
import { Coins, X, Check, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useTokenBalance } from "../hooks/useTokenBalance";
import { tempChargeTokens } from "../api/billingStore";
import { isLoggedIn } from "../utils/auth";

type Props = {
  isOpen: boolean;
  onClose: () => void;
};

type SubscriptionPlan = "MONTHLY" | "YEARLY";

type BulkTier = {
  tokens: number;
  price: number;
  pricePerToken: number;
  discountLabel?: string;
  highlight?: boolean;
};

const BULK_TIERS: BulkTier[] = [
  { tokens: 100, price: 1500, pricePerToken: 15 },
  { tokens: 500, price: 6500, pricePerToken: 13, discountLabel: "13% 할인" },
  { tokens: 1000, price: 12000, pricePerToken: 12, discountLabel: "20% 할인", highlight: true },
  { tokens: 3000, price: 33000, pricePerToken: 11, discountLabel: "27% 할인" },
  { tokens: 5000, price: 50000, pricePerToken: 10, discountLabel: "33% 할인" },
];

export default function BillingModal({ isOpen, onClose }: Props) {
  const { t } = useTranslation("billingModal");
  const balance = useTokenBalance();
  const [tab, setTab] = useState<"SUBSCRIPTION" | "BULK">("SUBSCRIPTION");
  const [plan, setPlan] = useState<SubscriptionPlan>("YEARLY");
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) { setToast(null); }
  }, [isOpen]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const loggedIn = isLoggedIn();

  const handlePurchase = async (tokens: number, label: string) => {
    if (!loggedIn) { setToast(t("purchase.loginFirst")); return; }
    try {
      await tempChargeTokens(tokens);
      setToast(t("purchase.success", { label, tokens: tokens.toLocaleString() }));
    } catch {
      setToast(t("purchase.failed"));
    }
  };

  const PLAN_KEYS: SubscriptionPlan[] = ["MONTHLY", "YEARLY"];

  return (
    <div
      className="fixed inset-0 z-[120] bg-black/50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-2xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between px-5 sm:px-6 py-4 border-b border-line">
          <div className="flex items-center gap-2">
            <Sparkles size={20} className="text-accent" />
            <h2 className="text-base sm:text-lg font-semibold text-ink">{t("title")}</h2>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-bg-sunk text-ink-3 hover:text-ink transition-colors"
            aria-label={t("closeLabel")}
          >
            <X size={18} />
          </button>
        </div>

        {/* 잔량 / 탭 */}
        <div className="px-5 sm:px-6 pt-4 pb-3 flex flex-col gap-3 border-b border-line">
          <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-gradient-to-r from-accent-soft to-surface border border-accent/20">
            <div className="flex items-center gap-2">
              <Coins size={18} className="text-accent" />
              <span className="text-sm text-ink-2">{t("balance.label")}</span>
            </div>
            <span className="text-lg sm:text-xl font-bold text-sky-700">
              {balance.toLocaleString()}
              <span className="ml-1 text-sm font-medium text-ink-3">{t("balance.unit")}</span>
            </span>
          </div>

          <div className="flex gap-1 bg-bg-sunk rounded-lg p-1">
            <button
              onClick={() => setTab("SUBSCRIPTION")}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${tab === "SUBSCRIPTION" ? "bg-accent text-white shadow-sm" : "text-ink-3 hover:text-ink"}`}
            >
              {t("tabs.subscription")}
            </button>
            <button
              onClick={() => setTab("BULK")}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${tab === "BULK" ? "bg-accent text-white shadow-sm" : "text-ink-3 hover:text-ink"}`}
            >
              {t("tabs.bulk")}
            </button>
          </div>
        </div>

        {/* 본문 */}
        <div className="flex-1 overflow-y-auto px-5 sm:px-6 py-5">
          {tab === "SUBSCRIPTION" && (
            <div className="flex flex-col gap-4">
              {/* 월/연 토글 */}
              <div className="flex gap-2">
                {PLAN_KEYS.map((key) => {
                  const isActive = plan === key;
                  const label = t(`plans.${key}.label` as "plans.MONTHLY.label");
                  const perMonth = t(`plans.${key}.perMonth` as "plans.MONTHLY.perMonth");
                  const badge = key === "YEARLY" ? t("plans.YEARLY.badge") : undefined;
                  return (
                    <button
                      key={key}
                      onClick={() => setPlan(key)}
                      className={`flex-1 px-4 py-3 rounded-xl border text-left transition-colors ${isActive ? "border-accent bg-accent-soft" : "border-line bg-surface hover:border-line-strong"}`}
                    >
                      <div className="flex items-center justify-between">
                        <span className={`text-sm font-semibold ${isActive ? "text-accent-ink" : "text-ink-2"}`}>{label}</span>
                        {badge && (
                          <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[11px] font-semibold">{badge}</span>
                        )}
                      </div>
                      <p className="text-xs text-ink-3 mt-1">{perMonth}</p>
                    </button>
                  );
                })}
              </div>

              {/* 선택 카드 */}
              <div className="border border-line rounded-2xl p-5 sm:p-6 flex flex-col gap-4 bg-surface">
                <div className="flex items-end gap-2">
                  <span className="text-3xl sm:text-4xl font-bold text-ink">
                    {plan === "MONTHLY" ? "9,900" : "99,000"}
                  </span>
                  <span className="text-sm text-ink-3 mb-1">
                    {t(`plans.${plan}.unit` as "plans.MONTHLY.unit")}
                  </span>
                </div>

                <div className="flex flex-col gap-2">
                  {(t(`plans.${plan}.highlights` as "plans.MONTHLY.highlights", { returnObjects: true }) as string[]).map((line) => (
                    <div key={line} className="flex items-start gap-2">
                      <Check size={16} className="text-accent flex-shrink-0 mt-0.5" />
                      <span className="text-sm text-ink-2">{line}</span>
                    </div>
                  ))}
                </div>

                <button
                  onClick={() => handlePurchase(plan === "MONTHLY" ? 1000 : 12000, t(`plans.${plan}.label` as "plans.MONTHLY.label"))}
                  disabled={!loggedIn}
                  className="w-full py-3 rounded-xl bg-accent text-white text-sm font-semibold hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
                >
                  {loggedIn
                    ? t("purchase.button", { label: t(`plans.${plan}.label` as "plans.MONTHLY.label") })
                    : t("purchase.loginRequired")}
                </button>
              </div>
            </div>
          )}

          {tab === "BULK" && (
            <div className="flex flex-col gap-3">
              <p className="text-xs text-ink-3">{t("bulk.description")}</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {BULK_TIERS.map((tier) => (
                  <button
                    key={tier.tokens}
                    onClick={() => handlePurchase(tier.tokens, t("bulk.tierLabel", { tokens: tier.tokens.toLocaleString() }))}
                    disabled={!loggedIn}
                    className={`relative text-left rounded-xl border p-4 transition-all ${
                      tier.highlight
                        ? "border-accent bg-accent-soft hover:bg-accent/15"
                        : "border-line bg-surface hover:border-accent/40 hover:bg-accent-soft/40"
                    } disabled:opacity-50 disabled:cursor-not-allowed`}
                  >
                    {tier.highlight && (
                      <span className="absolute top-3 right-3 px-2 py-0.5 rounded-full bg-accent text-white text-[10px] font-bold">BEST</span>
                    )}
                    <div className="flex items-center gap-1.5">
                      <Coins size={16} className="text-accent" />
                      <span className="text-base font-bold text-ink">
                        {tier.tokens.toLocaleString()} {t("bulk.tokenUnit")}
                      </span>
                    </div>
                    <div className="mt-2 flex items-baseline gap-1">
                      <span className="text-xl font-bold text-ink">{tier.price.toLocaleString()}</span>
                      <span className="text-xs text-ink-3">{t("bulk.wonUnit")}</span>
                    </div>
                    <div className="mt-1 flex items-center justify-between text-[11px]">
                      <span className="text-ink-3">{t("bulk.perToken", { price: tier.pricePerToken })}</span>
                      {tier.discountLabel && (
                        <span className="text-amber-600 font-semibold">{tier.discountLabel}</span>
                      )}
                    </div>
                  </button>
                ))}
              </div>
              {!loggedIn && (
                <p className="text-xs text-ink-4 text-center pt-1">{t("bulk.loginRequired")}</p>
              )}
            </div>
          )}
        </div>

        {/* 토스트 */}
        {toast && (
          <div className="px-5 sm:px-6 pb-4">
            <div className="px-4 py-2.5 rounded-lg bg-accent text-white text-sm text-center">{toast}</div>
          </div>
        )}
      </div>
    </div>
  );
}
