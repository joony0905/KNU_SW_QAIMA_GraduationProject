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
  t: string;      // ISO datetime
  value: number;  // rebased %
};

interface Props {
  data: RelativeLinePoint[];
  lineColor?: string;
  title?: string;
  height?: number;
  overlayCentroid?: RelativeLinePoint[] | null;
  overlayBand?: { t: string; p20: number; p80: number }[] | null;
  overlayPeers?: PeerItem[] | null;
  showPeerOverlay?: boolean;

  // 부모와 공유할 KST day key
  hoveredDayKey?: string | null;
  onHoverDayKeyChange?: (dayKey: string | null) => void;
}

const isoToEpochSeconds = (iso: string) =>
  Math.floor(new Date(iso).getTime() / 1000);

/**
 * epoch seconds -> KST day key (YYYY-MM-DD)
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

function RelativeLineWidget({
  data,
  lineColor = "#2563eb",
  title,
  height = 320,
  overlayCentroid,
  overlayBand,
  overlayPeers,
  showPeerOverlay = false,
  hoveredDayKey,
  onHoverDayKeyChange,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const lineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const centroidSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bandHighSeriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const bandLowSeriesRef = useRef<ISeriesApi<"Area"> | null>(null);
  const bandUpperLineRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bandLowerLineRef = useRef<ISeriesApi<"Line"> | null>(null);

  const syncingCrosshairRef = useRef(false);
  const [isInfoOpen, setIsInfoOpen] = React.useState(false);
  const [selectedDetailDayKey, setSelectedDetailDayKey] = React.useState<string | null>(null);

  const lineData = useMemo<LineData<Time>[]>(() => {
    return (data ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.value))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.value),
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }, [data]);

  const centroidData = useMemo<LineData<Time>[]>(() => {
    return (overlayCentroid ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.value))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.value) * 100,
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }, [overlayCentroid]);

  const bandHighData = useMemo<AreaData<Time>[]>(() => {
    if (!showPeerOverlay) return [];
    return (overlayBand ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.p80))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.p80) * 100,
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }, [overlayBand, showPeerOverlay]);

  const bandLowData = useMemo<AreaData<Time>[]>(() => {
    if (!showPeerOverlay) return [];
    return (overlayBand ?? [])
      .filter(
        (p) =>
          p &&
          typeof p.t === "string" &&
          Number.isFinite(Number(p.p20))
      )
      .map((p) => ({
        time: isoToEpochSeconds(p.t) as UTCTimestamp,
        value: Number(p.p20) * 100,
      }))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }, [overlayBand, showPeerOverlay]);

  /**
   * KST day key -> 실제 차트 time/value
   * 같은 day key가 여러 개면 마지막 값으로 덮어씀
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

  const latestDayKey = useMemo(() => {
    const last = lineData.at(-1);
    return last ? toKstDayKeyFromEpochSec(Number(last.time)) : null;
  }, [lineData]);

  const selectableDayKeys = useMemo(() => {
    return Array.from(dayMap.keys()).sort((a, b) => a.localeCompare(b));
  }, [dayMap]);

  const activeDetailDayKey = selectedDetailDayKey ?? hoveredDayKey ?? latestDayKey;
  const activeIndustryValue = activeDetailDayKey ? (dayMap.get(activeDetailDayKey)?.value ?? null) : null;
  const activeCentroidValue = activeDetailDayKey ? (centroidDayMap.get(activeDetailDayKey) ?? null) : null;
  const activeBand = activeDetailDayKey ? (bandDayMap.get(activeDetailDayKey) ?? null) : null;

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
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
    });

    const bandHighSeries = chart.addSeries(AreaSeries, {
      lineColor: "rgba(217, 119, 6, 0)",
      topColor: "rgba(217, 119, 6, 0.18)",
      bottomColor: "rgba(217, 119, 6, 0.06)",
      lineWidth: 0,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    const bandLowSeries = chart.addSeries(AreaSeries, {
      lineColor: "rgba(255,255,255,0)",
      topColor: "#ffffff",
      bottomColor: "#ffffff",
      lineWidth: 0,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });

    const centroidSeries = chart.addSeries(LineSeries, {
      color: "#d97706",
      lineWidth: 3,
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
    centroidSeriesRef.current = centroidSeries;
    bandHighSeriesRef.current = bandHighSeries;
    bandLowSeriesRef.current = bandLowSeries;
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
      centroidSeriesRef.current = null;
      bandHighSeriesRef.current = null;
      bandLowSeriesRef.current = null;
      bandUpperLineRef.current = null;
      bandLowerLineRef.current = null;
    };
  }, [height, lineColor]);

  useEffect(() => {
    const lineSeries = lineSeriesRef.current;
    const centroidSeries = centroidSeriesRef.current;
    const bandHighSeries = bandHighSeriesRef.current;
    const bandLowSeries = bandLowSeriesRef.current;
    const bandUpperLine = bandUpperLineRef.current;
    const bandLowerLine = bandLowerLineRef.current;
    const chart = chartRef.current;
    if (!lineSeries || !chart || !centroidSeries || !bandHighSeries || !bandLowSeries || !bandUpperLine || !bandLowerLine) return;

    lineSeries.setData(lineData);
    centroidSeries.setData(showPeerOverlay ? centroidData : []);
    bandHighSeries.setData(showPeerOverlay ? bandHighData : []);
    bandLowSeries.setData(showPeerOverlay ? bandLowData : []);
    bandUpperLine.setData(showPeerOverlay ? bandHighData : []);
    bandLowerLine.setData(showPeerOverlay ? bandLowData : []);
    chart.timeScale().fitContent();
  }, [lineData, centroidData, bandHighData, bandLowData, showPeerOverlay]);

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
        return;
      }

      const key = toKstDayKeyFromEpochSec(Number(param.time));
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
   * 외부 hoveredDayKey를 받아 내 차트의 실제 time/value로 crosshair 세팅
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
    <div className="w-full h-full min-h-0 flex flex-col">
      {title && (
        <div className="px-1 pb-2 text-sm font-medium text-zinc-700">
          {title}
        </div>
      )}
      <div className="px-1 pb-2 flex flex-wrap gap-4 text-xs sm:text-sm text-zinc-600">
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
            파란선은 산업 전체 흐름, 주황선은 유사 종목군 평균 흐름입니다.
            주황 음영은 유사 종목군 분산 범위(p20~p80)입니다.
          </p>
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
                파란선은 산업 전체의 상대 변화율이고, 주황선은 이 종목과 유사하게 움직인 종목군의 평균 흐름이에요.
                주황 음영은 해당 종목군의 실제 분산 범위(p20~p80)이며, 예측 구간이 아닌 실제 분포 범위랍니다!
              </p>
              <p className="mt-3 text-sm text-zinc-700 leading-relaxed">
                모든 값은 시작 시점을 0으로 맞춘 상대 변화율 기준이에요.
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
                            <p className="font-medium text-zinc-800">{formatScore(peer.corr)}</p>
                          </div>
                          <div>
                            <span className="text-zinc-400">유사도 점수</span>
                            <p className="font-medium text-zinc-800">{formatScore(peer.score)}</p>
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
