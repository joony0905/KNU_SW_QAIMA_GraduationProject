import { useEffect, useState } from "react";
import { Coins, X, Check, Sparkles } from "lucide-react";
import { useTokenBalance } from "../hooks/useTokenBalance";
import { tempChargeTokens } from "../api/billingStore";
import { isLoggedIn } from "../utils/auth";

type Props = {
  isOpen: boolean;
  onClose: () => void;
};

type SubscriptionPlan = "MONTHLY" | "YEARLY";

type SubscriptionInfo = {
  key: SubscriptionPlan;
  label: string;
  priceLabel: string;
  unit: string;
  tokens: number;
  perMonthLabel: string;
  badge?: string;
  highlights: string[];
};

type BulkTier = {
  tokens: number;
  price: number;
  pricePerToken: number;
  discountLabel?: string;
  highlight?: boolean;
};

const SUBSCRIPTION_PLANS: Record<SubscriptionPlan, SubscriptionInfo> = {
  MONTHLY: {
    key: "MONTHLY",
    label: "월간 구독",
    priceLabel: "9,900",
    unit: "원 / 월",
    tokens: 1000,
    perMonthLabel: "월 9,900원",
    highlights: [
      "매월 1,000 토큰 자동 충전",
      "모든 분석 기능 무제한 이용",
      "언제든지 해지 가능",
    ],
  },
  YEARLY: {
    key: "YEARLY",
    label: "연간 구독",
    priceLabel: "99,000",
    unit: "원 / 년",
    tokens: 12000,
    perMonthLabel: "월 8,250원 (약 17% 할인)",
    badge: "2개월 무료",
    highlights: [
      "연 12,000 토큰 (월 환산 1,000개)",
      "월간 대비 약 17% 절약",
      "프리미엄 모델 우선 사용",
    ],
  },
};

const BULK_TIERS: BulkTier[] = [
  { tokens: 100, price: 1500, pricePerToken: 15 },
  { tokens: 500, price: 6500, pricePerToken: 13, discountLabel: "13% 할인" },
  { tokens: 1000, price: 12000, pricePerToken: 12, discountLabel: "20% 할인", highlight: true },
  { tokens: 3000, price: 33000, pricePerToken: 11, discountLabel: "27% 할인" },
  { tokens: 5000, price: 50000, pricePerToken: 10, discountLabel: "33% 할인" },
];

