import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import type { AnalysisExplainSection, AnalysisPanelResult, FinancialTimelineSection, PriceFlowSummary } from "../types/analysisPanel";
import type { PeerItem } from "../types/feature2";
import { Download, Maximize2 } from "lucide-react";
import DictTerm from "./DictTerm";
import DictionaryText from "./DictionaryText";
import FinancialTimelineChart from "./FinancialTimelineChart";
import MarketSnapshotBars from "./MarketSnapshotBars";
import IndicatorSnapshotCards from "./IndicatorSnapshotCards";
import PriceFlowBars from "./PriceFlowBars";
import ShortSellingTrendChart from "./ShortSellingTrendChart";
import InvestorFlowTrendChart from "./InvestorFlowTrendChart";
import { MultiLineTrendChart } from "./Feature2TrendCharts";
import { usePdfExportReveal } from "../contexts/PdfExportContext";
import ReportHeader, { type ReportHeaderMeta } from "./ReportHeader";
import { mapWarningsToNotes, expandWarningLines } from "../utils/warningNotes";

interface AnalysisResultPanelProps {
  result: AnalysisPanelResult | null;
  loading: boolean;
  loadingStage?: string;
  err: string;
  showAnalyzeButton: boolean;
  onAnalyze: () => void;
  onDownload: () => void;
  onZoom: () => void;
  displayText: string;
  financialTimeline?: FinancialTimelineSection | null;
  priceFlowSummary?: PriceFlowSummary | null;
  layout?: "full" | "panel";
  reportMeta?: ReportHeaderMeta | null;
}

type ExplainSection = AnalysisExplainSection;

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR");
};

const formatSentimentScore = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value > 0 ? `+${value.toFixed(2)}` : value.toFixed(2);
};

const formatSignedNumber = (value?: number | null, digits = 2) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return value > 0 ? `+${value.toFixed(digits)}` : value.toFixed(digits);
};

const formatRatio = (value?: number | null) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
};

type AnalysisPanelTranslator = TFunction<"analysisPanel">;

const trendDirectionText = (direction: string | null | undefined, t: AnalysisPanelTranslator) => {
  switch (direction) {
    case "UP":
      return t("trend.rise");
    case "DOWN":
      return t("trend.fall");
    case "FLAT":
      return t("trend.flat");
    default:
      return t("trend.checking");
  }
};

const investorFlowDirectionText = (direction: string | null | undefined, t: AnalysisPanelTranslator) => {
  switch (direction) {
    case "BOTH_NET_BUY":
      return t("investorFlow.buyBoth");
    case "BOTH_NET_SELL":
      return t("investorFlow.sellBoth");
    case "FOREIGN_BUY_INSTITUTION_SELL":
      return t("investorFlow.foreignBuyInstSell");
    case "FOREIGN_SELL_INSTITUTION_BUY":
      return t("investorFlow.foreignSellInstBuy");
    default:
      return t("investorFlow.mixed");
  }
};

const formatFlowAmount = (value: number | null | undefined, t: AnalysisPanelTranslator) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  if (abs >= 100_000_000) return `${sign}${(abs / 100_000_000).toFixed(1)}${t("amountUnit.trillion")}`;
  if (abs >= 10_000) return `${sign}${(abs / 10_000).toFixed(1)}${t("amountUnit.hundredMillion")}`;
  return `${sign}${Math.round(abs).toLocaleString("ko-KR")}${t("amountUnit.million")}`;
};

