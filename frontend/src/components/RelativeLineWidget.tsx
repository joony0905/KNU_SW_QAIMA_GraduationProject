import React, { memo, useEffect, useMemo, useRef } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  createChart,
  AreaSeries,
  LineSeries,
  CrosshairMode,
  type IChartApi,
  type ISeriesApi,
  type AreaData,
  type LineData,
  type Time,
  type UTCTimestamp,
} from "lightweight-charts";
import type { PeerItem } from "../types/feature2";
import { useTheme } from "../hooks/useTheme";

export type RelativeLinePoint = {
  t: string;      // 표준 날짜/시간 문자열
  value: number;  // 리베이스된 변화율
};

interface Props {
  data: RelativeLinePoint[];
  lineColor?: string;
  title?: string;
  height?: number;
  overlayCentroid?: RelativeLinePoint[] | null;
  overlayBand?: { t: string; p20: number; p80: number }[] | null;
  overlayCoverage?: RelativeLinePoint[] | null;
  overlayAnchor?: RelativeLinePoint[] | null;
  overlayPeers?: PeerItem[] | null;
  showPeerOverlay?: boolean;

  // 부모와 공유할 KST 날짜 키
  hoveredDayKey?: string | null;
  onHoverDayKeyChange?: (dayKey: string | null) => void;
}

const isoToEpochSeconds = (iso: string) => {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return NaN;
  const kstDate = new Date(date.getTime() + 9 * 60 * 60 * 1000);
  return Math.floor(Date.UTC(kstDate.getUTCFullYear(), kstDate.getUTCMonth(), kstDate.getUTCDate()) / 1000);
};

/**
 * epoch seconds를 KST 날짜 키(YYYY-MM-DD)로 변환한다.
 * Asia/Seoul 기준
 */
const toKstDayKeyFromEpochSec = (sec: number) => {
  const kstDate = new Date(sec * 1000 + 9 * 60 * 60 * 1000);
  const y = kstDate.getUTCFullYear();
  const m = String(kstDate.getUTCMonth() + 1).padStart(2, "0");
  const d = String(kstDate.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

function safeClearCrosshair(chart: IChartApi | null) {
  if (!chart) return;
  try {
    (chart as any).clearCrosshairPosition?.();
  } catch {}
}

function safeSetCrosshair(
  chart: IChartApi | null,
  time: Time,
  price: number,
  series: ISeriesApi<"Line"> | null
) {
  if (!chart || !series) return;
  try {
    (chart as any).setCrosshairPosition(price, time, series);
  } catch {}
}

const formatPct = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
};

const formatNumber = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR");
};

const formatScore = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return value.toFixed(3);
};

const formatRelationLabel = (relation: PeerItem["relation"] | null | undefined, t: (key: string) => string) => {
  switch (relation) {
    case "LEADER":
      return t("relativeLine.relation.leader");
    case "FOLLOWER":
      return t("relativeLine.relation.follower");
    case "COINCIDENT":
      return t("relativeLine.relation.coincident");
    default:
      return t("relativeLine.relation.neutral");
  }
};

const formatAdjustmentBasis = (peer: PeerItem, t: (key: string) => string) => {
  if (peer.adjustedCorrValid && !peer.rawCorrValid) return t("relativeLine.adjustment.industryAdjustedSimilar");
  if (peer.adjustmentBasis === "SIMPLE_SUBTRACTION") return t("relativeLine.adjustment.industryAdjustedCorr");
  if (peer.adjustmentBasis === "FALLBACK_RAW") return t("relativeLine.adjustment.rawCorrBased");
  return t("relativeLine.adjustment.rawCorrBased");
};

const dedupeByTime = <T extends { time: Time }>(points: T[]): T[] => {
  const byTime = new Map<number, T>();
  for (const point of points) {
    byTime.set(Number(point.time), point);
  }
  return Array.from(byTime.values()).sort((a, b) => Number(a.time) - Number(b.time));
};