export default function BillingModal({ isOpen, onClose }: Props) {
  const balance = useTokenBalance();
  const [tab, setTab] = useState<"SUBSCRIPTION" | "BULK">("SUBSCRIPTION");
  const [plan, setPlan] = useState<SubscriptionPlan>("YEARLY");
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      setToast(null);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const loggedIn = isLoggedIn();

  const handlePurchase = async (tokens: number, label: string) => {
    if (!loggedIn) {
      setToast("로그인 후 결제할 수 있어요.");
      return;
    }
    try {
      await tempChargeTokens(tokens);
      setToast(`${label} 임시 충전 완료 · ${tokens.toLocaleString()} 토큰`);
    } catch {
      setToast("토큰 충전에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
  };

  const selectedPlan = SUBSCRIPTION_PLANS[plan];

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
            <h2 className="text-base sm:text-lg font-semibold text-ink">
              요금제 / 토큰 충전
            </h2>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-bg-sunk text-ink-3 hover:text-ink transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>

        {/* 잔량 / 탭 */}
        <div className="px-5 sm:px-6 pt-4 pb-3 flex flex-col gap-3 border-b border-line">
          <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-gradient-to-r from-accent-soft to-surface border border-accent/20">
            <div className="flex items-center gap-2">
              <Coins size={18} className="text-accent" />
              <span className="text-sm text-ink-2">현재 보유 토큰</span>
            </div>
            <span className="text-lg sm:text-xl font-bold text-sky-700">
              {balance.toLocaleString()}
              <span className="ml-1 text-sm font-medium text-ink-3">토큰</span>
            </span>
          </div>

          <div className="flex gap-1 bg-bg-sunk rounded-lg p-1">
            <button
              onClick={() => setTab("SUBSCRIPTION")}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${
                tab === "SUBSCRIPTION"
                  ? "bg-accent text-white shadow-sm"
                  : "text-ink-3 hover:text-ink"
              }`}
            >
              구독제
            </button>
            <button
              onClick={() => setTab("BULK")}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${
                tab === "BULK"
                  ? "bg-accent text-white shadow-sm"
                  : "text-ink-3 hover:text-ink"
              }`}
            >
              토큰 충전
            </button>
          </div>
        </div>

        {/* 본문 */}
        <div className="flex-1 overflow-y-auto px-5 sm:px-6 py-5">
          {tab === "SUBSCRIPTION" && (
            <div className="flex flex-col gap-4">
              {/* 월/연 토글 */}
              <div className="flex gap-2">
                {(["MONTHLY", "YEARLY"] as const).map((key) => {
                  const item = SUBSCRIPTION_PLANS[key];
                  const isActive = plan === key;
                  return (
                    <button
                      key={key}
                      onClick={() => setPlan(key)}
                      className={`flex-1 px-4 py-3 rounded-xl border text-left transition-colors ${
                        isActive
                          ? "border-accent bg-accent-soft"
                          : "border-line bg-surface hover:border-line-strong"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span
                          className={`text-sm font-semibold ${
                            isActive ? "text-accent-ink" : "text-ink-2"
                          }`}
                        >
                          {item.label}
                        </span>
                        {item.badge && (
                          <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[11px] font-semibold">
                            {item.badge}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-ink-3 mt-1">
                        {item.perMonthLabel}
                      </p>
                    </button>
                  );
                })}
              </div>

              {/* 선택 카드 */}
              <div className="border border-line rounded-2xl p-5 sm:p-6 flex flex-col gap-4 bg-surface">
                <div className="flex items-end gap-2">
                  <span className="text-3xl sm:text-4xl font-bold text-ink">
                    {selectedPlan.priceLabel}
                  </span>
                  <span className="text-sm text-ink-3 mb-1">
                    {selectedPlan.unit}
                  </span>
                </div>

                <div className="flex flex-col gap-2">
                  {selectedPlan.highlights.map((line) => (
                    <div key={line} className="flex items-start gap-2">
                      <Check
                        size={16}
                        className="text-accent flex-shrink-0 mt-0.5"
                      />
                      <span className="text-sm text-ink-2">{line}</span>
                    </div>
                  ))}
                </div>

                <button
                  onClick={() =>
                    handlePurchase(selectedPlan.tokens, selectedPlan.label)
                  }
                  disabled={!loggedIn}
                  className="w-full py-3 rounded-xl bg-accent text-white text-sm font-semibold hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
                >
                  {loggedIn ? `${selectedPlan.label} 결제하기` : "로그인 후 이용 가능"}
                </button>
              </div>
            </div>
          )}

          {tab === "BULK" && (
            <div className="flex flex-col gap-3">
              <p className="text-xs text-ink-3">
                많이 충전할수록 토큰당 단가가 저렴해집니다.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {BULK_TIERS.map((tier) => (
                  <button
                    key={tier.tokens}
                    onClick={() =>
                      handlePurchase(
                        tier.tokens,
                        `${tier.tokens.toLocaleString()} 토큰`,
                      )
                    }
                    disabled={!loggedIn}
                    className={`relative text-left rounded-xl border p-4 transition-all ${
                      tier.highlight
                        ? "border-accent bg-accent-soft hover:bg-accent/15"
                        : "border-line bg-surface hover:border-accent/40 hover:bg-accent-soft/40"
                    } disabled:opacity-50 disabled:cursor-not-allowed`}
                  >
                    {tier.highlight && (
                      <span className="absolute top-3 right-3 px-2 py-0.5 rounded-full bg-accent text-white text-[10px] font-bold">
                        BEST
                      </span>
                    )}
                    <div className="flex items-center gap-1.5">
                      <Coins size={16} className="text-accent" />
                      <span className="text-base font-bold text-ink">
                        {tier.tokens.toLocaleString()} 토큰
                      </span>
                    </div>
                    <div className="mt-2 flex items-baseline gap-1">
                      <span className="text-xl font-bold text-ink">
                        {tier.price.toLocaleString()}
                      </span>
                      <span className="text-xs text-ink-3">원</span>
                    </div>
                    <div className="mt-1 flex items-center justify-between text-[11px]">
                      <span className="text-ink-3">
                        토큰당 {tier.pricePerToken}원
                      </span>
                      {tier.discountLabel && (
                        <span className="text-amber-600 font-semibold">
                          {tier.discountLabel}
                        </span>
                      )}
                    </div>
                  </button>
                ))}
              </div>
              {!loggedIn && (
                <p className="text-xs text-ink-4 text-center pt-1">
                  결제는 로그인 후 이용할 수 있습니다.
                </p>
              )}
            </div>
          )}
        </div>

        {/* 토스트 */}
        {toast && (
          <div className="px-5 sm:px-6 pb-4">
            <div className="px-4 py-2.5 rounded-lg bg-accent text-white text-sm text-center">
              {toast}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
