import React, { memo, useEffect, useMemo, useRef } from "react";
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

const isoToEpochSeconds = (iso: string) =>
  Math.floor(new Date(iso).getTime() / 1000);

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

const formatRelationLabel = (relation?: PeerItem["relation"] | null) => {
  switch (relation) {
    case "LEADER":
      return "선행";
    case "FOLLOWER":
      return "후행";
    case "COINCIDENT":
      return "동행";
    default:
      return "중립";
  }
};

const formatAdjustmentBasis = (peer: PeerItem) => {
  if (peer.adjustedCorrValid && !peer.rawCorrValid) return "산업조정 기준 유사";
  if (peer.adjustmentBasis === "SIMPLE_SUBTRACTION") return "산업조정 상관";
  if (peer.adjustmentBasis === "FALLBACK_RAW") return "원시 상관 기준";
  return "원시 상관 기준";
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
    return (points ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.value))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.value) * scale,
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
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
    const mapped = (overlayBand ?? [])
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
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
    const first = mapped[0]?.value ?? 0;
    return mapped.map((p) => ({ ...p, value: Number(p.value) - Number(first) }));
  }, [commonTimes, overlayBand, showPeerOverlay]);

  const bandLowData = useMemo<AreaData<Time>[]>(() => {
    if (!showPeerOverlay) return [];
    const mapped = (overlayBand ?? [])
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
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
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
    if (coverage == null || !Number.isFinite(coverage)) return "coverage 확인 불가";
    if (coverage >= 0.75) return "coverage 높음";
    if (coverage >= 0.5) return "coverage 보통";
    return "coverage 낮음";
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
    const width = Math.floor(rect.width || el.clientWidth || 600);

    const chart = createChart(el, {
      width,
      height,
      layout: {
        background: { color: "#ffffff" },
        textColor: "#1f2937",
      },
      grid: {
        vertLines: { color: "#f3f4f6" },
        horzLines: { color: "#f3f4f6" },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { labelVisible: true },
        horzLine: { labelVisible: true },
      },
      rightPriceScale: {
        borderColor: "#e5e7eb",
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
      topColor: "#ffffff",
      bottomColor: "#ffffff",
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
        topColor: "#ffffff",
        bottomColor: "#ffffff",
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
      const h = Math.floor(r.height);

      if (w < 80 || h < 80) return;

      currentChart.applyOptions({
        width: w,
        height: h,
      });
    });

    ro.observe(el);

    return () => {
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
      <div className="px-1 pb-2 flex flex-wrap gap-4 text-xs sm:text-sm text-zinc-600">
        {showPeerOverlay && (
          <div className="flex items-center gap-2">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "#0f766e" }} />
            <span>선택 종목</span>
          </div>
        )}
        <div className="flex items-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: lineColor }} />
          <span>산업 지수</span>
        </div>
        {showPeerOverlay && (
          <>
            <div className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "#d97706" }} />
              <span>유사 종목군 중심선</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: "rgba(217, 119, 6, 0.35)" }} />
              <span>유사 종목군 범위 (p20-p80)</span>
            </div>
          </>
        )}
      </div>
      {showPeerOverlay && (
        <div className="px-1 pb-3 flex items-start justify-between gap-3 text-[11px] sm:text-xs text-zinc-500">
          <p className="leading-relaxed">
            선택 종목, 관련 산업지수, 유사 종목군 평균을 동일 기준일 0%로 환산해 비교합니다.
            음영은 유사 종목군의 p20~p80 범위이며, 날짜별 peer coverage가 높을수록 진하게 표시됩니다.
            낮은 coverage 구간은 표본이 줄어든 구간이라 참고 강도를 낮춰 해석해야 합니다.
          </p>
          <span className="shrink-0 rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-medium text-amber-700">
            최근 coverage {formatCoverage(latestCoverage)}
            {latestCoverageDayKey ? ` · ${latestCoverageDayKey}` : ""}
          </span>
          <div className="shrink-0">
            <button
              type="button"
              aria-label="peer cluster 설명 보기"
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
        className="w-full flex-1 min-h-0"
        style={{ height }}
      />
      {showPeerOverlay && tooltipDayKey && (
        <div className="pointer-events-none absolute right-4 top-20 z-10 rounded-lg border border-zinc-200 bg-white/95 px-3 py-2 text-[11px] text-zinc-600 shadow-sm">
          <p className="font-semibold text-zinc-900">{tooltipDayKey}</p>
          <p>선택 종목 {formatPct(anchorDayMap.get(tooltipDayKey) ?? null)}</p>
          <p>산업지수 {formatPct(dayMap.get(tooltipDayKey)?.value ?? null)}</p>
          <p>유사 평균 {formatPct(centroidDayMap.get(tooltipDayKey) ?? null)}</p>
          <p>p20 / p80 {formatPct(tooltipBand?.p20 ?? null)} / {formatPct(tooltipBand?.p80 ?? null)}</p>
          <p>coverage {formatCoverage(tooltipCoverage)} · {coverageLabel(tooltipCoverage)}</p>
        </div>
      )}
      {showPeerOverlay && isInfoOpen && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40"
          onClick={() => setIsInfoOpen(false)}
        >
          <div
            className="bg-white rounded-2xl shadow-xl max-w-lg w-[90%] max-h-[70vh] flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-200">
              <h2 className="text-lg font-semibold text-sky-600">Peer Cluster 설명</h2>
              <button
                onClick={() => setIsInfoOpen(false)}
                className="text-zinc-400 hover:text-zinc-700 text-xl leading-none"
              >
                &times;
              </button>
            </div>

            <div className="px-5 py-4 overflow-y-auto flex-1">
              <p className="text-sm text-zinc-700 leading-relaxed">
                선택 종목, 관련 산업지수, 유사 종목군 평균을 동일 기준일 0%로 환산해 비교합니다.
                음영은 유사 종목군의 p20~p80 범위이며, 날짜별 peer coverage가 높을수록 더 진하게 표시됩니다.
              </p>
              <p className="mt-3 text-sm text-zinc-700 leading-relaxed">
                coverage가 낮은 구간은 해당 날짜에 반영된 peer 수가 적다는 뜻이므로, 밴드폭과 중심선을 참고용으로 해석해야 합니다.
              </p>

              <div className="mt-5 rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-zinc-900">현재 시점 값</h3>
                  <span className="text-xs text-zinc-500">{activeDetailDayKey ?? "-"}</span>
                </div>

                <div className="mt-3">
                  <label className="block text-xs text-zinc-500 mb-1">날짜 선택</label>
                  <select
                    value={activeDetailDayKey ?? ""}
                    onChange={(e) => setSelectedDetailDayKey(e.target.value || null)}
                    className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-800 outline-none focus:border-sky-400"
                  >
                    {selectableDayKeys.map((dayKey) => (
                      <option key={dayKey} value={dayKey}>
                        {dayKey}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mt-3 grid grid-cols-2 gap-3">
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">선택 종목</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatPct(activeAnchorValue)}</p>
                  </div>
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">산업 지수</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatPct(activeIndustryValue)}</p>
                  </div>
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">Peer centroid</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatPct(activeCentroidValue)}</p>
                  </div>
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">p20</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatPct(activeBand?.p20 ?? null)}</p>
                  </div>
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">p80</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatPct(activeBand?.p80 ?? null)}</p>
                  </div>
                  <div className="rounded-xl border border-zinc-200 bg-white px-3 py-2">
                    <p className="text-xs text-zinc-500">Peer coverage</p>
                    <p className="mt-1 text-sm font-semibold text-zinc-900">{formatCoverage(activeCoverage)}</p>
                    <p className="mt-0.5 text-[11px] text-zinc-400">{coverageLabel(activeCoverage)}</p>
                  </div>
                </div>

                <p className="mt-3 text-xs text-zinc-500">
                  여기서 날짜를 직접 선택해 해당 시점 기준 값을 확인할 수 있어요.
                </p>
              </div>

              <div className="mt-5 rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-zinc-900">반영된 유사 종목</h3>
                  <span className="text-xs text-zinc-500">{overlayPeers?.length ?? 0}개</span>
                </div>

                {overlayPeers && overlayPeers.length > 0 ? (
                  <div className="mt-3 max-h-64 overflow-y-auto rounded-xl border border-zinc-200 bg-white">
                    {overlayPeers.map((peer, index) => (
                      <div
                        key={peer.stockCode}
                        className={`px-3 py-3 ${index > 0 ? "border-t border-zinc-200" : ""}`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-sm font-semibold text-zinc-900">{peer.companyName}</p>
                            <p className="text-xs text-zinc-500">{peer.stockCode}</p>
                          </div>
                          <span className="rounded-full bg-zinc-100 px-2 py-1 text-[11px] font-medium text-zinc-700">
                            {formatRelationLabel(peer.relation)}
                          </span>
                        </div>
                        <div className="mt-2 grid grid-cols-2 gap-2 text-xs text-zinc-600">
                          <div>
                            <span className="text-zinc-400">동행 상관도</span>
                            <p className="font-medium text-zinc-800">
                              {formatScore(peer.adjustedCorrValid ? peer.adjustedCorr : peer.corr)}
                            </p>
                            <p className="mt-0.5 text-[11px] text-zinc-400">{formatAdjustmentBasis(peer)}</p>
                          </div>
                          <div>
                            <span className="text-zinc-400">유사도 점수</span>
                            <p className="font-medium text-zinc-800">{formatScore(peer.peerScore ?? peer.score)}</p>
                          </div>
                          <div>
                            <span className="text-zinc-400">시차</span>
                            <p className="font-medium text-zinc-800">
                              {peer.bestLag == null ? "-" : `${peer.bestLag}일`}
                            </p>
                          </div>
                          <div>
                            <span className="text-zinc-400">평균 거래대금</span>
                            <p className="font-medium text-zinc-800">{formatNumber(peer.avgTurnover)}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-zinc-500">반영된 유사 종목 정보가 없습니다.</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default memo(RelativeLineWidget);
