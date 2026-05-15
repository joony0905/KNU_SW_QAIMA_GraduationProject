import { useMemo, useState, type ReactNode } from "react";
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

const tabs: { key: ExternalFactorTab; label: string }[] = [
  { key: "summary", label: "요약" },
  { key: "macro", label: "금리·환율" },
  { key: "flow", label: "수급" },
  { key: "news", label: "뉴스" },
  { key: "peer", label: "유사종목" },
  { key: "risk", label: "공매도" },
];

const formatTimeAgo = (isoStr: string): string => {
  const diff = Date.now() - new Date(isoStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${Math.max(0, mins)}분 전`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;
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

const formatFlowAmount = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const abs = Math.abs(value);
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  if (abs >= 100_000_000) return `${sign}${(abs / 100_000_000).toFixed(1)}조원`;
  if (abs >= 10_000) return `${sign}${(abs / 10_000).toFixed(1)}억원`;
  return `${sign}${Math.round(abs).toLocaleString("ko-KR")}백만원`;
};

const investorFlowDirectionText = (direction?: string | null) => {
  switch (direction) {
    case "BOTH_NET_BUY":
      return "동반 순매수";
    case "BOTH_NET_SELL":
      return "동반 순매도";
    case "FOREIGN_BUY_INSTITUTION_SELL":
      return "외국인 매수·기관 매도";
    case "FOREIGN_SELL_INSTITUTION_BUY":
      return "외국인 매도·기관 매수";
    default:
      return "혼재";
  }
};

const formatSignedPct = (value?: number | null) => {
  if (value == null || !Number.isFinite(value)) return "-";
  const sign = value > 0 ? "+" : "";
  return `${sign}${(value * 100).toFixed(1)}%`;
};

const flowStatusText = (delta?: number | null) => {
  if (delta == null || !Number.isFinite(delta)) return "잠금";
  if (delta > 0.02) return "상승";
  if (delta < -0.02) return "하락";
  return "혼재";
};

const flowStatusClass = (status: string) => {
  if (status === "상승") return "text-rose-700 bg-rose-50 border-rose-200";
  if (status === "하락") return "text-blue-700 bg-blue-50 border-blue-200";
  if (status === "혼재") return "text-zinc-700 bg-zinc-100 border-zinc-200";
  return "text-zinc-500 bg-zinc-50 border-zinc-200";
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
    <div className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2">
      <p className="text-[11px] font-medium text-zinc-500">{label}</p>
      <p className="mt-1 text-base font-bold text-zinc-900">{value}</p>
      {sub && <p className="mt-0.5 text-[11px] text-zinc-500">{sub}</p>}
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
  const [activeTab, setActiveTab] = useState<ExternalFactorTab>("summary");
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
      <div className="grid grid-cols-2 gap-2">
        <MetricTile
          label={<DictTerm term="기준금리">기준금리</DictTerm>}
          value={formatRate(krBaseRate?.value, krBaseRate?.unit ?? "%")}
          sub={formatDate(krBaseRate?.date)}
        />
        <MetricTile
          label="USD/KRW"
          value={macroRatesLoading ? "조회중" : usdKrw ? formatNumber(usdKrw.value, 2) : "-"}
          sub={usdKrw ? formatDate(usdKrw.date) : "데이터 없음"}
        />
        <MetricTile
          label="외국인/기관"
          value={
            investorFlowLoading
              ? "조회중"
              : investorFlow?.stockSummary
                ? investorFlowDirectionText(investorFlow.stockSummary.direction)
                : "-"
          }
          sub={
            investorFlow?.stockSummary
              ? formatFlowAmount(investorFlow.stockSummary.combinedNetBuyValueMillionSum)
              : "데이터 없음"
          }
        />
        <MetricTile
          label="유사종목 흐름"
          value={
            peerFlowLocked ? (
              <span className="inline-flex items-center gap-1 text-zinc-500">
                <Lock size={14} aria-hidden="true" />
                잠금
              </span>
            ) : (
              <span className={`inline-flex rounded-full border px-2 py-0.5 text-sm ${flowStatusClass(peerStatus)}`}>
                {peerStatus}
              </span>
            )
          }
          sub={
            peerFlowLocked
              ? "분석 결과 보기 후 활성화"
              : `피어 평균 ${formatSignedPct(peerDelta)}`
          }
        />
        <MetricTile
          label={<DictTerm term="공매도">공매도</DictTerm>}
          value={shortSellingMetrics ? `${shortSellingMetrics.shortAmountRatio.toFixed(2)}%` : "-"}
          sub={shortSellingMetrics ? formatDate(shortSellingMetrics.reportDate) : "데이터 없음"}
        />
      </div>
      <div className="rounded-lg border border-zinc-200 bg-white px-3 py-3">
        <p className="text-xs font-semibold text-zinc-800">금리 환경</p>
        <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
          {[...krBondYields, ...usBondYields].slice(0, 4).map((item) => (
            <div key={item.instrumentCode} className="flex items-center justify-between gap-2">
              <span className="text-zinc-500">{item.instrumentCode}</span>
              <span className="font-semibold text-zinc-900">{formatRate(item.value, item.unit)}</span>
            </div>
          ))}
          {krBondYields.length + usBondYields.length === 0 && (
            <p className="col-span-2 text-zinc-500">
              {macroRatesLoading ? "국채 데이터를 확인하는 중입니다." : "국채 데이터가 없습니다."}
            </p>
          )}
        </div>
      </div>
      <div className="flex flex-col gap-2">
        <p className="text-xs font-semibold text-zinc-800">간단 추세</p>
        {summaryTrendSeries.slice(0, 7).map((series, index) => (
          <MiniTrendRow
            key={series.key}
            series={series}
            color={index % 2 === 0 ? "blue" : "rose"}
          />
        ))}
        {summaryTrendSeries.length === 0 && (
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-5 text-center text-sm text-zinc-500">
            {macroRatesLoading ? "추세 데이터를 확인하는 중입니다." : "표시할 추세 데이터가 없습니다."}
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
      <div className="grid grid-cols-2 gap-2">
        <MetricTile
          label="한국 기준금리"
          value={formatRate(krBaseRate?.value, krBaseRate?.unit ?? "%")}
          sub={formatDate(krBaseRate?.date)}
        />
        <MetricTile
          label="미국 기준금리"
          value={formatRate(macroRates?.usFedFundsRate?.value, macroRates?.usFedFundsRate?.unit ?? "%")}
          sub={formatDate(macroRates?.usFedFundsRate?.date)}
        />
        <MetricTile
          label="USD/KRW"
          value={usdKrw ? formatNumber(usdKrw.value, 2) : "-"}
          sub={usdKrw ? `${usdKrw.baseCurrency}/${usdKrw.quoteCurrency}` : "데이터 없음"}
        />
        <MetricTile
          label="환율 기준일"
          value={formatDate(usdKrw?.date)}
          sub={usdKrw?.source ?? "-"}
        />
      </div>
      <BondYieldList title="국내 금리" items={krBondYields} />
      <BondYieldList title="미국 금리" items={usBondYields} />
      <div className="flex flex-col gap-3">
        <div>
          <p className="mb-2 text-sm font-semibold text-zinc-900">환율 추세</p>
          <MultiLineTrendChart series={exchangeSeries} height={190} />
        </div>
        <div>
          <p className="mb-2 text-sm font-semibold text-zinc-900">국내 금리 추세</p>
          <MultiLineTrendChart series={domesticMacroSeries} height={210} />
        </div>
        <div>
          <p className="mb-2 text-sm font-semibold text-zinc-900">미국 금리 추세</p>
          <MultiLineTrendChart series={usMacroSeries} height={210} />
        </div>
      </div>
    </div>
  );

  const renderFlow = () => (
    <div className="flex flex-col gap-3">
      {investorFlowLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-gray-500">
          수급 데이터를 불러오는 중입니다…
        </div>
      )}
      {investorFlowError && !investorFlowLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-red-500">
          {investorFlowError}
        </div>
      )}
      {!investorFlowLoading && !investorFlowError && !investorFlow?.stockSummary && (
        <div className="min-h-[360px] flex items-center justify-center text-sm text-gray-500 rounded-lg border border-zinc-200 bg-zinc-50">
          수급 데이터가 없습니다.
        </div>
      )}
      {!investorFlowLoading && !investorFlowError && investorFlow?.stockSummary && (
        <>
          <div className="grid grid-cols-2 gap-2">
            <MetricTile
              label="외국인 누적"
              value={formatFlowAmount(investorFlow.stockSummary.foreignNetBuyValueMillionSum)}
              sub={`${investorFlow.stockSummary.pointCount}거래일`}
            />
            <MetricTile
              label="기관 누적"
              value={formatFlowAmount(investorFlow.stockSummary.institutionNetBuyValueMillionSum)}
              sub={investorFlowDirectionText(investorFlow.stockSummary.direction)}
            />
            <MetricTile
              label="외국인+기관"
              value={formatFlowAmount(investorFlow.stockSummary.combinedNetBuyValueMillionSum)}
              sub={`${formatDate(investorFlow.stockSummary.startDate)} ~ ${formatDate(investorFlow.stockSummary.endDate)}`}
            />
            <MetricTile
              label="시장 수급"
              value={
                investorFlow.marketSummary
                  ? formatFlowAmount(investorFlow.marketSummary.combinedNetBuyValueMillionSum)
                  : "-"
              }
              sub={investorFlow.marketCode ?? "시장 매핑 없음"}
            />
          </div>
          <div>
            <p className="mb-2 text-sm font-semibold text-zinc-900">종목 외국인/기관 순매수</p>
            <InvestorFlowTrendChart points={investorFlow.stockSeries ?? []} height={210} />
          </div>
          {investorFlow.marketSeries?.length > 0 && (
            <div>
              <p className="mb-2 text-sm font-semibold text-zinc-900">시장 외국인/기관 순매수</p>
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
        <div className="h-40 flex items-center justify-center text-sm text-gray-500">
          뉴스를 불러오는 중입니다…
        </div>
      )}

      {newsError && !newsLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-red-500">
          {newsError ?? "뉴스를 불러오지 못했습니다."}
        </div>
      )}

      {!newsLoading && !newsError && (
        <div className="flex flex-col min-h-[360px] max-h-[520px] overflow-y-auto rounded-lg border border-zinc-200 bg-white">
          {newsItems.map((item, idx) => (
            <a
              key={item.newsId}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className={`flex items-center gap-4 px-3 py-3 bg-white border-t ${
                idx === newsItems.length - 1 ? "border-b" : ""
              } border-zinc-200 hover:bg-zinc-50 transition-colors`}
            >
              <div className="flex-1 flex flex-col gap-2 min-w-0">
                <div className="flex flex-col">
                  <h4 className="text-black text-sm font-semibold line-clamp-1">
                    {item.title}
                  </h4>
                  <p className="text-zinc-700 text-xs leading-snug line-clamp-2">
                    {item.summary}
                  </p>
                </div>
                <p className="text-zinc-500 text-[11px] font-medium">
                  {formatTimeAgo(item.publishedAt)} · {item.publisher}
                </p>
              </div>
            </a>
          ))}

          {newsItems.length === 0 && (
            <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
              표시할 뉴스가 없습니다.
            </div>
          )}
        </div>
      )}
    </div>
  );

  const renderPeer = () => (
    <div className="flex flex-col gap-3">
      <PeerFlowSummaryCard
        locked={peerFlowLocked}
        peerStatus={peerStatus}
        peerDelta={peerDelta}
        anchorDelta={anchorDelta}
        anchorVsPeer={anchorVsPeer}
        peerCount={peerCluster?.peers?.length ?? 0}
      />
      {relatedLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-gray-500">
          유사 종목을 불러오는 중입니다…
        </div>
      )}

      {relatedError && !relatedLoading && (
        <div className="h-40 flex items-center justify-center text-sm text-red-500">
          {relatedError ?? "유사 종목을 불러오지 못했습니다."}
        </div>
      )}

      {!relatedLoading && !relatedError && (
        <div className="flex flex-col min-h-[360px] max-h-[520px] overflow-y-auto rounded-lg border border-zinc-200 bg-white">
          {relatedStocks.map((row, idx) => {
            const colorClass = row.change > 0 ? "text-red-600" : row.change < 0 ? "text-blue-600" : "text-black";
            const fmtPrice = row.price.toLocaleString("ko-KR");
            const fmtVolume = row.volume.toLocaleString("ko-KR");
            const fmtChange = row.change > 0 ? `+${row.change.toLocaleString("ko-KR")}` : row.change.toLocaleString("ko-KR");
            const fmtRate = row.change > 0 ? `+${row.changeRate.toFixed(2)}%` : row.change < 0 ? `${row.changeRate.toFixed(2)}%` : "0.00%";

            return (
              <button
                key={row.stockCode}
                onClick={() => onSelectRelatedStock(row.stockCode)}
                className={`flex items-center gap-2 px-3 py-3 bg-white border-t ${
                  idx === relatedStocks.length - 1 ? "border-b" : ""
                } border-zinc-200 hover:bg-zinc-50 transition-colors cursor-pointer w-full text-left`}
              >
                <div className="min-w-0 w-[104px] flex-shrink-0">
                  <p className="text-sm font-semibold text-black truncate">{row.companyName}</p>
                  <p className="text-[10px] text-gray-400">{row.stockCode}</p>
                </div>
                <div className="flex flex-col items-end flex-1 min-w-0">
                  <span className={`text-sm font-semibold ${colorClass}`}>{fmtPrice}</span>
                  <span className="text-xs text-gray-500">{fmtVolume}</span>
                </div>
                <div className="flex-shrink-0 w-3 flex items-center justify-center">
                  {row.change > 0 && (
                    <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-b-[7px] border-transparent border-b-current`} />
                  )}
                  {row.change < 0 && (
                    <div className={`${colorClass} w-0 h-0 border-l-[5px] border-r-[5px] border-t-[7px] border-transparent border-t-current`} />
                  )}
                  {row.change === 0 && (
                    <span className="text-black text-lg font-extrabold leading-none">-</span>
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
            <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
              표시할 유사 종목이 없습니다.
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
        <div className="min-h-[360px] flex items-center justify-center text-sm text-gray-500 rounded-lg border border-zinc-200 bg-zinc-50">
          공매도 데이터가 없습니다.
        </div>
      );
    }

    const rows: { label: ReactNode; value: string }[] = [
      { label: "기준일", value: ss.reportDate },
      { label: "공매도 거래량", value: formatNumber(ss.shortVolumeTotal, 0) },
      { label: <><DictTerm term="거래량">거래량</DictTerm> (총)</>, value: formatNumber(ss.totalVolume, 0) },
      { label: "공매도 거래량 비율", value: `${ss.shortVolumeRatio.toFixed(2)}%` },
      { label: "공매도 거래대금", value: `${formatNumber(ss.shortAmountTotal, 0)}원` },
      { label: <><DictTerm term="거래대금">거래대금</DictTerm> (총)</>, value: `${formatNumber(ss.totalAmount, 0)}원` },
      { label: "공매도 거래대금 비율", value: `${ss.shortAmountRatio.toFixed(2)}%` },
    ];

    return (
      <div className="flex flex-col gap-3">
        <div className="flex flex-col rounded-lg border border-zinc-200 bg-white overflow-hidden">
          {rows.map((row, idx) => (
            <div
              key={`short-selling-row-${idx}`}
              className={`flex items-center justify-between gap-3 px-3 py-3 border-t ${
                idx === rows.length - 1 ? "border-b" : ""
              } border-zinc-200`}
            >
              <span className="text-xs sm:text-sm text-gray-600">{row.label}</span>
              <span className="text-xs sm:text-sm font-semibold text-black text-right">{row.value}</span>
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
    <section className="w-full flex-1 bg-surface rounded-2xl border border-line shadow-card px-4 py-4 flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h3 className="text-ink text-lg font-semibold">외부요인 패널</h3>
        <p className="text-xs text-ink-3 truncate">{stockName || "선택 종목"} 기준</p>
      </div>
      <div className="grid grid-cols-3 gap-1.5">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`h-9 rounded-md border text-xs font-semibold transition-colors ${
              activeTab === tab.key
                ? "border-zinc-900 bg-zinc-900 text-white"
                : "border-zinc-200 bg-zinc-50 text-zinc-600 hover:bg-zinc-100"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto min-h-[420px]">
        {content()}
      </div>
    </section>
  );
}

function PeerFlowSummaryCard({
  locked,
  peerStatus,
  peerDelta,
  anchorDelta,
  anchorVsPeer,
  peerCount,
}: {
  locked: boolean;
  peerStatus: string;
  peerDelta: number | null;
  anchorDelta: number | null;
  anchorVsPeer: number | null;
  peerCount: number;
}) {
  if (locked) {
    return (
      <div className="rounded-lg border border-dashed border-zinc-300 bg-zinc-50 px-3 py-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-zinc-800">유사종목 흐름</p>
            <p className="mt-1 text-xs text-zinc-500">분석 결과 보기 후 피어 평균 흐름이 활성화됩니다.</p>
          </div>
          <span className="inline-flex items-center gap-1 rounded-full border border-zinc-200 bg-white px-2.5 py-1 text-xs font-semibold text-zinc-500">
            <Lock size={13} aria-hidden="true" />
            잠금
          </span>
        </div>
      </div>
    );
  }

  const relativeText =
    anchorVsPeer == null
      ? "종목 대비 데이터 없음"
      : anchorVsPeer > 0.01
        ? `분석 종목이 피어 평균보다 ${formatSignedPct(anchorVsPeer)} 강함`
        : anchorVsPeer < -0.01
          ? `분석 종목이 피어 평균보다 ${formatSignedPct(Math.abs(anchorVsPeer))} 약함`
          : "분석 종목과 피어 평균 흐름이 유사";

  return (
    <div className="rounded-lg border border-zinc-200 bg-white px-3 py-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-zinc-900">유사종목 흐름</p>
          <p className="mt-1 text-xs text-zinc-500">{relativeText}</p>
        </div>
        <span className={`rounded-full border px-2.5 py-1 text-xs font-bold ${flowStatusClass(peerStatus)}`}>
          {peerStatus}
        </span>
      </div>
      <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <div className="rounded-md bg-zinc-50 px-2 py-2">
          <p className="text-zinc-500">피어 평균</p>
          <p className="mt-0.5 font-bold text-zinc-900">{formatSignedPct(peerDelta)}</p>
        </div>
        <div className="rounded-md bg-zinc-50 px-2 py-2">
          <p className="text-zinc-500">분석 종목</p>
          <p className="mt-0.5 font-bold text-zinc-900">{formatSignedPct(anchorDelta)}</p>
        </div>
        <div className="rounded-md bg-zinc-50 px-2 py-2">
          <p className="text-zinc-500">피어 수</p>
          <p className="mt-0.5 font-bold text-zinc-900">{peerCount}개</p>
        </div>
      </div>
    </div>
  );
}

function BondYieldList({
  title,
  items,
}: {
  title: string;
  items: NonNullable<Feature2MacroRates["bondYields"]>;
}) {
  return (
    <div className="rounded-lg border border-zinc-200 bg-white px-3 py-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-semibold text-zinc-900">{title}</p>
        <span className="text-[11px] text-zinc-400">{items.length}개</span>
      </div>
      <div className="mt-2 flex flex-col divide-y divide-zinc-100">
        {items.map((item) => (
          <div key={item.instrumentCode} className="flex items-center justify-between gap-3 py-2">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-zinc-800">{item.instrumentCode}</p>
              <p className="text-[11px] text-zinc-500">{item.instrumentName} · {formatDate(item.date)}</p>
            </div>
            <p className="text-sm font-bold text-zinc-900">{formatRate(item.value, item.unit)}</p>
          </div>
        ))}
        {items.length === 0 && (
          <p className="py-5 text-center text-sm text-zinc-500">데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}
