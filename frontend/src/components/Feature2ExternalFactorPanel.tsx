import { useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Lock } from "lucide-react";
import DictTerm from "./DictTerm";
import InvestorFlowTrendChart from "./InvestorFlowTrendChart";
import ShortSellingTrendChart from "./ShortSellingTrendChart";
import { MiniTrendRow, MultiLineTrendChart } from "./Feature2TrendCharts";
import type {
  BaseRateMetrics,
  Feature2MacroRates,
  Feature2InvestorFlow,
  Feature2TrendSeries,
  PeerCluster,
  RelativePoint,
  ShortSellingSeriesPoint,
  ShortSellingMetrics,
} from "../types/feature2";
import type { NewsItemDto } from "../types/news";

type ExternalFactorTab = "summary" | "macro" | "flow" | "news" | "peer" | "risk";

interface RelatedStockDisplay {
  stockCode: string;
  companyName: string;
  price: number;
  change: number;
  changeRate: number;
  volume: number;
}

interface Props {
  stockName: string;
  baseRateMetrics: BaseRateMetrics | null;
  macroRates: Feature2MacroRates | null;
  macroRatesLoading: boolean;
  macroRatesError: string | null;
  macroTrendSeries: Feature2TrendSeries[];
  shortSellingSeries: ShortSellingSeriesPoint[];
  newsItems: NewsItemDto[];
  newsLoading: boolean;
  newsError: string | null;
  relatedStocks: RelatedStockDisplay[];
  relatedLoading: boolean;
  relatedError: string | null;
  shortSellingMetrics: ShortSellingMetrics | null;
  investorFlow: Feature2InvestorFlow | null;
  investorFlowLoading: boolean;
  investorFlowError: string | null;
  peerCluster: PeerCluster | null;
  onSelectRelatedStock: (stockCode: string) => void;
}

const tabKeys: ExternalFactorTab[] = ["summary", "macro", "flow", "news", "peer", "risk"];

