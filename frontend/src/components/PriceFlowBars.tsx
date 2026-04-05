import { useEffect, useMemo, useRef, useState } from "react";
import type { PriceFlowSummary } from "../types/analysisPanel";
import { formatKstDateTimeDisplay } from "../utils/kst";

type Props = {
  summary: PriceFlowSummary;
};

const POSITIVE_COLOR = "#2563eb";
const NEGATIVE_COLOR = "#dc2626";
const RANGE_COLOR = "#0f766e";
const VOLUME_COLOR = "#7c3aed";

const fmtNumber = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR");
};

const fmtPercent = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
};

const fmtVolume = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  if (abs >= 1e8) return `${(value / 1e8).toFixed(2)}억주`;
  if (abs >= 1e4) return `${(value / 1e4).toFixed(1)}만주`;
  return `${value.toLocaleString("ko-KR")}주`;
};

export default function PriceFlowBars({ summary }: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [animated, setAnimated] = useState(false);

  useEffect(() => {
    setAnimated(false);
  }, [summary]);

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
      {
        threshold: 0.2,
        rootMargin: "0px 0px -10% 0px",
      },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [summary]);

  const rangeSpan = useMemo(() => {
    const high = summary.high ?? null;
    const low = summary.low ?? null;
    if (high == null || low == null || !Number.isFinite(high) || !Number.isFinite(low)) return null;
    return Math.max(high - low, 0);
  }, [summary.high, summary.low]);

  const startMarker = useMemo(() => {
    if (summary.startClose == null || summary.low == null || rangeSpan == null) return null;
    if (rangeSpan === 0) return 50;
    return Math.min(Math.max(((summary.startClose - summary.low) / rangeSpan) * 100, 0), 100);
  }, [summary.startClose, summary.low, rangeSpan]);

  const endMarker = useMemo(() => {
    if (summary.endClose == null || summary.low == null || rangeSpan == null) return null;
    if (rangeSpan === 0) return 50;
    return Math.min(Math.max(((summary.endClose - summary.low) / rangeSpan) * 100, 0), 100);
  }, [summary.endClose, summary.low, rangeSpan]);

  const rangePct = useMemo(() => {
    if (
      rangeSpan == null
      || summary.startClose == null
      || !Number.isFinite(summary.startClose)
      || summary.startClose === 0
    ) {
      return null;
    }
    return (rangeSpan / summary.startClose) * 100;
  }, [rangeSpan, summary.startClose]);

  const volumeRatio = useMemo(() => {
    if (summary.avgVolume == null || !Number.isFinite(summary.avgVolume) || summary.avgVolume <= 0) return 0;
    return Math.min(Math.log10(summary.avgVolume + 1) / 8, 1);
  }, [summary.avgVolume]);

  const returnRatio = useMemo(() => {
    if (summary.returnPct == null || !Number.isFinite(summary.returnPct)) return 0;
    return Math.min(Math.abs(summary.returnPct) / 30, 1);
  }, [summary.returnPct]);

  const returnFillColor = (summary.returnPct ?? 0) < 0 ? NEGATIVE_COLOR : POSITIVE_COLOR;
  const returnWidth = Math.max(returnRatio * 50, summary.returnPct == null ? 0 : 8);

  return (
    <div ref={rootRef}>
      <div className="flex flex-col gap-1">
        <h3 className="text-base sm:text-lg font-semibold text-zinc-900">가격 흐름 요약</h3>
        <p className="text-sm text-zinc-500">분석 기간 내 종가 변화 · 가격 범위 · 평균 거래량</p>
        <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-zinc-700">
          <div>시작일: {formatKstDateTimeDisplay(summary.from) || "-"}</div>
          <div>종료일: {formatKstDateTimeDisplay(summary.to) || "-"}</div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div
          className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: returnFillColor }} />
            <h4 className="text-sm font-semibold text-zinc-900">수익률 흐름</h4>
          </div>
          <div className="mt-3 flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-zinc-500">시작 종가</p>
              <p className="text-sm font-medium text-zinc-900">{fmtNumber(summary.startClose)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-zinc-500">마지막 종가</p>
              <p className="text-sm font-medium text-zinc-900">{fmtNumber(summary.endClose)}</p>
            </div>
          </div>
          <div className="mt-4">
            <div className="flex items-center justify-between text-xs text-zinc-500">
              <span>하락</span>
              <span>중립</span>
              <span>상승</span>
            </div>
            <div className="relative mt-1 h-3 rounded-full bg-zinc-200 overflow-hidden">
              <div className="absolute inset-y-0 left-1/2 w-px bg-zinc-400" />
              <div
                className="absolute top-0 h-full rounded-full"
                style={{
                  left: (summary.returnPct ?? 0) < 0
                    ? `calc(50% - ${animated ? returnWidth : 0}%)`
                    : "50%",
                  width: `${animated ? returnWidth : 0}%`,
                  backgroundColor: returnFillColor,
                  transition: "width 900ms cubic-bezier(0.22, 1, 0.36, 1), left 900ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
            <p
              className="mt-2 text-sm font-semibold"
              style={{ color: returnFillColor }}
            >
              {fmtPercent(summary.returnPct)}
            </p>
          </div>
        </div>

        <div
          className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
            transitionDelay: "90ms",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: RANGE_COLOR }} />
            <h4 className="text-sm font-semibold text-zinc-900">가격 범위</h4>
          </div>
          <div className="mt-3 flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-zinc-500">최저가</p>
              <p className="text-sm font-medium text-zinc-900">{fmtNumber(summary.low)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-zinc-500">최고가</p>
              <p className="text-sm font-medium text-zinc-900">{fmtNumber(summary.high)}</p>
            </div>
          </div>
          <div className="mt-4">
            <div className="relative px-2 py-1">
              <div className="relative h-3 rounded-full bg-zinc-200 overflow-visible">
                <div
                  className="absolute top-0 h-full rounded-full bg-emerald-300/60"
                  style={{
                    width: `${animated ? 100 : 0}%`,
                    transition: "width 900ms cubic-bezier(0.22, 1, 0.36, 1)",
                  }}
                />
                {startMarker != null && (
                  <div
                    className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-zinc-900 shadow-sm"
                    style={{
                      left: `${animated ? startMarker : 0}%`,
                      transition: "left 950ms cubic-bezier(0.22, 1, 0.36, 1)",
                    }}
                    title="시작 종가"
                  />
                )}
                {endMarker != null && (
                  <div
                    className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-emerald-700 shadow-sm"
                    style={{
                      left: `${animated ? endMarker : 0}%`,
                      transition: "left 1000ms cubic-bezier(0.22, 1, 0.36, 1)",
                    }}
                    title="마지막 종가"
                  />
                )}
              </div>
            </div>
            <div className="mt-2 flex items-center justify-between text-xs text-zinc-500">
              <span>시작 종가</span>
              <span>마지막 종가</span>
            </div>
            <p className="mt-2 text-sm font-semibold text-emerald-700">
              변동폭 {fmtPercent(rangePct)}
            </p>
          </div>
        </div>

        <div
          className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
            transitionDelay: "180ms",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: VOLUME_COLOR }} />
            <h4 className="text-sm font-semibold text-zinc-900">거래량 강도</h4>
          </div>
          <div className="mt-3">
            <p className="text-xs text-zinc-500">평균 거래량</p>
            <p className="text-sm font-medium text-zinc-900">{fmtVolume(summary.avgVolume)}</p>
          </div>
          <div className="mt-4">
            <div className="h-3 rounded-full bg-zinc-200 overflow-hidden">
              <div
                className="h-full rounded-full"
                style={{
                  width: `${animated ? Math.max(volumeRatio * 100, summary.avgVolume == null ? 0 : 8) : 0}%`,
                  backgroundColor: VOLUME_COLOR,
                  transition: "width 950ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
            <div className="mt-2 flex items-center justify-between text-xs text-zinc-500">
              <span>낮음</span>
              <span>활발</span>
            </div>
            <p className="mt-2 text-sm font-semibold text-violet-700">
              고가-저가 범위 {fmtNumber(rangeSpan)}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