const formatRatePoint = (value?: number | null, unit = "%") => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}${unit}`;
};

type MacroChartMode = "exchange" | "baseRate" | "domesticBond" | "usRates" | "shortSelling";

const MACRO_CHART_KEYS: MacroChartMode[] = [
  "exchange",
  "baseRate",
  "domesticBond",
  "usRates",
  "shortSelling",
];

const sentimentLabelText = (label: string | null | undefined, score: number | null | undefined, t: AnalysisPanelTranslator) => {
  switch (label) {
    case "positive":
      return t("sentiment.positive");
    case "negative":
      return t("sentiment.negative");
  }
  if (score != null && Number.isFinite(score)) {
    if (score > 0.05) return t("sentiment.positive");
    if (score < -0.05) return t("sentiment.negative");
  }
  return t("sentiment.neutral");
};

const sentimentBadgeClass = (score?: number | null) => {
  if (score == null || !Number.isFinite(score) || Math.abs(score) < 0.05) {
    return "bg-zinc-100 text-zinc-600 border-zinc-200";
  }
  if (score > 0) {
    return "bg-rose-50 text-rose-700 border-rose-200";
  }
  return "bg-blue-50 text-blue-700 border-blue-200";
};

const formatNewsDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return date.toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" });
};

const formatNewsTimeAgo = (value: string | null | undefined, t: AnalysisPanelTranslator): string => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return formatNewsDate(value);
  const diff = Date.now() - date.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return t("timeAgo.minutes", { n: Math.max(0, mins) });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t("timeAgo.hours", { n: hours });
  const days = Math.floor(hours / 24);
  if (days < 7) return t("timeAgo.days", { n: days });
  return date.toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" });
};

const formatRelationBadge = (relation: PeerItem["relation"], t: AnalysisPanelTranslator) => {
  switch (relation) {
    case "LEADER":
      return t("lead.leading");
    case "FOLLOWER":
      return t("lead.lagging");
    case "COINCIDENT":
      return t("lead.coincident");
    default:
      return t("lead.neutral");
  }
};

const correlationColorClass = (corr?: number | null): string => {
  if (corr == null || !Number.isFinite(corr)) return "text-ink-3";
  const abs = Math.abs(corr);
  if (abs >= 0.7) return "text-red-600 dark:text-red-300";
  if (abs >= 0.5) return "text-orange-500 dark:text-orange-300";
  if (abs >= 0.3) return "text-amber-500 dark:text-amber-300";
  return "text-ink-3";
};

const displayPeerCorr = (peer: PeerItem): number | null => {
  if (peer.adjustedCorrValid && peer.adjustedCorr != null && Number.isFinite(peer.adjustedCorr)) {
    return peer.adjustedCorr;
  }
  return peer.corr == null || !Number.isFinite(peer.corr) ? null : peer.corr;
};

const displayPeerCorrLabel = (peer: PeerItem, t: AnalysisPanelTranslator): string => {
  if (peer.adjustedCorrValid && !peer.rawCorrValid) return t("peer.industryAdjustedBasis");
  if (peer.adjustedCorrValid) return t("peer.industryAdjustedCorr");
  return t("peer.rawCorr");
};

const formatPeerScore = (peer: PeerItem): string => {
  const value = peer.peerScore ?? peer.score;
  return value == null || !Number.isFinite(value) ? "-" : value.toFixed(2);
};

const formatDisplayStatus = (peer: PeerItem, t: AnalysisPanelTranslator): string => {
  switch (peer.displayStatus) {
    case "SELECTED":
      return t("peer.clusterIncluded");
    case "ELIGIBLE_NOT_SELECTED":
      return t("peer.similarExcluded");
    case "LOW_CORR":
      return t("peer.lowCorr");
    case "ADJUSTED_ONLY":
      return t("peer.industryAdjustedSimilar");
    case "RAW_ONLY":
      return t("peer.rawCorrBased");
    case "FALLBACK_RAW":
      return t("peer.rawCorrFallback");
    default:
      return t("peer.referenceCandidate");
  }
};

const peerCardClass = (peer: PeerItem): string => {
  switch (peer.displayStatus) {
    case "SELECTED":
      return "border-sky-300 bg-sky-50/70 dark:border-indigo-400/40 dark:bg-indigo-950/30 dark:shadow-[inset_0_1px_0_rgb(255_255_255_/_0.04)]";
    case "LOW_CORR":
      return "border-line bg-bg-sunk opacity-75 dark:border-line dark:bg-bg-sunk/70";
    case "DISPLAY_ONLY":
    case "RAW_ONLY":
    case "ADJUSTED_ONLY":
    case "FALLBACK_RAW":
      return "border-line bg-surface dark:border-line-strong/70 dark:bg-surface-2/55";
    default:
      return "border-line bg-surface dark:border-line-strong/70 dark:bg-surface-2/55";
  }
};

const relationColorClass = (relation: PeerItem["relation"]): string => {
  switch (relation) {
    case "LEADER":
      return "text-green-600 dark:text-emerald-300";
    case "FOLLOWER":
      return "text-purple-600 dark:text-violet-300";
    case "COINCIDENT":
      return "text-ink";
    default:
      return "text-ink-3";
  }
};

const renderExplainSection = (section: ExplainSection | null | undefined, t: AnalysisPanelTranslator) => {
  if (!section || (!section.summary && (!section.bullets || section.bullets.length === 0))) {
    return null;
  }

  return (
    <div className="mt-3 rounded-2xl border border-line bg-bg-sunk px-4 py-4">
      <h4 className="text-sm font-semibold text-ink">
        {section.title ?? t("section.defaultTitle")}
      </h4>
      {section.summary ? (
        <p className="mt-2 text-sm sm:text-base text-ink-2">
          <DictionaryText text={section.summary} />
        </p>
      ) : null}
      {section.bullets && section.bullets.length > 0 ? (
        <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-ink-2 flex flex-col gap-1">
          {section.bullets.map((item, idx) => (
            <li key={`${section.title ?? "section"}-${idx}`}>
              <DictionaryText text={item} />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
};


export default function AnalysisResultPanel({
  result,
  loading,
  err,
  showAnalyzeButton,
  onAnalyze,
  onDownload,
  onZoom,
  displayText,
  financialTimeline = null,
  priceFlowSummary = null,
  layout = "full",
  reportMeta = null,
}: AnalysisResultPanelProps) {
  const isPanel = layout === "panel";
  const pdfExporting = usePdfExportReveal();
  const { t } = useTranslation("analysisPanel");
  const reportRef = useRef<HTMLDivElement | null>(null);
  const [macroChartMode, setMacroChartMode] = useState<MacroChartMode>("exchange");
  const macroChartOptions = useMemo(
    () => MACRO_CHART_KEYS.map((key) => ({ key, label: t(`macro.${key}`) })),
    [t],
  );
  const explainSections = result?.explain?.sections ?? null;
  const overallExplain = result?.explain?.overall ?? null;
  const warningNotes = expandWarningLines(mapWarningsToNotes(result?.warnings));
  const hasResult = Boolean(result);
  const isFeature2Report = Boolean(
    result?.metrics?.peerCluster
      || result?.metrics?.macroRates
      || result?.metrics?.macroRatesSeries?.series?.length
      || result?.metrics?.investorFlow
      || result?.metrics?.newsSentimentSummary
      || result?.metrics?.shortSellingSeries?.length
      || result?.metrics?.baseRateTrendSummary,
  );
  const reportAnimationKey = result
    ? [
        result.metrics?.stock?.stockCode ?? "report",
        isFeature2Report ? "feature2" : "feature1",
        result.metrics?.shortSelling?.reportDate ?? "",
        result.metrics?.newsSentimentSummary?.summaryDate ?? "",
        result.explain?.text?.slice(0, 24) ?? "",
      ].join(":")
    : "empty";
  const financialTimelineAnimationKey = financialTimeline
    ? [
        financialTimeline.period,
        financialTimeline.points.length,
        financialTimeline.points[0]?.label ?? "",
        financialTimeline.points.at(-1)?.label ?? "",
      ].join(":")
    : "no-financial-timeline";

  useEffect(() => {
    const reportNode = reportRef.current;
    if (!reportNode || !hasResult) return;

    const sections = Array.from(reportNode.children).filter(
      (child): child is HTMLElement => child instanceof HTMLElement,
    );

    sections.forEach((section, index) => {
      section.classList.remove("is-visible");
      section.style.transitionDelay = `${Math.min(index * 55, 320)}ms`;
    });

    if (pdfExporting) {
      sections.forEach((section) => {
        section.classList.add("is-visible");
        section.style.transitionDelay = "0ms";
      });
      return;
    }

    const revealVisibleSections = () => {
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
      sections.forEach((section) => {
        if (section.classList.contains("is-visible")) return;
        const rect = section.getBoundingClientRect();
        if (rect.top < viewportHeight + 240 && rect.bottom > -240) {
          section.classList.add("is-visible");
        }
      });
    };

    if (typeof IntersectionObserver === "undefined") {
      sections.forEach((section) => section.classList.add("is-visible"));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.01, rootMargin: "240px 0px 240px 0px" },
    );

    sections.forEach((section) => observer.observe(section));
    requestAnimationFrame(revealVisibleSections);

    window.addEventListener("scroll", revealVisibleSections, { passive: true });
    window.addEventListener("resize", revealVisibleSections);
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", revealVisibleSections);
      window.removeEventListener("resize", revealVisibleSections);
    };
  }, [hasResult, pdfExporting, reportAnimationKey, financialTimelineAnimationKey]);

  // --- 래퍼 클래스 ---
  const wrapperClass = isPanel
    ? result
      ? "w-full min-w-0 overflow-hidden bg-bg-sunk rounded-2xl border-2 border-line flex flex-col items-center py-6 sm:py-8 gap-4"
      : "w-full min-w-0 overflow-hidden bg-bg-sunk rounded-2xl border-2 border-line flex flex-col items-center justify-center py-[15rem] sm:py-[16rem] md:py-[17rem]"
    : "w-full min-w-0 overflow-hidden bg-bg-sunk rounded-2xl py-8 sm:py-10 flex flex-col items-center gap-4 mt-2";

  return (
    <div className={wrapperClass}>
      {/* 상단 헤더 바: 다운로드/확대 버튼 — result 있을 때만. data-pdf-exclude: PDF 캡처 시 제외 */}
      {result && (
        <div data-pdf-exclude="true" className="w-full min-w-0 max-w-4xl px-3 sm:w-[90%] sm:px-0 flex items-center justify-end">
          <div className="flex items-center gap-2">
            <div className="relative group">
              <button
                onClick={onDownload}
                aria-label={t("buttons.downloadPdf")}
                title={t("buttons.downloadPdf")}
                className="w-8 h-8 grid place-items-center rounded-lg border border-line bg-surface text-ink-2 hover:bg-bg-sunk transition-colors"
              >
                <Download size={16} />
              </button>
              <span className="pointer-events-none absolute bottom-full right-0 mb-2 whitespace-nowrap rounded-md bg-ink text-bg text-[11px] font-medium px-2 py-1 opacity-0 group-hover:opacity-100 transition-opacity z-20">
                {t("buttons.downloadPdf")}
              </span>
            </div>
            <div className="relative group">
              <button
                onClick={onZoom}
                aria-label={t("buttons.expand")}
                title={t("buttons.expand")}
                className="w-8 h-8 grid place-items-center rounded-lg border border-line bg-surface text-ink-2 hover:bg-bg-sunk transition-colors"
              >
                <Maximize2 size={16} />
              </button>
              <span className="pointer-events-none absolute bottom-full right-0 mb-2 whitespace-nowrap rounded-md bg-ink text-bg text-[11px] font-medium px-2 py-1 opacity-0 group-hover:opacity-100 transition-opacity z-20">
                {t("buttons.expand")}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* 분석 실행 버튼 */}
      {showAnalyzeButton && (
        <div className="flex flex-col items-center gap-3">
          <button
            onClick={onAnalyze}
            disabled={loading}
            className="px-6 sm:px-8 py-2.5 bg-accent rounded-2xl text-white text-base sm:text-xl md:text-2xl font-medium"
          >
            {t("buttons.viewAnalysis")}
          </button>
        </div>
      )}

      {/* 로딩 문구 */}
      {loading && (
        <div className="w-full rounded-2xl border-2 flex items-center justify-center py-24 bg-bg-sunk border-line">
          <p className="text-base animate-pulse text-ink-3">{t("status.loading")}</p>
        </div>
      )}

      {/* 에러 문구 */}
      {err && <p className="text-sm sm:text-base text-danger">{err}</p>}

      {/* 분석 결과 카드 */}
      {result && (
        <div
          key={reportAnimationKey}
          ref={reportRef}
          className={`qaima-analysis-report ${isFeature2Report ? "qaima-feature2-report " : ""}qaima-report-enter qaima-scroll-stagger w-full min-w-0 max-w-4xl sm:w-[90%] bg-surface rounded-2xl shadow-sm border border-line p-3 sm:p-6 flex flex-col gap-4 overflow-hidden`}
        >
          <ReportHeader meta={reportMeta} />

          {result.metrics?.stock && (
            <div className="rounded-lg border border-line bg-bg-sunk px-4 py-3">
              <p className="text-xs sm:text-sm font-medium text-ink-3">{t("subject")}</p>
              <p className="mt-1 text-lg sm:text-xl font-bold text-ink">
                {result.metrics.stock.companyName || result.metrics.stock.stockCode}
              </p>
            </div>
          )}

          {result.metrics?.investorFlow && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">{t("investorFlowCard.title")}</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  {t("investorFlowCard.compare")}
                </span>
              </div>
              <div className="mt-2 grid grid-cols-1 sm:grid-cols-3 gap-2">
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("investorFlowCard.stockDirection")}</p>
                  <p className="text-base font-bold text-ink">
                    {investorFlowDirectionText(result.metrics.investorFlow.stockSummary?.direction, t)}
                  </p>
                  <p className="mt-1 text-xs text-ink-3">
                    {formatFlowAmount(result.metrics.investorFlow.stockSummary?.combinedNetBuyValueMillionSum, t)}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("investorFlowCard.marketDirection")}</p>
                  <p className="text-base font-bold text-ink">
                    {investorFlowDirectionText(result.metrics.investorFlow.marketSummary?.direction, t)}
                  </p>
                  <p className="mt-1 text-xs text-ink-3">
                    {formatFlowAmount(result.metrics.investorFlow.marketSummary?.combinedNetBuyValueMillionSum, t)}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("investorFlowCard.tradingDays")}</p>
                  <p className="text-base font-bold text-ink">
                    {result.metrics.investorFlow.stockSummary?.pointCount ?? "-"}{t("investorFlowCard.dayUnit")}
                  </p>
                  <p className="mt-1 text-xs text-ink-3">
                    {result.metrics.investorFlow.marketCode ?? t("investorFlowCard.noMarketMapping")}
                  </p>
                </div>
              </div>
              {result.metrics.investorFlow.stockSeries?.length ? (
                <div className="mt-3">
                  <InvestorFlowTrendChart points={result.metrics.investorFlow.stockSeries} height={230} />
                </div>
              ) : null}
              {renderExplainSection(explainSections?.investorFlow, t)}
            </div>
          )}

          {result.metrics?.peerCluster && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">{t("peerCluster.title")}</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  {t("peerCluster.selectedCount", { n: result.metrics.peerCluster.peers.length })}
                </span>
              </div>
              <p className="mt-1 text-xs sm:text-sm text-ink-3">
                {t("peerCluster.disclaimer")}
              </p>
              {(() => {
                const selectedCodes = new Set(result.metrics?.peerCluster?.peers.map((peer) => peer.stockCode) ?? []);
                const candidates = result.metrics?.peerCluster?.candidates?.length
                  ? result.metrics.peerCluster.candidates
                  : result.metrics?.peerCluster?.peers ?? [];
                const selected = candidates.filter((peer) => peer.displayStatus === "SELECTED" || selectedCodes.has(peer.stockCode));
                const extra = candidates.filter((peer) => !(peer.displayStatus === "SELECTED" || selectedCodes.has(peer.stockCode)));
                const renderRows = (peers: PeerItem[]) => (
                  <div className="grid grid-cols-1 gap-2">
                    {peers.map((peer) => (
                      <div key={peer.stockCode} className={`rounded-lg border px-4 py-3 ${peerCardClass(peer)}`}>
                        <div className="flex flex-wrap items-center gap-3">
                          <div className="min-w-0 w-[120px] sm:w-[170px] flex-shrink-0">
                            <p className="text-sm sm:text-base font-semibold text-ink truncate">{peer.companyName ?? "-"}</p>
                            <p className="text-[11px] sm:text-xs text-ink-4">{peer.stockCode}</p>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[80px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">{displayPeerCorrLabel(peer, t)}</span>
                            <span className={`text-sm sm:text-base font-bold ${correlationColorClass(displayPeerCorr(peer))}`}>
                              {displayPeerCorr(peer) == null ? "-" : displayPeerCorr(peer)?.toFixed(2)}
                            </span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[70px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">{t("peerCluster.relation")}</span>
                            <span className={`text-sm sm:text-base font-semibold ${relationColorClass(peer.relation)}`}>
                              {formatRelationBadge(peer.relation, t)}
                            </span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[70px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">{t("peerCluster.score")}</span>
                            <span className="text-sm sm:text-base font-bold text-ink">{formatPeerScore(peer)}</span>
                          </div>
                          <div className="flex flex-col items-start sm:items-end flex-1 min-w-[92px]">
                            <span className="text-[11px] sm:text-xs text-ink-4">{t("peerCluster.status")}</span>
                            <span className="text-xs sm:text-sm font-medium text-ink-2">{formatDisplayStatus(peer, t)}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                );

                return (
                  <div className={`mt-3 rounded-lg border border-line bg-bg-sunk/60 p-3 pr-2 ${
                    pdfExporting ? "overflow-visible" : "max-h-[420px] overflow-y-auto"
                  }`}>
                    <div className="flex flex-col gap-4">
                      <div>
                        <h4 className="text-sm font-semibold text-ink">{t("peerCluster.coreHeading")}</h4>
                        <div className="mt-2">{selected.length > 0 ? renderRows(selected) : <p className="text-sm text-ink-3">{t("peerCluster.empty")}</p>}</div>
                      </div>
                      {extra.length > 0 && (
                        <div>
                          <h4 className="text-sm font-semibold text-ink">{t("peerCluster.additionalHeading")}</h4>
                          <div className="mt-2">{renderRows(extra)}</div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })()}
              {renderExplainSection(explainSections?.peerCluster, t)}
            </div>
          )}

          {result.metrics?.newsSentimentSummary && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">{t("news.title")}</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  {t("news.scoredCount", { n: result.metrics.newsSentimentSummary.scoredNewsCount })}
                </span>
              </div>
              <div className="mt-2 grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("news.recentAvg")}</p>
                  <p className={`text-lg font-bold ${result.metrics.newsSentimentSummary.dailyAvgScore != null && result.metrics.newsSentimentSummary.dailyAvgScore < -0.05 ? "text-blue-700" : result.metrics.newsSentimentSummary.dailyAvgScore != null && result.metrics.newsSentimentSummary.dailyAvgScore > 0.05 ? "text-rose-700" : "text-ink"}`}>
                    {formatSentimentScore(result.metrics.newsSentimentSummary.dailyAvgScore)}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("news.scoredNews")}</p>
                  <p className="text-lg font-bold text-ink">{result.metrics.newsSentimentSummary.scoredNewsCount}</p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("news.ratio")}</p>
                  <p className="text-sm font-semibold text-ink">
                    {result.metrics.newsSentimentSummary.positiveCount} / {result.metrics.newsSentimentSummary.neutralCount} / {result.metrics.newsSentimentSummary.negativeCount}
                  </p>
                </div>
                <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                  <p className="text-[11px] sm:text-xs text-ink-3">{t("news.asOf")}</p>
                  <p className="text-sm font-semibold text-ink">{result.metrics.newsSentimentSummary.summaryDate ?? "-"}</p>
                </div>
              </div>

              {(result.metrics?.newsList?.length ?? 0) > 0 && (
                <div className="mt-4">
                  <h4 className="text-sm font-semibold text-ink">{t("news.scoredHeading")}</h4>
                  <div className={`mt-2 divide-y divide-line rounded-lg border border-line bg-surface pr-1 ${
                    pdfExporting ? "overflow-visible" : "max-h-[360px] overflow-y-auto"
                  }`}>
                    {result.metrics?.newsList?.map((item, idx) => (
                      <a
                        key={item.newsId}
                        href={item.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-4 px-3 py-3 hover:bg-bg-sunk transition-colors ${
                          idx === 0 ? "rounded-t-lg" : ""
                        } ${idx === (result.metrics?.newsList?.length ?? 0) - 1 ? "rounded-b-lg" : ""}`}
                      >
                        <div className="min-w-0 flex-1 flex flex-col gap-2">
                          <div className="flex flex-col">
                            <h5 className="truncate text-sm sm:text-base font-semibold text-ink">
                              {item.title}
                            </h5>
                            <p className="line-clamp-2 text-xs sm:text-sm leading-snug text-ink-2">
                              {item.summary}
                            </p>
                          </div>
                          <p className="text-[11px] sm:text-xs font-medium text-ink-3">
                            {formatNewsTimeAgo(item.publishedAt, t)} · {item.publisher}
                          </p>
                        </div>
                        <div className={`flex min-w-[64px] flex-col items-center justify-center rounded-md border px-2 py-1 text-xs font-semibold ${sentimentBadgeClass(item.sentimentScore)}`}>
                          <span>{sentimentLabelText(item.sentimentLabel, item.sentimentScore, t)}</span>
                          <span>{formatSentimentScore(item.sentimentScore)}</span>
                        </div>
                      </a>
                    ))}
                  </div>
                </div>
              )}
              {renderExplainSection(explainSections?.newsSentiment, t)}
            </div>
          )}

          {(result.metrics?.macroRates
            || result.metrics?.macroRatesSeries?.series?.length
            || result.metrics?.baseRateTrendSummary
            || result.metrics?.shortSellingTrendSummary
            || result.metrics?.shortSellingSeries?.length) && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">{t("macroSeries.title")}</h3>
                <span className="rounded-full bg-bg-sunk px-2 py-1 text-[11px] font-medium text-ink-3">
                  {t("macroSeries.subtitle")}
                </span>
              </div>
              <div className="mt-2 grid grid-cols-2 sm:grid-cols-4 gap-2">
                {result.metrics?.macroRates && (
                  <>
                    <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                      <p className="text-[11px] sm:text-xs text-ink-3">{t("macroSeries.krBaseRate")}</p>
                      <p className="text-lg font-bold text-ink">
                        {formatRatePoint(result.metrics.macroRates.krBaseRate?.value, result.metrics.macroRates.krBaseRate?.unit ?? "%")}
                      </p>
                    </div>
                    <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                      <p className="text-[11px] sm:text-xs text-ink-3">{t("macroSeries.usBaseRate")}</p>
                      <p className="text-lg font-bold text-ink">
                        {formatRatePoint(result.metrics.macroRates.usFedFundsRate?.value, result.metrics.macroRates.usFedFundsRate?.unit ?? "%")}
                      </p>
                    </div>
                    <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                      <p className="text-[11px] sm:text-xs text-ink-3">USD/KRW</p>
                      <p className="text-lg font-bold text-ink">
                        {formatNumber(result.metrics.macroRates.usdKrw?.value)}
                      </p>
                    </div>
                  </>
                )}
                {result.metrics?.baseRateTrendSummary && (
                  <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                    <p className="text-[11px] sm:text-xs text-ink-3">{t("macroSeries.baseRateChange")}</p>
                    <p className="text-sm font-bold text-ink">
                      {result.metrics.baseRateTrendSummary.startValue ?? "-"}
                      {result.metrics.baseRateTrendSummary.unit ?? ""} → {result.metrics.baseRateTrendSummary.endValue ?? "-"}
                      {result.metrics.baseRateTrendSummary.unit ?? ""}
                    </p>
                    <p className="mt-1 text-xs text-ink-3">
                      {formatSignedNumber(result.metrics.baseRateTrendSummary.change)} · {trendDirectionText(result.metrics.baseRateTrendSummary.direction, t)}
                    </p>
                  </div>
                )}
                {result.metrics?.shortSellingTrendSummary && (
                  <div className="rounded-lg border border-line bg-bg-sunk px-3 py-2">
                    <p className="text-[11px] sm:text-xs text-ink-3">{t("macroSeries.shortSellingRatio")}</p>
                    <p className="text-sm font-bold text-ink">
                      {formatRatio(result.metrics.shortSellingTrendSummary.startShortAmountRatio)} → {formatRatio(result.metrics.shortSellingTrendSummary.endShortAmountRatio)}
                    </p>
                    <p className="mt-1 text-xs text-ink-3">
                      {formatSignedNumber(result.metrics.shortSellingTrendSummary.shortAmountRatioChange)}% · {trendDirectionText(result.metrics.shortSellingTrendSummary.direction, t)}
                    </p>
                  </div>
                )}
              </div>
              {(result.metrics?.macroRatesSeries?.series?.length || result.metrics?.shortSellingSeries?.length) ? (
                <div className="mt-3 flex flex-col gap-3">
                  {pdfExporting ? (
                    <div className="flex flex-col gap-4">
                      {[
                        { key: "exchange", labelKey: "macro.exchange", seriesKeys: ["USD_KRW"], height: 190 },
                        { key: "baseRate", labelKey: "macro.baseRate", seriesKeys: ["KR_BASE_RATE", "US_FED_FUNDS"], height: 230 },
                        { key: "domesticBond", labelKey: "macro.domesticBond", seriesKeys: ["KR3Y", "KR10Y"], height: 230 },
                        { key: "usRates", labelKey: "macro.usRates", seriesKeys: ["US2Y", "US5Y", "US10Y"], height: 230 },
                      ].map((chart) => {
                        const series = (result.metrics?.macroRatesSeries?.series ?? []).filter((item) =>
                          chart.seriesKeys.includes(item.key),
                        );
                        if (series.length === 0) return null;
                        return (
                          <div key={chart.key} className="rounded-lg border border-line bg-bg-sunk/60 p-3">
                            <h4 className="mb-2 text-sm font-semibold text-ink">{t(chart.labelKey)}</h4>
                            <MultiLineTrendChart series={series} height={chart.height} />
                          </div>
                        );
                      })}
                      {(result.metrics?.shortSellingSeries?.length ?? 0) > 0 && (
                        <div className="rounded-lg border border-line bg-bg-sunk/60 p-3">
                          <h4 className="mb-2 text-sm font-semibold text-ink">{t("macroSeries.shortSelling")}</h4>
                          <ShortSellingTrendChart points={result.metrics?.shortSellingSeries ?? []} />
                        </div>
                      )}
                    </div>
                  ) : (
                    <>
                      <div className="flex flex-wrap gap-1.5">
                        {macroChartOptions.map((option) => (
                          <button
                            key={option.key}
                            type="button"
                            onClick={() => setMacroChartMode(option.key)}
                            className={`rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
                              macroChartMode === option.key
                                ? "bg-accent text-white"
                                : "bg-bg-sunk text-ink-3 hover:bg-surface-2"
                            }`}
                          >
                            {option.label}
                          </button>
                        ))}
                      </div>
                      <div>
                        {macroChartMode === "shortSelling" ? (
                          <ShortSellingTrendChart points={result.metrics?.shortSellingSeries ?? []} />
                        ) : (
                          <MultiLineTrendChart
                            series={(result.metrics?.macroRatesSeries?.series ?? []).filter((item) => {
                              if (macroChartMode === "exchange") return item.key === "USD_KRW";
                              if (macroChartMode === "baseRate") return ["KR_BASE_RATE", "US_FED_FUNDS"].includes(item.key);
                              if (macroChartMode === "domesticBond") return ["KR3Y", "KR10Y"].includes(item.key);
                              return ["US2Y", "US5Y", "US10Y"].includes(item.key);
                            })}
                            height={macroChartMode === "exchange" ? 190 : 230}
                          />
                        )}
                      </div>
                    </>
                  )}
                </div>
              ) : null}
              {renderExplainSection(explainSections?.macroEnvironment, t)}
              {renderExplainSection(explainSections?.trendSummary, t)}
            </div>
          )}

          {/* 공매도 현황 */}
          {result.metrics?.shortSelling ? (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base sm:text-lg font-semibold text-ink">
                  <DictTerm term="공매도">{t("macroSeries.shortSelling")}</DictTerm> {t("shortSelling.titleSuffix")}
                </h3>
              </div>
              <div className="mt-2 max-w-full overflow-x-auto">
                <table className="min-w-full text-xs sm:text-sm text-ink-2 border border-line">
                  <thead className="bg-bg-sunk text-ink">
                    <tr>
                      <th className="px-3 py-2 text-left border-b">{t("shortSelling.headerItem")}</th>
                      <th className="px-3 py-2 text-right border-b">{t("shortSelling.headerValue")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className="px-3 py-2 border-b">{t("shortSelling.asOf")}</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.reportDate}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">{t("shortSelling.shortVolume")}</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.shortVolumeTotal)}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b"><DictTerm term="거래량">{t("shortSelling.volumeLabel")}</DictTerm> {t("shortSelling.totalVolume")}</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.totalVolume)}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">{t("shortSelling.shortVolumeRatio")}</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.shortVolumeRatio.toFixed(2)}%</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">{t("shortSelling.shortAmount")}</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.shortAmountTotal)}{t("amountUnit.won")}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b"><DictTerm term="거래대금">{t("shortSelling.amountLabel")}</DictTerm> {t("shortSelling.totalAmount")}</td>
                      <td className="px-3 py-2 text-right border-b">{formatNumber(result.metrics.shortSelling.totalAmount)}{t("amountUnit.won")}</td>
                    </tr>
                    <tr>
                      <td className="px-3 py-2 border-b">{t("shortSelling.shortAmountRatio")}</td>
                      <td className="px-3 py-2 text-right border-b">{result.metrics.shortSelling.shortAmountRatio.toFixed(2)}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              {renderExplainSection(explainSections?.shortSelling, t)}
            </div>
          ) : null}

          {/* 가격 흐름 요약 */}
          {priceFlowSummary && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <PriceFlowBars summary={priceFlowSummary} />
              {renderExplainSection(explainSections?.priceFlow, t)}
            </div>
          )}

          {/* 시장 스냅샷 */}
          {result.metrics?.marketSnapshot && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <MarketSnapshotBars snapshot={result.metrics.marketSnapshot} />
              {renderExplainSection(explainSections?.marketSnapshot, t)}
            </div>
          )}

          {/* 보조지표 요약 */}
          {result.metrics?.indicators && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <IndicatorSnapshotCards
                indicators={result.metrics.indicators}
              />
              {renderExplainSection(explainSections?.indicators, t)}
            </div>
          )}

          {financialTimeline && financialTimeline.points.length > 0 && (
            <div className="border-t border-line pt-4 first:border-t-0 first:pt-0">
              <FinancialTimelineChart
                period={financialTimeline.period}
                points={financialTimeline.points}
              />
              {renderExplainSection(explainSections?.financialTimeline, t)}
            </div>
          )}

          {renderExplainSection(explainSections?.crossSignal, t)}

          {(overallExplain?.summary
            || (overallExplain?.bullets && overallExplain.bullets.length > 0)
            || (overallExplain?.risks && overallExplain.risks.length > 0)
            || overallExplain?.conclusion
            || result.explain?.text?.trim()) && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-ink">
                {t("overall.title")}
              </h3>

              {overallExplain ? (
                <div className="mt-2 text-sm sm:text-base text-ink-2 flex flex-col gap-3">
                  {overallExplain.summary ? (
                    <div>
                      <p className="font-medium text-ink">{t("overall.summary")}</p>
                      <p><DictionaryText text={overallExplain.summary} /></p>
                    </div>
                  ) : null}
                  {overallExplain.bullets && overallExplain.bullets.length > 0 ? (
                    <div>
                      <p className="font-medium text-ink">{t("overall.highlights")}</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.bullets.map((item, idx) => (
                          <li key={`overall-bullet-${idx}`}>
                            <DictionaryText text={item} />
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.risks && overallExplain.risks.length > 0 ? (
                    <div>
                      <p className="font-medium text-ink">{t("overall.risks")}</p>
                      <ul className="list-disc list-inside">
                        {overallExplain.risks.map((item, idx) => (
                          <li key={`overall-risk-${idx}`}>
                            <DictionaryText text={item} />
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {overallExplain.conclusion ? (
                    <div>
                      <p className="font-medium text-ink">{t("overall.conclusion")}</p>
                      <p><DictionaryText text={overallExplain.conclusion} /></p>
                    </div>
                  ) : null}
                </div>
              ) : result.explain?.text?.trim() ? (
                <p className="text-sm sm:text-base text-ink-2 whitespace-pre-wrap mt-2">
                  <DictionaryText text={displayText} />
                </p>
              ) : null}
            </div>
          )}

          {warningNotes.length > 0 && (
            <div>
              <h3 className="text-base sm:text-lg font-semibold text-ink">
                {t("notes")}
              </h3>
              <ul className="mt-2 list-disc list-inside text-sm sm:text-base text-ink-2 flex flex-col gap-1">
                {warningNotes.map((item, idx) => (
                  <li key={`warning-note-${idx}`}>{item}</li>
                ))}
              </ul>
            </div>
          )}

        </div>
      )}
    </div>
  );
}
