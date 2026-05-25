// src/components/FeatureIntro.tsx
// 종목 검색 전, 휑한 빈 화면을 채우는 기능 소개 카드.
// 검색이 완료되면 호출부에서 조건부로 언마운트된다.
import {
  LineChart,
  BarChart3,
  FileText,
  Sparkles,
  Landmark,
  Newspaper,
  Users,
  Search,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useTranslation } from "react-i18next";

type Variant = "deep" | "external";

const ICONS: Record<Variant, LucideIcon[]> = {
  deep: [LineChart, BarChart3, FileText, Sparkles],
  external: [Landmark, LineChart, Users, Newspaper],
};

const ITEM_KEYS: Record<Variant, string[]> = {
  deep: ["realTimeChart", "financialIndicators", "financialTimeline", "aiReport"],
  external: ["rates", "industryIndex", "supplyDemand", "newsSentiment"],
};

// eyebrow labels are already English, no translation needed
const EYEBROW: Record<Variant, string> = {
  deep: "Deep Analysis",
  external: "External Factors",
};

export default function FeatureIntro({ variant }: { variant: Variant }) {
  const { t } = useTranslation("featureIntro");
  const icons = ICONS[variant];
  const itemKeys = ITEM_KEYS[variant];

  return (
    <section className="w-full rounded-2xl border border-line bg-surface shadow-card overflow-hidden">
      <div className="bg-gradient-to-br from-accent-soft to-surface px-6 sm:px-8 py-7 sm:py-9 border-b border-line">
        <div className="flex items-center gap-2 text-[11px] font-semibold text-accent tracking-tight mb-2">
          <Search size={13} />
          {EYEBROW[variant]}
        </div>
        <h2 className="text-xl sm:text-2xl font-bold text-ink tracking-tight">
          {t(`${variant}.heading`)}
        </h2>
        <p className="mt-2 text-sm text-ink-3 max-w-2xl">{t("sub")}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4 p-5 sm:p-6">
        {icons.map((Icon, i) => (
          <div
            key={itemKeys[i]}
            className="flex items-start gap-3.5 rounded-xl border border-line bg-bg-sunk px-4 py-4 transition-colors hover:bg-surface-2"
          >
            <div className="shrink-0 w-10 h-10 grid place-items-center rounded-xl bg-accent-soft text-accent">
              <Icon size={19} />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-ink tracking-tight">
                {t(`${variant}.items.${itemKeys[i]}.title`)}
              </h3>
              <p className="mt-1 text-[13px] leading-relaxed text-ink-3">
                {t(`${variant}.items.${itemKeys[i]}.desc`)}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
