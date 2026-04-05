import { useEffect, useRef, useState } from "react";
import type { IndicatorBundle } from "../types/indicator";

type Props = {
  indicators: IndicatorBundle;
};

const latestDefined = <T,>(items: T[] | null | undefined, pick: (item: T) => number | null | undefined) => {
  if (!items?.length) return null;
  for (let i = items.length - 1; i >= 0; i -= 1) {
    const value = pick(items[i]);
    if (value != null && Number.isFinite(value)) return value;
  }
  return null;
};

const fmt = (value: number | null | undefined, digits = 2) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR", {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  });
};

const fmtPct = (value: number | null | undefined, digits = 1) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(digits)}%`;
};

const normalizeGauge = (value: number | null | undefined, max: number) => {
  if (value == null || !Number.isFinite(value) || max <= 0) return 0;
  return Math.max(0, Math.min((Math.abs(value) / max) * 100, 100));
};

export default function IndicatorSnapshotCards({ indicators }: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [animated, setAnimated] = useState(false);

  useEffect(() => {
    setAnimated(false);
  }, [indicators]);

  useEffect(() => {
    const node = rootRef.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting) return;
        setAnimated(true);
        observer.disconnect();
      },
      { threshold: 0.2, rootMargin: "0px 0px -10% 0px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [indicators]);

  const ema20 = latestDefined(indicators.ema?.["20"], (item) => item.value);
  const ema60 = latestDefined(indicators.ema?.["60"], (item) => item.value);
  const ema120 = latestDefined(indicators.ema?.["120"], (item) => item.value);
  const emaAlignment =
    ema20 != null && ema60 != null && ema120 != null
      ? ema20 > ema60 && ema60 > ema120
        ? "상승 정렬"
        : ema20 < ema60 && ema60 < ema120
          ? "하락 정렬"
          : "혼합"
      : "확인 불가";

  const bbLatest = indicators.bb20_2?.length ? indicators.bb20_2[indicators.bb20_2.length - 1] : null;
  const percentB =
    bbLatest && bbLatest.upper != null && bbLatest.lower != null && bbLatest.mid != null
      ? (((bbLatest.mid - bbLatest.lower) / (bbLatest.upper - bbLatest.lower || 1)) * 100)
      : null;
  const bbWidth =
    bbLatest && bbLatest.upper != null && bbLatest.lower != null ? bbLatest.upper - bbLatest.lower : null;

  const stochLatest = indicators.stoch14_3_3?.length
    ? indicators.stoch14_3_3[indicators.stoch14_3_3.length - 1]
    : null;
  const stochZone =
    stochLatest?.k != null
      ? stochLatest.k >= 80
        ? "과열"
        : stochLatest.k <= 20
          ? "침체"
          : "중립"
      : "확인 불가";
  const stochGap =
    stochLatest?.k != null && stochLatest?.d != null ? stochLatest.k - stochLatest.d : null;

  const cards = [
    {
      title: "이동평균선 (EMA)",
      color: "#2563eb",
      body: (
        <>
          <div className="grid grid-cols-3 gap-2 text-sm text-zinc-700">
            <div>EMA20: {fmt(ema20, 1)}</div>
            <div>EMA60: {fmt(ema60, 1)}</div>
            <div>EMA120: {fmt(ema120, 1)}</div>
          </div>
          <div className="mt-3">
            <div className="mb-1 flex items-center justify-between text-sm">
              <span className="text-zinc-700">정렬 상태</span>
              <span className="font-medium text-zinc-900">{emaAlignment}</span>
            </div>
            <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-200">
              <div
                className="h-full rounded-full"
                style={{
                  width: `${animated ? (emaAlignment === "상승 정렬" ? 92 : emaAlignment === "하락 정렬" ? 28 : 58) : 0}%`,
                  backgroundColor: "#2563eb",
                  transition: "width 850ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
          </div>
        </>
      ),
    },
    {
      title: "볼린저 밴드 (Bollinger Band)",
      color: "#16a34a",
      body: (
        <>
          <div className="grid grid-cols-2 gap-2 text-sm text-zinc-700">
            <div>중심선: {fmt(bbLatest?.mid, 1)}</div>
            <div>밴드폭: {fmt(bbWidth, 1)}</div>
            <div>상단선: {fmt(bbLatest?.upper, 1)}</div>
            <div>하단선: {fmt(bbLatest?.lower, 1)}</div>
          </div>
          <div className="mt-3">
            <div className="mb-1 flex items-center justify-between text-sm">
              <span className="text-zinc-700">밴드 내 위치</span>
              <span className="font-medium text-zinc-900">{fmtPct(percentB, 1)}</span>
            </div>
            <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-zinc-200">
              <div className="absolute inset-y-0 left-1/2 w-px bg-white/70" />
              <div
                className="absolute top-1/2 h-4 w-4 -translate-y-1/2 rounded-full border-2 border-white bg-[#16a34a] shadow-sm"
                style={{
                  left: `calc(${animated ? percentB ?? 0 : 0}% - 8px)`,
                  transition: "left 900ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
          </div>
        </>
      ),
    },
    {
      title: "스토캐스틱 (Stochastic)",
      color: "#d97706",
      body: (
        <>
          <div className="grid grid-cols-2 gap-2 text-sm text-zinc-700">
            <div>%K: {fmt(stochLatest?.k, 1)}</div>
            <div>%D: {fmt(stochLatest?.d, 1)}</div>
            <div>구간: {stochZone}</div>
            <div>K-D: {fmt(stochGap, 1)}</div>
          </div>
          <div className="mt-3">
            <div className="mb-1 flex items-center justify-between text-sm">
              <span className="text-zinc-700">오실레이터 위치</span>
              <span className="font-medium text-zinc-900">{fmtPct(stochLatest?.k, 1)}</span>
            </div>
            <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-zinc-200">
              <div className="absolute inset-y-0 left-[20%] w-px bg-white/70" />
              <div className="absolute inset-y-0 left-[80%] w-px bg-white/70" />
              <div
                className="h-full rounded-full"
                style={{
                  width: `${animated ? normalizeGauge(stochLatest?.k, 100) : 0}%`,
                  backgroundColor: "#d97706",
                  transition: "width 850ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
          </div>
        </>
      ),
    },
  ];

  return (
    <div ref={rootRef}>
      <div className="flex flex-col gap-1">
        <h3 className="text-base sm:text-lg font-semibold text-zinc-900">투자 보조지표 요약 (Indicator)</h3>
        <p className="text-sm text-zinc-500">EMA · 볼린저 밴드 · 스토캐스틱</p>
      </div>

      <div className="mt-3 grid grid-cols-1 lg:grid-cols-3 gap-4">
        {cards.map((card, index) => (
          <div
            key={card.title}
            className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4"
            style={{
              opacity: animated ? 1 : 0,
              transform: animated ? "translateY(0)" : "translateY(18px)",
              transitionProperty: "opacity, transform",
              transitionDuration: "700ms",
              transitionTimingFunction: "cubic-bezier(0.22, 1, 0.36, 1)",
              transitionDelay: `${index * 90}ms`,
            }}
          >
            <div className="flex items-center gap-2">
              <span
                className="inline-block h-3 w-3 rounded-full"
                style={{ backgroundColor: card.color }}
              />
              <h4 className="text-sm font-semibold text-zinc-900">{card.title}</h4>
            </div>
            <div className="mt-3">{card.body}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
