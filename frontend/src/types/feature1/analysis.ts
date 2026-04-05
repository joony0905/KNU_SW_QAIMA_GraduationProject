import type { AnalysisBase } from "../common/analysis";
import type { IndicatorBundle } from "../indicator";

export interface OhlcvSummary {
  count: number;
  from?: string | null;
  to?: string | null;
  lastClose?: number | null;
}

export interface MarketSnapshotSectionValuation {
  per?: number | null;
  pbr?: number | null;
  psr?: number | null;
  marketCap?: number | null;
}

export interface MarketSnapshotSectionProfitability {
  roe?: number | null;
  roa?: number | null;
  operatingMargin?: number | null;
  netMargin?: number | null;
}

export interface MarketSnapshotSectionStability {
  debtRatio?: number | null;
  currentRatio?: number | null;
  quickRatio?: number | null;
  interestCoverageRatio?: number | null;
}

export interface MarketSnapshotSectionGrowth {
  revenueGrowth?: number | null;
  epsGrowth?: number | null;
  freeCashFlow?: number | null;
}

export interface MarketSnapshotSectionPerShare {
  eps?: number | null;
  bps?: number | null;
}

export interface Feature1MarketSnapshot {
  asOf?: string | null;
  currency?: string | null;
  valuation?: MarketSnapshotSectionValuation | null;
  profitability?: MarketSnapshotSectionProfitability | null;
  stability?: MarketSnapshotSectionStability | null;
  growth?: MarketSnapshotSectionGrowth | null;
  perShare?: MarketSnapshotSectionPerShare | null;
}

export interface FinancialSeries {
  years: number[];
  revenue: Record<string, number | null>;
  operatingIncome: Record<string, number | null>;
  netIncome: Record<string, number | null>;
}

export interface Feat1Metrics {
  stockCode: string;
  asOf: string;
  schemaVersion: string;
  ohlcvSummary: OhlcvSummary;
  financialSeries: FinancialSeries;
  marketSnapshot: Feature1MarketSnapshot;
  indicators: IndicatorBundle;
  indicatorSummary?: string | null;
  [key: string]: unknown;
}

export type Feat1AnalysisResult = AnalysisBase<Feat1Metrics>;
