import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { usePdfExportReveal } from "../contexts/PdfExportContext";
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

const isEnglish = (language?: string) => (language ?? "").toLowerCase().startsWith("en");

const fmtVolume = (value: number | null | undefined, language?: string) => {
  if (value == null || !Number.isFinite(value)) return "-";
  if (isEnglish(language)) return `${value.toLocaleString("en-US")} shares`;
  const abs = Math.abs(value);
  if (abs >= 1e8) return `${(value / 1e8).toFixed(2)}억주`;
  if (abs >= 1e4) return `${(value / 1e4).toFixed(1)}만주`;
  return `${value.toLocaleString("ko-KR")}주`;
};

export default function PriceFlowBars({ summary }: Props) {
  const { t, i18n } = useTranslation("analysisPanel");
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [hasAnimated, setHasAnimated] = useState(false);
  const forceReveal = usePdfExportReveal();
  const animated = hasAnimated || forceReveal;

  useEffect(() => {
    setHasAnimated(false);
  }, [summary]);

  useEffect(() => {
    const node = rootRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting) return;
        setHasAnimated(true);
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
        <h3 className="text-base sm:text-lg font-semibold text-ink">{t("priceFlow.title")}</h3>
        <p className="text-sm text-ink-3">{t("priceFlow.subtitle")}</p>
        <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-zinc-700">
          <div>{t("priceFlow.startDate")}: {formatKstDateTimeDisplay(summary.from) || "-"}</div>
          <div>{t("priceFlow.endDate")}: {formatKstDateTimeDisplay(summary.to) || "-"}</div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div
          className="rounded-2xl border border-line bg-bg-sunk px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: returnFillColor }} />
            <h4 className="text-sm font-semibold text-ink">{t("priceFlow.returnFlow")}</h4>
          </div>
          <div className="mt-3 flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-ink-3">{t("priceFlow.startClose")}</p>
              <p className="text-sm font-medium text-ink">{fmtNumber(summary.startClose)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-ink-3">{t("priceFlow.endClose")}</p>
              <p className="text-sm font-medium text-ink">{fmtNumber(summary.endClose)}</p>
            </div>
          </div>
          <div className="mt-4">
            <div className="flex items-center justify-between text-xs text-ink-3">
              <span>{t("priceFlow.down")}</span>
              <span>{t("priceFlow.neutral")}</span>
              <span>{t("priceFlow.up")}</span>
            </div>
            <div className="relative mt-1 h-3 rounded-full bg-line overflow-hidden">
              <div className="absolute inset-y-0 left-1/2 w-px bg-line-strong" />
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
          className="rounded-2xl border border-line bg-bg-sunk px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
            transitionDelay: "90ms",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: RANGE_COLOR }} />
            <h4 className="text-sm font-semibold text-ink">{t("priceFlow.priceRange")}</h4>
          </div>
          <div className="mt-3 flex items-end justify-between gap-3">
            <div>
              <p className="text-xs text-ink-3">{t("priceFlow.low")}</p>
              <p className="text-sm font-medium text-ink">{fmtNumber(summary.low)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-ink-3">{t("priceFlow.high")}</p>
              <p className="text-sm font-medium text-ink">{fmtNumber(summary.high)}</p>
            </div>
          </div>
          <div className="mt-4">
            <div className="relative px-2 py-1">
              <div className="relative h-3 rounded-full bg-line overflow-visible">
                <div
                  className="absolute top-0 h-full rounded-full bg-emerald-300/60"
                  style={{
                    width: `${animated ? 100 : 0}%`,
                    transition: "width 900ms cubic-bezier(0.22, 1, 0.36, 1)",
                  }}
                />
                {startMarker != null && (
                  <div
                    className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-ink shadow-sm"
                    style={{
                      left: `${animated ? startMarker : 0}%`,
                      transition: "left 950ms cubic-bezier(0.22, 1, 0.36, 1)",
                    }}
                    title={t("priceFlow.startClose")}
                  />
                )}
                {endMarker != null && (
                  <div
                    className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-emerald-700 shadow-sm"
                    style={{
                      left: `${animated ? endMarker : 0}%`,
                      transition: "left 1000ms cubic-bezier(0.22, 1, 0.36, 1)",
                    }}
                    title={t("priceFlow.endClose")}
                  />
                )}
              </div>
            </div>
            <div className="mt-2 flex items-center justify-between text-xs text-ink-3">
              <span>{t("priceFlow.startClose")}</span>
              <span>{t("priceFlow.endClose")}</span>
            </div>
            <p className="mt-2 text-sm font-semibold text-emerald-700">
              {t("priceFlow.rangePct", { value: fmtPercent(rangePct) })}
            </p>
          </div>
        </div>

        <div
          className="rounded-2xl border border-line bg-bg-sunk px-4 py-4"
          style={{
            opacity: animated ? 1 : 0,
            transform: animated ? "translateY(0)" : "translateY(18px)",
            transition: "opacity 700ms cubic-bezier(0.22, 1, 0.36, 1), transform 700ms cubic-bezier(0.22, 1, 0.36, 1)",
            transitionDelay: "180ms",
          }}
        >
          <div className="flex items-center gap-2">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: VOLUME_COLOR }} />
            <h4 className="text-sm font-semibold text-ink">{t("priceFlow.volumeStrength")}</h4>
          </div>
          <div className="mt-3">
            <p className="text-xs text-ink-3">{t("priceFlow.avgVolume")}</p>
            <p className="text-sm font-medium text-ink">{fmtVolume(summary.avgVolume, i18n.language)}</p>
          </div>
          <div className="mt-4">
            <div className="h-3 rounded-full bg-line overflow-hidden">
              <div
                className="h-full rounded-full"
                style={{
                  width: `${animated ? Math.max(volumeRatio * 100, summary.avgVolume == null ? 0 : 8) : 0}%`,
                  backgroundColor: VOLUME_COLOR,
                  transition: "width 950ms cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              />
            </div>
            <div className="mt-2 flex items-center justify-between text-xs text-ink-3">
              <span>{t("priceFlow.lowActivity")}</span>
              <span>{t("priceFlow.active")}</span>
            </div>
            <p className="mt-2 text-sm font-semibold text-violet-700">
              {t("priceFlow.highLowRange", { value: fmtNumber(rangeSpan) })}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
