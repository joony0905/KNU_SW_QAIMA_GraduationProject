import type { AnalysisBase } from "../common/analysis";
import type { IndicatorBundle } from "../indicator";

export interface OhlcvSummary {
  count: number;
  from?: string | null;
  to?: string | null;
  lastClose?: number | null;
}

export interface FinancialSummary {
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
  financialSummary: FinancialSummary;
  indicators: IndicatorBundle;
  indicatorSummary?: string | null;
  [key: string]: unknown;
}

export type Feat1AnalysisResult = AnalysisBase<Feat1Metrics>;