const formatTimeAgo = (isoStr: string, t: (key: string, options?: Record<string, unknown>) => string): string => {
  const diff = Date.now() - new Date(isoStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return t("timeAgo.minutes", { n: Math.max(0, mins) });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t("timeAgo.hours", { n: hours });
  const days = Math.floor(hours / 24);
  if (days < 7) return t("timeAgo.days", { n: days });
  return new Date(isoStr).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" });
};

const formatNumber = (value?: number | null, digits = 2) => {
  if (value == null || !Number.isFinite(value)) return "-";
  return value.toLocaleString("ko-KR", {
    maximumFractionDigits: digits,
  });
};

const formatRate = (value?: number | null, unit = "%") => {
  if (value == null || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}${unit}`;
};

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  return value.slice(0, 10);
};

const formatFlowAmount = (value: number | null | undefined, t: (key: string) => string) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  if (abs >= 100_000_000) return `${sign}${(abs / 100_000_000).toFixed(1)}${t("amountUnit.trillion")}`;
  if (abs >= 10_000) return `${sign}${(abs / 10_000).toFixed(1)}${t("amountUnit.hundredMillion")}`;
  return `${sign}${Math.round(abs).toLocaleString("ko-KR")}${t("amountUnit.million")}`;
};

const investorFlowDirectionText = (direction: string | null | undefined, t: (key: string) => string) => {
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

const formatSignedPct = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const sign = value > 0 ? "+" : "";
  return `${sign}${(value * 100).toFixed(1)}%`;
};

type FlowStatus = "LOCKED" | "UP" | "DOWN" | "MIXED";

const flowStatusText = (delta?: number | null): FlowStatus => {
  if (delta == null || !Number.isFinite(delta)) return "LOCKED";
  if (delta > 0.02) return "UP";
  if (delta < -0.02) return "DOWN";
  return "MIXED";
};

const flowStatusClass = (status: FlowStatus) => {
  if (status === "UP") return "text-rise bg-rise-soft border-rise/30";
  if (status === "DOWN") return "text-fall bg-fall-soft border-fall/30";
  if (status === "MIXED") return "text-ink-2 bg-bg-sunk border-line";
  return "text-ink-3 bg-bg-sunk border-line";
};

const flowStatusLabel = (status: FlowStatus, t: (key: string) => string) => {
  if (status === "UP") return t("external.peer.status.up");
  if (status === "DOWN") return t("external.peer.status.down");
  if (status === "MIXED") return t("external.peer.status.mixed");
  return t("external.peer.status.locked");
};

const seriesDelta = (series?: RelativePoint[] | null) => {
  const points = (series ?? []).filter((point) => Number.isFinite(point.value));
  if (points.length < 2) return null;
  return points[points.length - 1].value - points[0].value;
};

function MetricTile({
  label,
  value,
  sub,
}: {
  label: ReactNode;
  value: ReactNode;
  sub?: ReactNode;
}) {
  return (
    <div className="min-w-0 overflow-hidden rounded-lg border border-line bg-bg-sunk px-3 py-2">
      <p className="text-[11px] font-medium text-ink-3">{label}</p>
      <p className="mt-1 min-w-0 break-words text-base font-bold text-ink">{value}</p>
      {sub && <p className="mt-0.5 min-w-0 break-words text-[11px] text-ink-3">{sub}</p>}
    </div>
  );
}

export default function Feature2ExternalFactorPanel({
  stockName,
  baseRateMetrics,
  macroRates,
  macroRatesLoading,
  macroRatesError,
  macroTrendSeries,
  shortSellingSeries,
  newsItems,
  newsLoading,
  newsError,
  relatedStocks,
  relatedLoading,
  relatedError,
  shortSellingMetrics,
  investorFlow,
  investorFlowLoading,
  investorFlowError,
  peerCluster,
  onSelectRelatedStock,
}: Props) {
  const { t } = useTranslation("analysisPanel");
  const [activeTab, setActiveTab] = useState<ExternalFactorTab>("summary");
  const tabs = tabKeys.map((key) => ({ key, label: t(`external.tabs.${key}`) }));
  const hasInvestorFlowData = Boolean(
    investorFlow?.stockSummary
      || investorFlow?.marketSummary
      || investorFlow?.stockSeries?.length
      || investorFlow?.marketSeries?.length,
  );
  const krBaseRate = macroRates?.krBaseRate ?? baseRateMetrics ?? null;
  const usdKrw = macroRates?.usdKrw ?? null;
  const krBondYields = useMemo(
    () => (macroRates?.bondYields ?? []).filter((item) => item.countryCode === "KR"),
    [macroRates?.bondYields],
  );
  const usBondYields = useMemo(
    () => (macroRates?.bondYields ?? []).filter((item) => item.countryCode === "US"),
    [macroRates?.bondYields],
  );
  const trendByKey = useMemo(() => {
    const map = new Map<string, Feature2TrendSeries>();
    for (const series of macroTrendSeries ?? []) {
      map.set(series.key, series);
    }
    return map;
  }, [macroTrendSeries]);
  const summaryTrendKeys = ["USD_KRW", "KR_BASE_RATE", "KR3Y", "KR10Y", "US_FED_FUNDS", "US2Y", "US10Y"];
  const summaryTrendSeries = summaryTrendKeys
    .map((key) => trendByKey.get(key))
    .filter((series): series is Feature2TrendSeries => Boolean(series));
  const domesticMacroSeries = ["KR_BASE_RATE", "KR3Y", "KR10Y"]
    .map((key) => trendByKey.get(key))
    .filter((series): series is Feature2TrendSeries => Boolean(series));
  const usMacroSeries = ["US_FED_FUNDS", "US2Y", "US5Y", "US10Y"]
    .map((key) => trendByKey.get(key))
    .filter((series): series is Feature2TrendSeries => Boolean(series));
  const exchangeSeries = ["USD_KRW"]
    .map((key) => trendByKey.get(key))
    .filter((series): series is Feature2TrendSeries => Boolean(series));
  const peerCentroidSeries = peerCluster?.peerCentroid?.length
    ? peerCluster.peerCentroid
    : peerCluster?.centroid ?? null;
  const peerDelta = seriesDelta(peerCentroidSeries);
  const anchorDelta = seriesDelta(peerCluster?.anchorSeries);
  const peerStatus = flowStatusText(peerDelta);
  const anchorVsPeer =
    anchorDelta == null || peerDelta == null
      ? null
      : anchorDelta - peerDelta;
  const peerFlowLocked = !peerCluster;

  const renderSummary = () => (
    <div className="flex flex-col gap-3">
      {macroRatesError && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
          {macroRatesError}
        </div>
      )}
      <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-2">
        <MetricTile
          label={<DictTerm term="기준금리">{t("macroSeries.krBaseRate")}</DictTerm>}
          value={formatRate(krBaseRate?.value, krBaseRate?.unit ?? "%")}
          sub={formatDate(krBaseRate?.date)}
        />
        <MetricTile
          label="USD/KRW"
          value={macroRatesLoading ? t("external.loading.short") : usdKrw ? formatNumber(usdKrw.value, 2) : "-"}
          sub={usdKrw ? formatDate(usdKrw.date) : t("external.empty.data")}
        />
        <MetricTile
          label={t("external.flow.foreignInstitution")}
          value={
            investorFlowLoading
              ? t("external.loading.short")
              : investorFlow?.stockSummary
                ? investorFlowDirectionText(investorFlow.stockSummary.direction, t)
                : "-"
          }
          sub={
            investorFlow?.stockSummary
              ? formatFlowAmount(investorFlow.stockSummary.combinedNetBuyValueMillionSum, t)
              : t("external.empty.data")
          }
        />
        <MetricTile
          label={t("external.flow.marketFlow")}
          value={
            investorFlowLoading
              ? t("external.loading.short")
              : investorFlow?.marketSummary
                ? formatFlowAmount(investorFlow.marketSummary.combinedNetBuyValueMillionSum, t)
                : "-"
          }
          sub={investorFlow?.marketCode ?? t("investorFlowCard.noMarketMapping")}
        />
        <MetricTile
          label={t("external.peer.flowTitle")}
          value={
            peerFlowLocked ? (
              <span className="inline-flex items-center gap-1 text-ink-3">
                <Lock size={14} aria-hidden="true" />
                {t("external.peer.status.locked")}
              </span>
            ) : (
              <span className={`inline-flex rounded-full border px-2 py-0.5 text-sm ${flowStatusClass(peerStatus)}`}>
                {flowStatusLabel(peerStatus, t)}
              </span>
            )
          }
          sub={
            peerFlowLocked
              ? t("external.peer.unlockAfterAnalysis")
              : t("external.peer.avgFlow", { value: formatSignedPct(peerDelta) })
          }
        />
        <MetricTile
          label={<DictTerm term="공매도">{t("shortSelling.shortSelling")}</DictTerm>}
          value={shortSellingMetrics ? `${shortSellingMetrics.shortAmountRatio.toFixed(2)}%` : "-"}
          sub={shortSellingMetrics ? formatDate(shortSellingMetrics.reportDate) : t("external.empty.data")}
        />
      </div>
      <div className="rounded-lg border border-line bg-surface px-3 py-3">
        <p className="text-xs font-semibold text-ink-2">{t("external.summary.rateEnvironment")}</p>
        <div className="mt-2 grid min-w-0 grid-cols-1 gap-2 text-xs sm:grid-cols-2">
          {[...krBondYields, ...usBondYields].slice(0, 4).map((item) => (
            <div key={item.instrumentCode} className="flex items-center justify-between gap-2">
              <span className="text-ink-3">{item.instrumentCode}</span>
              <span className="font-semibold text-ink">{formatRate(item.value, item.unit)}</span>
            </div>
          ))}
          {krBondYields.length + usBondYields.length === 0 && (
            <p className="col-span-2 text-ink-3">
              {macroRatesLoading ? t("external.loading.bonds") : t("external.empty.bonds")}
            </p>
          )}
        </div>
      </div>
      <div className="flex flex-col gap-2">
        <p className="text-xs font-semibold text-ink-2">{t("external.summary.simpleTrend")}</p>
        {summaryTrendSeries.slice(0, 7).map((series, index) => (
          <MiniTrendRow
            key={series.key}
            series={series}
            color={index % 2 === 0 ? "blue" : "rose"}
          />
        ))}
        {summaryTrendSeries.length === 0 && (
          <div className="rounded-lg border border-line bg-bg-sunk px-3 py-5 text-center text-sm text-ink-3">
            {macroRatesLoading ? t("external.loading.trends") : t("external.empty.trends")}
          </div>
        )}
      </div>
    </div>
  );

  const renderMacro = () => (
    <div className="flex flex-col gap-3">
      {macroRatesError && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
          {macroRatesError}
        </div>
      )}
      <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-2">
        <MetricTile
          label={t("macroSeries.krBaseRate")}
          value={formatRate(krBaseRate?.value, krBaseRate?.unit ?? "%")}
          sub={formatDate(krBaseRate?.date)}
        />
        <MetricTile
          label={t("macroSeries.usBaseRate")}
          value={formatRate(macroRates?.usFedFundsRate?.value, macroRates?.usFedFundsRate?.unit ?? "%")}
          sub={formatDate(macroRates?.usFedFundsRate?.date)}
        />
        <MetricTile
          label="USD/KRW"
          value={usdKrw ? formatNumber(usdKrw.value, 2) : "-"}
          sub={usdKrw ? `${usdKrw.baseCurrency}/${usdKrw.quoteCurrency}` : t("external.empty.data")}
        />
        <MetricTile
          label={t("external.macro.fxAsOf")}
          value={formatDate(usdKrw?.date)}
          sub={usdKrw?.source ?? "-"}
        />
      </div>
      <BondYieldList title={t("external.macro.krRates")} items={krBondYields} emptyText={t("external.empty.data")} countUnit={t("external.countUnit")} />
      <BondYieldList title={t("external.macro.usRates")} items={usBondYields} emptyText={t("external.empty.data")} countUnit={t("external.countUnit")} />
      <div className="flex flex-col gap-3">
        <div>
          <p className="mb-2 text-sm font-semibold text-ink">{t("external.macro.fxTrend")}</p>
          <MultiLineTrendChart series={exchangeSeries} height={190} />
        </div>
        <div>
          <p className="mb-2 text-sm font-semibold text-ink">{t("external.macro.krRateTrend")}</p>
          <MultiLineTrendChart series={domesticMacroSeries} height={210} />
        </div>
        <div>
          <p className="mb-2 text-sm font-semibold text-ink">{t("external.macro.usRateTrend")}</p>
          <MultiLineTrendChart series={usMacroSeries} height={210} />
        </div>
      </div>
    </div>
  );

  const renderFlow = () => (
    <div className="flex flex-col gap-3">
      {investorFlowLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-ink-3">
          {t("external.loading.flow")}
        </div>
      )}
      {investorFlowError && !investorFlowLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-danger">
          {investorFlowError}
        </div>
      )}
      {!investorFlowLoading && !investorFlowError && !hasInvestorFlowData && (
        <div className="min-h-[360px] flex items-center justify-center text-sm text-ink-3 rounded-lg border border-line bg-bg-sunk">
          {t("external.empty.flow")}
        </div>
      )}
      {!investorFlowLoading && !investorFlowError && hasInvestorFlowData && investorFlow && (
        <>
          <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-2">
            <MetricTile
              label={t("external.flow.foreignAccum")}
              value={formatFlowAmount(investorFlow.stockSummary?.foreignNetBuyValueMillionSum, t)}
              sub={investorFlow.stockSummary ? t("external.flow.tradingDays", { count: investorFlow.stockSummary.pointCount }) : t("external.empty.stockData")}
            />
            <MetricTile
              label={t("external.flow.institutionAccum")}
              value={formatFlowAmount(investorFlow.stockSummary?.institutionNetBuyValueMillionSum, t)}
              sub={investorFlow.stockSummary ? investorFlowDirectionText(investorFlow.stockSummary.direction, t) : t("external.empty.stockData")}
            />
            <MetricTile
              label={t("external.flow.combined")}
              value={formatFlowAmount(investorFlow.stockSummary?.combinedNetBuyValueMillionSum, t)}
              sub={investorFlow.stockSummary
                ? `${formatDate(investorFlow.stockSummary.startDate)} ~ ${formatDate(investorFlow.stockSummary.endDate)}`
                : t("external.empty.stockData")}
            />
            <MetricTile
              label={t("external.flow.marketFlow")}
              value={
                investorFlow.marketSummary
                  ? formatFlowAmount(investorFlow.marketSummary.combinedNetBuyValueMillionSum, t)
                  : "-"
              }
              sub={investorFlow.marketCode ?? t("investorFlowCard.noMarketMapping")}
            />
          </div>
          {investorFlow.stockSeries?.length > 0 && (
            <div>
              <p className="mb-2 text-sm font-semibold text-ink">{t("external.flow.stockChart")}</p>
              <InvestorFlowTrendChart points={investorFlow.stockSeries} height={210} />
            </div>
          )}
          {investorFlow.marketSeries?.length > 0 && (
            <div>
              <p className="mb-2 text-sm font-semibold text-ink">{t("external.flow.marketChart")}</p>
              <InvestorFlowTrendChart points={investorFlow.marketSeries} height={190} />
            </div>
          )}
        </>
      )}
    </div>
  );

  const renderNews = () => (
    <div className="flex flex-col gap-3">
      {newsLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-ink-3">
          {t("external.loading.news")}
        </div>
      )}

      {newsError && !newsLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-danger">
          {newsError ?? t("external.error.news")}
        </div>
      )}

      {!newsLoading && !newsError && (
        <div className="flex flex-col min-h-[360px] max-h-[520px] overflow-y-auto rounded-lg border border-line bg-surface">
          {newsItems.map((item, idx) => (
            <a
              key={item.newsId}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className={`flex items-center gap-4 px-3 py-3 bg-surface border-t ${
                idx === newsItems.length - 1 ? "border-b" : ""
              } border-line hover:bg-bg-sunk transition-colors`}
            >
              <div className="flex-1 flex flex-col gap-2 min-w-0">
                <div className="flex flex-col">
                  <h4 className="text-ink text-sm font-semibold line-clamp-1">
                    {item.title}
                  </h4>
                  <p className="text-ink-2 text-xs leading-snug line-clamp-2">
                    {item.summary}
                  </p>
                </div>
                <p className="text-ink-3 text-[11px] font-medium">
                  {formatTimeAgo(item.publishedAt, t)} · {item.publisher}
                </p>
              </div>
            </a>
          ))}

          {newsItems.length === 0 && (
            <div className="flex-1 flex items-center justify-center text-sm text-ink-3">
              {t("external.empty.news")}
            </div>
          )}
        </div>
      )}
    </div>
  );

  const renderPeer = () => (
    <div className="flex flex-col gap-3">
      <PeerFlowSummaryCard
        t={t}
        locked={peerFlowLocked}
        peerStatus={peerStatus}
        peerDelta={peerDelta}
        anchorDelta={anchorDelta}
        anchorVsPeer={anchorVsPeer}
        peerCount={peerCluster?.peers?.length ?? 0}
      />
      {relatedLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-ink-3">
          {t("external.loading.peer")}
        </div>
      )}

      {relatedError && !relatedLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-danger">
          {relatedError ?? t("external.error.peer")}
        </div>
      )}

      {!relatedLoading && !relatedError && (
        <div className="flex flex-col min-h-[360px] max-h-[520px] overflow-y-auto rounded-lg border border-line bg-surface">
          {relatedStocks.map((row, idx) => {
            const colorClass = row.change > 0 ? "text-rise" : row.change < 0 ? "text-fall" : "text-flat";
            const fmtPrice = row.price.toLocaleString("ko-KR");
            const fmtVolume = row.volume.toLocaleString("ko-KR");
            const fmtChange = row.change > 0 ? `+${row.change.toLocaleString("ko-KR")}` : row.change.toLocaleString("ko-KR");
            const fmtRate = row.change > 0 ? `+${row.changeRate.toFixed(2)}%` : row.change < 0 ? `${row.changeRate.toFixed(2)}%` : "0.00%";

            return (
              <button
                key={row.stockCode}
                onClick={() => onSelectRelatedStock(row.stockCode)}
                className={`flex items-center gap-2 px-3 py-3 bg-surface border-t ${
                  idx === relatedStocks.length - 1 ? "border-b" : ""
                } border-line hover:bg-bg-sunk transition-colors cursor-pointer w-full text-left`}
              >
                <div className="min-w-0 w-[104px] flex-shrink-0">
                  <p className="text-sm font-semibold text-ink truncate">{row.companyName}</p>
                  <p className="text-[10px] text-ink-4">{row.stockCode}</p>
                </div>
                <div className="flex flex-col items-end flex-1 min-w-0">
                  <span className={`text-sm font-semibold ${colorClass}`}>{fmtPrice}</span>
                  <span className="text-xs text-ink-3">{fmtVolume}</span>
                </div>
                <div className="flex-shrink-0 w-3 flex items-center justify-center">
                  {row.change > 0 && (
                    <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-b-[7px] border-transparent border-b-current`} />
                  )}
                  {row.change < 0 && (
                    <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-t-[7px] border-transparent border-t-current`} />
                  )}
                  {row.change === 0 && (
                    <span className="text-ink text-lg font-extrabold leading-none">-</span>
                  )}
                </div>
                <div className="flex flex-col items-end flex-shrink-0 min-w-[64px]">
                  <span className={`text-sm font-semibold ${colorClass}`}>{fmtChange}</span>
                  <span className={`text-xs font-medium ${colorClass}`}>{fmtRate}</span>
                </div>
              </button>
            );
          })}

          {relatedStocks.length === 0 && (
            <div className="flex-1 flex items-center justify-center text-sm text-ink-3">
              {t("external.empty.peer")}
            </div>
          )}
        </div>
      )}
    </div>
  );

  const renderRisk = () => {
    const ss = shortSellingMetrics;
    if (!ss) {
      return (
        <div className="min-h-[360px] flex items-center justify-center text-sm text-ink-3 rounded-lg border border-line bg-bg-sunk">
          {t("external.empty.shortSelling")}
        </div>
      );
    }

    const rows: { label: ReactNode; value: string }[] = [
      { label: t("shortSelling.asOf"), value: ss.reportDate },
      { label: t("shortSelling.shortVolume"), value: formatNumber(ss.shortVolumeTotal, 0) },
      { label: <><DictTerm term="거래량">{t("shortSelling.volumeLabel")}</DictTerm> {t("shortSelling.totalVolume")}</>, value: formatNumber(ss.totalVolume, 0) },
      { label: t("shortSelling.shortVolumeRatio"), value: `${ss.shortVolumeRatio.toFixed(2)}%` },
      { label: t("shortSelling.shortAmount"), value: `${formatNumber(ss.shortAmountTotal, 0)}${t("amountUnit.won")}` },
      { label: <><DictTerm term="거래대금">{t("shortSelling.amountLabel")}</DictTerm> {t("shortSelling.totalAmount")}</>, value: `${formatNumber(ss.totalAmount, 0)}${t("amountUnit.won")}` },
      { label: t("shortSelling.shortAmountRatio"), value: `${ss.shortAmountRatio.toFixed(2)}%` },
    ];

    return (
      <div className="flex flex-col gap-3">
        <div className="flex flex-col rounded-lg border border-line bg-surface overflow-hidden">
          {rows.map((row, idx) => (
            <div
              key={`short-selling-row-${idx}`}
              className={`flex items-center justify-between gap-3 px-3 py-3 border-t ${
                idx === rows.length - 1 ? "border-b" : ""
              } border-line`}
            >
              <span className="text-xs sm:text-sm text-ink-3">{row.label}</span>
              <span className="text-xs sm:text-sm font-semibold text-ink text-right">{row.value}</span>
            </div>
          ))}
        </div>
        <ShortSellingTrendChart points={shortSellingSeries} />
      </div>
    );
  };

  const content = {
    summary: renderSummary,
    macro: renderMacro,
    flow: renderFlow,
    news: renderNews,
    peer: renderPeer,
    risk: renderRisk,
  }[activeTab];

  return (
    <section className="qaima-mobile-safe-panel w-full min-w-0 flex-1 overflow-hidden bg-surface rounded-2xl border border-line shadow-card px-3 py-4 sm:px-4 flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h3 className="text-ink text-lg font-semibold">{t("external.panel.title")}</h3>
        <p className="text-xs text-ink-3 truncate">
          {t("external.panel.basedOn", { name: stockName || t("external.panel.subjectFallback") })}
        </p>
      </div>
      <div className="grid grid-cols-3 gap-1.5 min-w-0">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`h-9 rounded-md border text-xs font-semibold transition-colors ${
              activeTab === tab.key
                ? "border-accent bg-accent text-white"
                : "border-line bg-bg-sunk text-ink-3 hover:bg-bg-sunk hover:text-ink hover:border-line-strong"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto min-h-[420px] min-w-0">
        {content()}
      </div>
    </section>
  );
}

function PeerFlowSummaryCard({
  t,
  locked,
  peerStatus,
  peerDelta,
  anchorDelta,
  anchorVsPeer,
  peerCount,
}: {
  t: (key: string, options?: Record<string, unknown>) => string;
  locked: boolean;
  peerStatus: FlowStatus;
  peerDelta: number | null;
  anchorDelta: number | null;
  anchorVsPeer: number | null;
  peerCount: number;
}) {
  if (locked) {
    return (
      <div className="rounded-lg border border-dashed border-line bg-bg-sunk px-3 py-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-ink-2">{t("external.peer.flowTitle")}</p>
            <p className="mt-1 text-xs text-ink-3">{t("external.peer.unlockDescription")}</p>
          </div>
          <span className="inline-flex items-center gap-1 rounded-full border border-line bg-surface px-2.5 py-1 text-xs font-semibold text-ink-3">
            <Lock size={13} aria-hidden="true" />
            {t("external.peer.status.locked")}
          </span>
        </div>
      </div>
    );
  }

  const relativeText =
    anchorVsPeer == null
      ? t("external.peer.noComparison")
      : anchorVsPeer > 0.01
        ? t("external.peer.anchorStronger", { value: formatSignedPct(anchorVsPeer) })
        : anchorVsPeer < -0.01
          ? t("external.peer.anchorWeaker", { value: formatSignedPct(Math.abs(anchorVsPeer)) })
          : t("external.peer.similarFlow");

  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-ink">{t("external.peer.flowTitle")}</p>
          <p className="mt-1 text-xs text-ink-3">{relativeText}</p>
        </div>
        <span className={`rounded-full border px-2.5 py-1 text-xs font-bold ${flowStatusClass(peerStatus)}`}>
          {flowStatusLabel(peerStatus, t)}
        </span>
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <div className="rounded-md bg-bg-sunk px-2 py-2">
          <p className="text-ink-3">{t("external.peer.peerAverage")}</p>
          <p className="mt-0.5 font-bold text-ink">{formatSignedPct(peerDelta)}</p>
        </div>
        <div className="rounded-md bg-bg-sunk px-2 py-2">
          <p className="text-ink-3">{t("external.peer.anchorStock")}</p>
          <p className="mt-0.5 font-bold text-ink">{formatSignedPct(anchorDelta)}</p>
        </div>
        <div className="rounded-md bg-bg-sunk px-2 py-2">
          <p className="text-ink-3">{t("external.peer.peerCount")}</p>
          <p className="mt-0.5 font-bold text-ink">{t("external.peer.countUnit", { count: peerCount })}</p>
        </div>
      </div>
    </div>
  );
}

function BondYieldList({
  title,
  items,
  emptyText,
  countUnit,
}: {
  title: string;
  items: NonNullable<Feature2MacroRates["bondYields"]>;
  emptyText: string;
  countUnit: string;
}) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-ink">{title}</p>
        <span className="text-[11px] text-ink-4">{items.length}{countUnit}</span>
      </div>
      <div className="mt-2 flex flex-col divide-y divide-line">
        {items.map((item) => (
          <div key={item.instrumentCode} className="flex items-center justify-between gap-3 py-2">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-ink-2">{item.instrumentCode}</p>
              <p className="text-[11px] text-ink-3">{item.instrumentName} · {formatDate(item.date)}</p>
            </div>
            <p className="text-sm font-bold text-ink">{formatRate(item.value, item.unit)}</p>
          </div>
        ))}
        {items.length === 0 && (
          <p className="py-5 text-center text-sm text-ink-3">{emptyText}</p>
        )}
      </div>
    </div>
  );
}
