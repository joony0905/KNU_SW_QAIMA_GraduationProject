import type {
  BaseRateSeriesPoint,
  Feature2InvestorFlow,
  Feature2MacroRates,
  Feature2MacroRatesSeries,
  NewsSentimentSummary,
  PeerCluster,
  ShortSellingSeriesPoint,
  StockMeta,
} from "./feature2";
import type { IndicatorBundle } from "./indicator";
import type { NewsItemDto } from "./news";

export interface AnalysisExplainSection {
  title?: string | null;
  summary?: string | null;
  bullets?: string[] | null;
}

export interface AnalysisPanelResult {
  explain?: {
    provider?: string | null;
    model?: string | null;
    text?: string | null;
    sections?: {
      priceFlow?: AnalysisExplainSection | null;
      marketSnapshot?: AnalysisExplainSection | null;
      indicators?: AnalysisExplainSection | null;
      financialTimeline?: AnalysisExplainSection | null;
      peerCluster?: AnalysisExplainSection | null;
      macroEnvironment?: AnalysisExplainSection | null;
      investorFlow?: AnalysisExplainSection | null;
      crossSignal?: AnalysisExplainSection | null;
      newsSentiment?: AnalysisExplainSection | null;
      trendSummary?: AnalysisExplainSection | null;
      baseRate?: AnalysisExplainSection | null;
      shortSelling?: AnalysisExplainSection | null;
    } | null;
    overall?: {
      summary?: string | null;
      bullets?: string[] | null;
      risks?: string[] | null;
      conclusion?: string | null;
    } | null;
  } | null;

  warnings?: string[] | null;
  meta?: {
    warnings?: string[] | null;
  } | null;

  metrics?: {
    stock?: StockMeta | null;
    ohlcvSummary?: {
      count: number;
      from?: string;
      to?: string;
      lastClose?: number;
    };
    indicatorSummary?: string;
    financialSeries?: {
      years: number[];
      revenue: Record<string, number>;
      operatingIncome: Record<string, number>;
      netIncome: Record<string, number>;
    };
    marketSnapshot?: {
      asOf?: string;
      currency?: string;
      valuation?: {
        per?: number;
        pbr?: number;
        psr?: number;
        marketCap?: number;
      };
      profitability?: {
        roe?: number;
        roa?: number;
        operatingMargin?: number;
        netMargin?: number;
      };
      stability?: {
        debtRatio?: number;
        currentRatio?: number;
        quickRatio?: number;
        interestCoverageRatio?: number;
      };
      growth?: {
        revenueGrowth?: number;
        epsGrowth?: number;
        freeCashFlow?: number;
      };
      perShare?: {
        eps?: number;
        bps?: number;
      };
      };
    indicators?: IndicatorBundle;
    shortSelling?: {
      stockCode: string;
      companyName: string;
      reportDate: string;
      shortVolumeTotal: number;
      totalVolume: number;
      shortVolumeRatio: number;
      shortAmountTotal: number;
      totalAmount: number;
      shortAmountRatio: number;
    } | null;
    baseRate?: {
      date: string;
      value: number;
      unit: string;
    } | null;
    baseRateTrendSummary?: import("./feature2").BaseRateTrendSummary | null;
    shortSellingTrendSummary?: import("./feature2").ShortSellingTrendSummary | null;
    baseRateSeries?: BaseRateSeriesPoint[] | null;
    macroRates?: Feature2MacroRates | null;
    macroRatesSeries?: Feature2MacroRatesSeries | null;
    investorFlow?: Feature2InvestorFlow | null;
    shortSellingSeries?: ShortSellingSeriesPoint[] | null;
    peerCluster?: PeerCluster | null;
    newsSentimentSummary?: NewsSentimentSummary | null;
    newsList?: NewsItemDto[] | null;
  } | null;
  summary?: string;
  highlights?: string[];
  risks?: string[];
  rating?: string;
}

export interface FinancialTimelineDatum {
  label: string;
  revenue: number | null;
  operatingIncome: number | null;
  netIncome: number | null;
  operatingMargin: number | null;
}

export interface FinancialTimelineSection {
  period: "A" | "H" | "Q";
  points: FinancialTimelineDatum[];
}

export interface PriceFlowSummary {
  from?: string | null;
  to?: string | null;
  startClose: number | null;
  endClose: number | null;
  returnPct: number | null;
  high: number | null;
  low: number | null;
  avgVolume: number | null;
}
