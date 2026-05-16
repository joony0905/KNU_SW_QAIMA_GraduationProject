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

type Variant = "deep" | "external";

type IntroItem = {
  icon: LucideIcon;
  title: string;
  desc: string;
};

const CONTENT: Record<
  Variant,
  { eyebrow: string; heading: string; sub: string; items: IntroItem[] }
> = {
  deep: {
    eyebrow: "Deep Analysis",
    heading: "종목 하나를 깊게 파고드는 심층분석",
    sub: "위 검색창에 종목명이나 6자리 종목코드(예: 005930)를 입력하면 아래 기능들을 한 번에 볼 수 있어요.",
    items: [
      {
        icon: LineChart,
        title: "실시간 차트 · 보조지표",
        desc: "캔들 차트 위에 EMA · 볼린저밴드 · 스토캐스틱을 함께 표시합니다.",
      },
      {
        icon: BarChart3,
        title: "투자지표 · 재무제표",
        desc: "수익성 · 안정성 등 핵심 재무 지표와 연·분기 재무제표를 정리합니다.",
      },
      {
        icon: FileText,
        title: "재무 시계열",
        desc: "매출 · 영업이익 · 순이익 추이를 기간별로 시각화합니다.",
      },
      {
        icon: Sparkles,
        title: "AI 심층 리포트",
        desc: "가격 흐름 · 재무 · 보조지표를 종합한 LLM 분석 리포트를 생성합니다.",
      },
    ],
  },
  external: {
    eyebrow: "External Factors",
    heading: "종목을 둘러싼 바깥 흐름까지 보는 외부요인",
    sub: "위 검색창에 종목명이나 6자리 종목코드(예: 005930)를 입력하면 아래 기능들을 한 번에 볼 수 있어요.",
    items: [
      {
        icon: Landmark,
        title: "기준금리 · 환율 · 국채",
        desc: "거시 금리 환경과 환율 추이를 종목과 함께 살펴봅니다.",
      },
      {
        icon: LineChart,
        title: "산업 지수 · 유사종목",
        desc: "관련 산업 지수와 또래 종목군의 반응 구조를 비교합니다.",
      },
      {
        icon: Users,
        title: "수급 · 공매도 추이",
        desc: "투자자별 매매 동향과 공매도 흐름을 추적합니다.",
      },
      {
        icon: Newspaper,
        title: "뉴스 감성 + AI 리포트",
        desc: "관련 뉴스 감성과 외부요인을 종합한 LLM 리포트를 생성합니다.",
      },
    ],
  },
};

export default function FeatureIntro({ variant }: { variant: Variant }) {
  const { eyebrow, heading, sub, items } = CONTENT[variant];

  return (
    <section className="w-full rounded-2xl border border-line bg-surface shadow-card overflow-hidden">
      <div className="bg-gradient-to-br from-accent-soft to-surface px-6 sm:px-8 py-7 sm:py-9 border-b border-line">
        <div className="flex items-center gap-2 text-[11px] font-semibold text-accent tracking-tight mb-2">
          <Search size={13} />
          {eyebrow}
        </div>
        <h2 className="text-xl sm:text-2xl font-bold text-ink tracking-tight">
          {heading}
        </h2>
        <p className="mt-2 text-sm text-ink-3 max-w-2xl">{sub}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4 p-5 sm:p-6">
        {items.map(({ icon: Icon, title, desc }) => (
          <div
            key={title}
            className="flex items-start gap-3.5 rounded-xl border border-line bg-bg-sunk px-4 py-4 transition-colors hover:bg-surface-2"
          >
            <div className="shrink-0 w-10 h-10 grid place-items-center rounded-xl bg-accent-soft text-accent">
              <Icon size={19} />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-ink tracking-tight">
                {title}
              </h3>
              <p className="mt-1 text-[13px] leading-relaxed text-ink-3">
                {desc}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