function RelativeLineWidget({
  data,
  lineColor = "#2563eb",
  title,
  height = 320,
  overlayCentroid,
  overlayBand,
  overlayCoverage,
  overlayAnchor,
  overlayPeers,
  showPeerOverlay = false,
  hoveredDayKey,
  onHoverDayKeyChange,
}: Props) {
  const { theme } = useTheme();
  const { t } = useTranslation("analysisPanel");
  // 차트 캔버스 배경/격자/축 색 — 다크모드 대응 (카드 surface 색과 맞춤)
  const chartTheme = useMemo(
    () =>
      theme === "dark"
        ? {
            bg: "#202024",
            text: "#d4d4d4",
            grid: "#303035",
            border: "#3a3a40",
          }
        : {
            bg: "#ffffff",
            text: "#1f2937",
            grid: "#f3f4f6",
            border: "#e5e7eb",
          },
    [theme],
  );

  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const lineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const anchorSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const centroidSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bandHighSeriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const bandLowSeriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const coverageBandHighSeriesRefs = useRef<ISeriesApi<"Area">[]>([]);
  const coverageBandLowSeriesRefs = useRef<ISeriesApi<"Area">[]>([]);
  const bandUpperLineRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bandLowerLineRef = useRef<ISeriesApi<"Line"> | null>(null);

  const syncingCrosshairRef = useRef(false);
  const [isInfoOpen, setIsInfoOpen] = React.useState(false);
  const [selectedDetailDayKey, setSelectedDetailDayKey] = React.useState<string | null>(null);
  const [hoverTooltipDayKey, setHoverTooltipDayKey] = React.useState<string | null>(null);

  const toSortedLineData = (points: RelativeLinePoint[] | null | undefined, scale = 1): LineData<Time>[] => {
    return dedupeByTime((points ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(isoToEpochSeconds(p.t)) &&
          Number.isFinite(Number(p.value))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.value) * scale,
      })));
  };

  const rawIndustryData = useMemo<LineData<Time>[]>(() => toSortedLineData(data, 1), [data]);
  const rawAnchorData = useMemo<LineData<Time>[]>(() => toSortedLineData(overlayAnchor, 100), [overlayAnchor]);
  const rawCentroidData = useMemo<LineData<Time>[]>(() => toSortedLineData(overlayCentroid, 100), [overlayCentroid]);
  const rawCoverageData = useMemo<LineData<Time>[]>(() => toSortedLineData(overlayCoverage, 1), [overlayCoverage]);

  const coverageByTime = useMemo(() => {
    const map = new Map<number, number>();
    for (const point of rawCoverageData) {
      map.set(Number(point.time), Math.max(0, Math.min(1, Number(point.value))));
    }
    return map;
  }, [rawCoverageData]);

  const commonTimes = useMemo(() => {
    if (!showPeerOverlay || rawAnchorData.length === 0) return null;
    let common = new Set(rawIndustryData.map((p) => Number(p.time)));
    for (const series of [rawAnchorData, rawCentroidData]) {
      if (series.length === 0) continue;
      const times = new Set(series.map((p) => Number(p.time)));
      common = new Set([...common].filter((time) => times.has(time)));
    }
    if (overlayBand && overlayBand.length > 0) {
      const bandTimes = new Set(
        overlayBand
          .filter((p) => p && typeof p.t === "string" && Number.isFinite(Number(p.p20)) && Number.isFinite(Number(p.p80)))
          .map((p) => isoToEpochSeconds(p.t))
      );
      common = new Set([...common].filter((time) => bandTimes.has(time)));
    }
    return common.size > 0 ? common : null;
  }, [overlayBand, rawAnchorData, rawCentroidData, rawIndustryData, showPeerOverlay]);

  const normalizeSeries = (series: LineData<Time>[], times: Set<number> | null) => {
    const filtered = times ? series.filter((p) => times.has(Number(p.time))) : series;
    const first = filtered[0]?.value;
    if (first == null || !Number.isFinite(Number(first))) return filtered;
    return filtered.map((p) => ({ ...p, value: Number(p.value) - Number(first) }));
  };

  const lineData = useMemo<LineData<Time>[]>(() => {
    return normalizeSeries(rawIndustryData, showPeerOverlay ? commonTimes : null);
  }, [commonTimes, rawIndustryData, showPeerOverlay]);

  const anchorData = useMemo<LineData<Time>[]>(() => {
    return normalizeSeries(rawAnchorData, commonTimes);
  }, [commonTimes, rawAnchorData]);

  const centroidData = useMemo<LineData<Time>[]>(() => {
    return normalizeSeries(rawCentroidData, commonTimes);
  }, [commonTimes, rawCentroidData]);

  const bandHighData = useMemo<AreaData<Time>[]>(() => {
    if (!showPeerOverlay) return [];
    const mapped = dedupeByTime((overlayBand ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.p80)) &&
          (!commonTimes || commonTimes.has(isoToEpochSeconds(p.t)))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.p80) * 100,
      })));
    const first = mapped[0]?.value ?? 0;
    return mapped.map((p) => ({ ...p, value: Number(p.value) - Number(first) }));
  }, [commonTimes, overlayBand, showPeerOverlay]);

  const bandLowData = useMemo<AreaData<Time>[]>(() => {
    if (!showPeerOverlay) return [];
    const mapped = dedupeByTime((overlayBand ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.p20)) &&
          (!commonTimes || commonTimes.has(isoToEpochSeconds(p.t)))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.p20) * 100,
      })));
    const first = mapped[0]?.value ?? 0;
    return mapped.map((p) => ({ ...p, value: Number(p.value) - Number(first) }));
  }, [commonTimes, overlayBand, showPeerOverlay]);

  const bandCoverageBuckets = useMemo(() => {
    const buckets = {
      low: { high: [] as AreaData<Time>[], low: [] as AreaData<Time>[] },
      medium: { high: [] as AreaData<Time>[], low: [] as AreaData<Time>[] },
      high: { high: [] as AreaData<Time>[], low: [] as AreaData<Time>[] },
    };

    for (const point of bandHighData) {
      const coverage = coverageByTime.get(Number(point.time)) ?? 1;
      const bucket = coverage >= 0.75 ? "high" : coverage >= 0.5 ? "medium" : "low";
      buckets[bucket].high.push(point);
    }
    for (const point of bandLowData) {
      const coverage = coverageByTime.get(Number(point.time)) ?? 1;
      const bucket = coverage >= 0.75 ? "high" : coverage >= 0.5 ? "medium" : "low";
      buckets[bucket].low.push(point);
    }

    return [buckets.low, buckets.medium, buckets.high];
  }, [bandHighData, bandLowData, coverageByTime]);

  /**
   * KST 날짜 키를 실제 차트 time/value로 변환한다.
   * 같은 날짜 키가 여러 개면 마지막 값으로 덮어쓴다.
   */
  const dayMap = useMemo(() => {
    const map = new Map<string, { time: Time; value: number }>();

    for (const p of lineData) {
      const timeNum = Number(p.time);
      const key = toKstDayKeyFromEpochSec(timeNum);
      map.set(key, {
        time: p.time,
        value: Number(p.value),
      });
    }

    return map;
  }, [lineData]);

  const centroidDayMap = useMemo(() => {
    const map = new Map<string, number>();
    for (const p of centroidData) {
      map.set(toKstDayKeyFromEpochSec(Number(p.time)), Number(p.value));
    }
    return map;
  }, [centroidData]);

  const anchorDayMap = useMemo(() => {
    const map = new Map<string, number>();
    for (const p of anchorData) {
      map.set(toKstDayKeyFromEpochSec(Number(p.time)), Number(p.value));
    }
    return map;
  }, [anchorData]);

  const bandDayMap = useMemo(() => {
    const map = new Map<string, { p20: number; p80: number }>();
    const lowMap = new Map<string, number>();

    for (const p of bandLowData) {
      lowMap.set(toKstDayKeyFromEpochSec(Number(p.time)), Number(p.value));
    }
    for (const p of bandHighData) {
      const key = toKstDayKeyFromEpochSec(Number(p.time));
      const low = lowMap.get(key);
      if (low == null) continue;
      map.set(key, { p20: low, p80: Number(p.value) });
    }

    return map;
  }, [bandHighData, bandLowData]);

  const coverageDayMap = useMemo(() => {
    const map = new Map<string, number>();
    for (const point of rawCoverageData) {
      map.set(toKstDayKeyFromEpochSec(Number(point.time)), Number(point.value));
    }
    return map;
  }, [rawCoverageData]);

  const latestDayKey = useMemo(() => {
    const last = lineData.at(-1);
    return last ? toKstDayKeyFromEpochSec(Number(last.time)) : null;
  }, [lineData]);

  const latestCoveragePoint = useMemo(() => {
    return rawCoverageData.at(-1) ?? null;
  }, [rawCoverageData]);

  const latestCoverageDayKey = latestCoveragePoint
    ? toKstDayKeyFromEpochSec(Number(latestCoveragePoint.time))
    : null;

  const selectableDayKeys = useMemo(() => {
    return Array.from(dayMap.keys()).sort((a, b) => a.localeCompare(b));
  }, [dayMap]);

  const activeDetailDayKey = selectedDetailDayKey ?? hoveredDayKey ?? latestDayKey;
  const tooltipDayKey = hoverTooltipDayKey ?? hoveredDayKey;
  const activeIndustryValue = activeDetailDayKey ? (dayMap.get(activeDetailDayKey)?.value ?? null) : null;
  const activeAnchorValue = activeDetailDayKey ? (anchorDayMap.get(activeDetailDayKey) ?? null) : null;
  const activeCentroidValue = activeDetailDayKey ? (centroidDayMap.get(activeDetailDayKey) ?? null) : null;
  const activeBand = activeDetailDayKey ? (bandDayMap.get(activeDetailDayKey) ?? null) : null;
  const tooltipBand = tooltipDayKey ? (bandDayMap.get(tooltipDayKey) ?? null) : null;
  const activeCoverage = activeDetailDayKey ? (coverageDayMap.get(activeDetailDayKey) ?? null) : null;
  const tooltipCoverage = tooltipDayKey ? (coverageDayMap.get(tooltipDayKey) ?? null) : null;
  const latestCoverage = latestCoveragePoint ? Number(latestCoveragePoint.value) : null;

  const coverageLabel = (coverage?: number | null) => {
    if (coverage == null || !Number.isFinite(coverage)) return t("relativeLine.coverage.unknown");
    if (coverage >= 0.75) return t("relativeLine.coverage.high");
    if (coverage >= 0.5) return t("relativeLine.coverage.medium");
    return t("relativeLine.coverage.low");
  };

  const formatCoverage = (coverage?: number | null) => {
    if (coverage == null || !Number.isFinite(coverage)) return "-";
    return `${Math.round(coverage * 100)}%`;
  };

  useEffect(() => {
    if (!isInfoOpen) return;
    setSelectedDetailDayKey((current) => current ?? hoveredDayKey ?? latestDayKey ?? null);
  }, [isInfoOpen, hoveredDayKey, latestDayKey]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    if (chartRef.current) return;

    const rect = el.getBoundingClientRect();
    const width = Math.max(280, Math.floor(rect.width || el.clientWidth || 600));

    const chart = createChart(el, {
      width,
      height,
      layout: {
        background: { color: chartTheme.bg },
        textColor: chartTheme.text,
      },
      grid: {
        vertLines: { color: chartTheme.grid },
        horzLines: { color: chartTheme.grid },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { labelVisible: true },
        horzLine: { labelVisible: true },
      },
      rightPriceScale: {
        borderColor: chartTheme.border,
      },
      timeScale: {
        timeVisible: true,
        visible: true,
      },
      localization: {
        priceFormatter: (price: number) => `${price.toFixed(2)}%`,
      },
    });

    const lineSeries = chart.addSeries(LineSeries, {
      color: lineColor,
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
    });

    const anchorSeries = chart.addSeries(LineSeries, {
      color: "#0f766e",
      lineWidth: 3,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
    });

    const bandHighSeries = chart.addSeries(AreaSeries, {
      lineColor: "rgba(217, 119, 6, 0)",
      topColor: "rgba(217, 119, 6, 0.08)",
      bottomColor: "rgba(217, 119, 6, 0.03)",
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    const bandLowSeries = chart.addSeries(AreaSeries, {
      lineColor: "rgba(255,255,255,0)",
      topColor: chartTheme.bg,
      bottomColor: chartTheme.bg,
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    const coverageBandConfigs = [
      { topColor: "rgba(217, 119, 6, 0.05)", bottomColor: "rgba(217, 119, 6, 0.015)" },
      { topColor: "rgba(217, 119, 6, 0.10)", bottomColor: "rgba(217, 119, 6, 0.035)" },
      { topColor: "rgba(217, 119, 6, 0.18)", bottomColor: "rgba(217, 119, 6, 0.07)" },
    ];
    const coverageHighSeries = coverageBandConfigs.map((config) =>
      chart.addSeries(AreaSeries, {
        lineColor: "rgba(217, 119, 6, 0)",
        topColor: config.topColor,
        bottomColor: config.bottomColor,
        lineWidth: 1,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
      })
    );
    const coverageLowSeries = coverageBandConfigs.map(() =>
      chart.addSeries(AreaSeries, {
        lineColor: "rgba(255,255,255,0)",
        topColor: chartTheme.bg,
        bottomColor: chartTheme.bg,
        lineWidth: 1,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
      })
    );

    const centroidSeries = chart.addSeries(LineSeries, {
      color: "#d97706",
      lineWidth: 2,
      lineStyle: 2,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
    });

    const bandUpperLine = chart.addSeries(LineSeries, {
      color: "rgba(217, 119, 6, 0.45)",
      lineWidth: 1,
      lineStyle: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    const bandLowerLine = chart.addSeries(LineSeries, {
      color: "rgba(217, 119, 6, 0.32)",
      lineWidth: 1,
      lineStyle: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    chartRef.current = chart;
    lineSeriesRef.current = lineSeries;
    anchorSeriesRef.current = anchorSeries;
    centroidSeriesRef.current = centroidSeries;
    bandHighSeriesRef.current = bandHighSeries;
    bandLowSeriesRef.current = bandLowSeries;
    coverageBandHighSeriesRefs.current = coverageHighSeries;
    coverageBandLowSeriesRefs.current = coverageLowSeries;
    bandUpperLineRef.current = bandUpperLine;
    bandLowerLineRef.current = bandLowerLine;

    const ro = new ResizeObserver(() => {
      const target = containerRef.current;
      const currentChart = chartRef.current;
      if (!target || !currentChart) return;

      const r = target.getBoundingClientRect();
      const w = Math.floor(r.width);
      const h = Math.floor(r.height || height);

      if (w < 80 || h < 80) return;

      currentChart.applyOptions({
        width: w,
        height: h,
      });
      currentChart.timeScale().fitContent();
    });

    ro.observe(el);

    const syncSize = () => {
      const target = containerRef.current;
      const currentChart = chartRef.current;
      if (!target || !currentChart) return;
      const r = target.getBoundingClientRect();
      const w = Math.max(280, Math.floor(r.width || target.clientWidth || width));
      const h = Math.max(160, Math.floor(r.height || height));
      currentChart.applyOptions({ width: w, height: h });
      currentChart.timeScale().fitContent();
    };

    const scheduleSyncSize = () => {
      requestAnimationFrame(syncSize);
    };

    const raf = requestAnimationFrame(syncSize);
    const timeout = window.setTimeout(syncSize, 250);
    const lateTimeout = window.setTimeout(syncSize, 800);
    window.addEventListener("resize", scheduleSyncSize);
    window.addEventListener("orientationchange", scheduleSyncSize);
    window.visualViewport?.addEventListener("resize", scheduleSyncSize);
    window.visualViewport?.addEventListener("scroll", scheduleSyncSize);

    return () => {
      cancelAnimationFrame(raf);
      window.clearTimeout(timeout);
      window.clearTimeout(lateTimeout);
      window.removeEventListener("resize", scheduleSyncSize);
      window.removeEventListener("orientationchange", scheduleSyncSize);
      window.visualViewport?.removeEventListener("resize", scheduleSyncSize);
      window.visualViewport?.removeEventListener("scroll", scheduleSyncSize);
      try {
        ro.disconnect();
      } catch {}

      try {
        chart.remove();
      } catch {}

      chartRef.current = null;
      lineSeriesRef.current = null;
      anchorSeriesRef.current = null;
      centroidSeriesRef.current = null;
      bandHighSeriesRef.current = null;
      bandLowSeriesRef.current = null;
      coverageBandHighSeriesRefs.current = [];
      coverageBandLowSeriesRefs.current = [];
      bandUpperLineRef.current = null;
      bandLowerLineRef.current = null;
    };
  }, [height, lineColor]);

  useEffect(() => {
    const lineSeries = lineSeriesRef.current;
    const anchorSeries = anchorSeriesRef.current;
    const centroidSeries = centroidSeriesRef.current;
    const bandHighSeries = bandHighSeriesRef.current;
    const bandLowSeries = bandLowSeriesRef.current;
    const coverageHighSeries = coverageBandHighSeriesRefs.current;
    const coverageLowSeries = coverageBandLowSeriesRefs.current;
    const bandUpperLine = bandUpperLineRef.current;
    const bandLowerLine = bandLowerLineRef.current;
    const chart = chartRef.current;
    if (!lineSeries || !anchorSeries || !chart || !centroidSeries || !bandHighSeries || !bandLowSeries || !bandUpperLine || !bandLowerLine) return;

    lineSeries.setData(lineData);
    anchorSeries.setData(showPeerOverlay ? anchorData : []);
    centroidSeries.setData(showPeerOverlay ? centroidData : []);
    bandHighSeries.setData([]);
    bandLowSeries.setData([]);
    coverageHighSeries.forEach((series, index) => {
      series.setData(showPeerOverlay ? (bandCoverageBuckets[index]?.high ?? []) : []);
    });
    coverageLowSeries.forEach((series, index) => {
      series.setData(showPeerOverlay ? (bandCoverageBuckets[index]?.low ?? []) : []);
    });
    bandUpperLine.setData(showPeerOverlay ? bandHighData : []);
    bandLowerLine.setData(showPeerOverlay ? bandLowData : []);
    chart.timeScale().fitContent();
  }, [anchorData, lineData, centroidData, bandHighData, bandLowData, bandCoverageBuckets, showPeerOverlay]);

  /**
   * 다크모드 토글 시 차트 캔버스/마스크 색 갱신 (차트 재생성 없이)
   */
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    try {
      chart.applyOptions({
        layout: {
          background: { color: chartTheme.bg },
          textColor: chartTheme.text,
        },
        grid: {
          vertLines: { color: chartTheme.grid },
          horzLines: { color: chartTheme.grid },
        },
        rightPriceScale: { borderColor: chartTheme.border },
      });
      bandLowSeriesRef.current?.applyOptions({
        topColor: chartTheme.bg,
        bottomColor: chartTheme.bg,
      });
      coverageBandLowSeriesRefs.current.forEach((series) =>
        series.applyOptions({
          topColor: chartTheme.bg,
          bottomColor: chartTheme.bg,
        }),
      );
    } catch {}
  }, [chartTheme]);

  /**
   * 차트에서 hover하면 부모에 KST day key 전달
   */
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || !onHoverDayKeyChange) return;

    const handler = (param: any) => {
      if (syncingCrosshairRef.current) return;

      if (!param?.time) {
        onHoverDayKeyChange(null);
        setHoverTooltipDayKey(null);
        return;
      }

      const key = toKstDayKeyFromEpochSec(Number(param.time));
      setHoverTooltipDayKey(key);
      onHoverDayKeyChange(key);
    };

    chart.subscribeCrosshairMove(handler);

    return () => {
      try {
        chart.unsubscribeCrosshairMove(handler);
      } catch {}
    };
  }, [onHoverDayKeyChange]);

  /**
   * 외부 hoveredDayKey를 받아 이 차트의 실제 time/value로 crosshair를 설정한다.
   */
  useEffect(() => {
    const chart = chartRef.current;
    const series = lineSeriesRef.current;
    if (!chart || !series) return;

    syncingCrosshairRef.current = true;
    try {
      if (!hoveredDayKey) {
        safeClearCrosshair(chart);
        return;
      }

      const found = dayMap.get(hoveredDayKey);
      if (!found) {
        safeClearCrosshair(chart);
        return;
      }

      safeSetCrosshair(chart, found.time, found.value, series);
    } finally {
      queueMicrotask(() => {
        syncingCrosshairRef.current = false;
      });
    }
  }, [hoveredDayKey, dayMap]);

  return (
    <div className="relative w-full h-full min-h-0 flex flex-col">
      {title && (
        <div className="px-1 pb-2 text-sm font-medium text-zinc-700">
          {title}
        </div>
      )}
      <div className="px-1 pb-2 flex flex-wrap gap-x-4 gap-y-2 text-xs sm:text-sm text-zinc-600">
        {showPeerOverlay && (
          <div className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "#0f766e" }} />
            <span>{t("relativeLine.legend.anchor")}</span>
          </div>
        )}
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: lineColor }} />
          <span>{t("relativeLine.legend.industryIndex")}</span>
        </div>
        {showPeerOverlay && (
          <>
            <div className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "#d97706" }} />
              <span>{t("relativeLine.legend.peerCentroid")}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "rgba(217, 119, 6, 0.35)" }} />
              <span>{t("relativeLine.legend.peerBand")}</span>
            </div>
          </>
        )}
      </div>
      {showPeerOverlay && (
        <div className="px-1 pb-3 flex flex-col gap-2 text-[11px] sm:flex-row sm:items-start sm:justify-between sm:gap-3 sm:text-xs text-zinc-500">
          <p className="min-w-0 leading-relaxed sm:flex-1">
            {t("relativeLine.summary")}
          </p>
          <span className="self-start shrink-0 rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-medium text-amber-700 sm:self-auto">
            {t("relativeLine.latestCoverage", { value: formatCoverage(latestCoverage) })}
            {latestCoverageDayKey ? ` · ${latestCoverageDayKey}` : ""}
          </span>
          <div className="absolute right-1 top-[4.4rem] shrink-0 sm:static">
            <button
              type="button"
              aria-label={t("relativeLine.openInfo")}
              aria-expanded={isInfoOpen}
              onClick={() => {
                if (isInfoOpen) {
                  setIsInfoOpen(false);
                  return;
                }
                setSelectedDetailDayKey(hoveredDayKey ?? latestDayKey ?? null);
                setIsInfoOpen(true);
              }}
              className="relative -top-0.5 inline-flex h-4 w-4 items-center justify-center rounded-full bg-zinc-300 text-[9px] font-bold text-white transition-colors hover:bg-sky-400"
            >
              ?
            </button>
          </div>
        </div>
      )}
      <div
        ref={containerRef}
        className="w-full min-w-0 shrink-0 flex-1 min-h-[300px] sm:min-h-0"
        style={{ height }}
      />
      {showPeerOverlay && tooltipDayKey && (
        <div className="pointer-events-none absolute right-4 top-20 z-10 rounded-lg border border-zinc-200 bg-white/95 px-3 py-2 text-[11px] text-zinc-600 shadow-sm">
          <p className="font-semibold text-zinc-900">{tooltipDayKey}</p>
          <p>{t("relativeLine.legend.anchor")} {formatPct(anchorDayMap.get(tooltipDayKey) ?? null)}</p>
          <p>{t("relativeLine.legend.industryIndex")} {formatPct(dayMap.get(tooltipDayKey)?.value ?? null)}</p>
          <p>{t("relativeLine.tooltip.peerAverage")} {formatPct(centroidDayMap.get(tooltipDayKey) ?? null)}</p>
          <p>p20 / p80 {formatPct(tooltipBand?.p20 ?? null)} / {formatPct(tooltipBand?.p80 ?? null)}</p>
          <p>{t("relativeLine.coverage.label")} {formatCoverage(tooltipCoverage)} · {coverageLabel(tooltipCoverage)}</p>
        </div>
      )}
      {showPeerOverlay && isInfoOpen && createPortal(
        <div
          className="qaima-modal-backdrop-in fixed inset-0 z-[9999] flex items-center justify-center bg-black/45 px-4"
          onClick={() => setIsInfoOpen(false)}
        >
          <div
            className="qaima-modal-pop-in bg-surface rounded-2xl border border-line shadow-pop max-w-lg w-full max-h-[70vh] flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-4 border-b border-line">
              <h2 className="text-lg font-semibold text-accent">{t("relativeLine.modal.title")}</h2>
              <button
                onClick={() => setIsInfoOpen(false)}
                className="text-ink-4 hover:text-ink-2 text-xl leading-none"
              >
                &times;
              </button>
            </div>

            <div className="px-5 py-4 overflow-y-auto flex-1">
              <p className="text-sm text-ink-2 leading-relaxed">
                {t("relativeLine.modal.paragraph1")}
              </p>
              <p className="mt-3 text-sm text-ink-2 leading-relaxed">
                {t("relativeLine.modal.paragraph2")}
              </p>

              <div className="mt-5 rounded-2xl border border-line bg-bg-sunk p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-ink">{t("relativeLine.modal.currentValues")}</h3>
                  <span className="text-xs text-ink-3">{activeDetailDayKey ?? "-"}</span>
                </div>

                <div className="mt-3">
                  <label className="block text-xs text-ink-3 mb-1">{t("relativeLine.modal.selectDate")}</label>
                  <select
                    value={activeDetailDayKey ?? ""}
                    onChange={(e) => setSelectedDetailDayKey(e.target.value || null)}
                    className="w-full rounded-xl border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent"
                  >
                    {selectableDayKeys.map((dayKey) => (
                      <option key={dayKey} value={dayKey}>
                        {dayKey}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mt-3 grid grid-cols-2 gap-3">
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">{t("relativeLine.legend.anchor")}</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatPct(activeAnchorValue)}</p>
                  </div>
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">{t("relativeLine.legend.industryIndex")}</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatPct(activeIndustryValue)}</p>
                  </div>
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">{t("relativeLine.legend.peerCentroid")}</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatPct(activeCentroidValue)}</p>
                  </div>
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">p20</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatPct(activeBand?.p20 ?? null)}</p>
                  </div>
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">p80</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatPct(activeBand?.p80 ?? null)}</p>
                  </div>
                  <div className="rounded-xl border border-line bg-surface px-3 py-2">
                    <p className="text-xs text-ink-3">{t("relativeLine.coverage.peerCoverage")}</p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatCoverage(activeCoverage)}</p>
                    <p className="mt-0.5 text-[11px] text-ink-4">{coverageLabel(activeCoverage)}</p>
                  </div>
                </div>

                <p className="mt-3 text-xs text-ink-3">
                  {t("relativeLine.modal.dateHelp")}
                </p>
              </div>

              <div className="mt-5 rounded-2xl border border-line bg-bg-sunk p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-ink">{t("relativeLine.modal.includedPeers")}</h3>
                  <span className="text-xs text-ink-3">{t("relativeLine.modal.peerCount", { count: overlayPeers?.length ?? 0 })}</span>
                </div>

                {overlayPeers && overlayPeers.length > 0 ? (
                  <div className="mt-3 max-h-64 overflow-y-auto rounded-xl border border-line bg-surface">
                    {overlayPeers.map((peer, index) => (
                      <div
                        key={peer.stockCode}
                        className={`px-3 py-3 ${index > 0 ? "border-t border-line" : ""}`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-sm font-semibold text-ink">{peer.companyName}</p>
                            <p className="text-xs text-ink-3">{peer.stockCode}</p>
                          </div>
                          <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-2">
                            {formatRelationLabel(peer.relation, t)}
                          </span>
                        </div>
                        <div className="mt-2 grid grid-cols-2 gap-2 text-xs text-ink-3">
                          <div>
                            <span className="text-ink-4">{t("relativeLine.peer.correlation")}</span>
                            <p className="font-medium text-ink-2">
                              {formatScore(peer.adjustedCorrValid ? peer.adjustedCorr : peer.corr)}
                            </p>
                            <p className="mt-0.5 text-[11px] text-ink-4">{formatAdjustmentBasis(peer, t)}</p>
                          </div>
                          <div>
                            <span className="text-ink-4">{t("relativeLine.peer.score")}</span>
                            <p className="font-medium text-ink-2">{formatScore(peer.peerScore ?? peer.score)}</p>
                          </div>
                          <div>
                            <span className="text-ink-4">{t("relativeLine.peer.lag")}</span>
                            <p className="font-medium text-ink-2">
                              {peer.bestLag == null ? "-" : t("relativeLine.peer.lagDays", { value: peer.bestLag })}
                            </p>
                          </div>
                          <div>
                            <span className="text-ink-4">{t("relativeLine.peer.avgTurnover")}</span>
                            <p className="font-medium text-ink-2">{formatNumber(peer.avgTurnover)}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-ink-3">{t("relativeLine.modal.emptyPeers")}</p>
                )}
              </div>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}

export default memo(RelativeLineWidget);
