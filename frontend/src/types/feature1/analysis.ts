import type { AnalysisBase } from "../common/analysis";
import type { IndicatorBundle } from "../indicator";

export interface OhlcvSummary {
  count: number;
  from?: string | null;
  to?: string | null;
  last_close?: number | null;
}

export interface FinancialSummary {
  years: number[];
  revenue: Record<string, number | null>;
  operating_income: Record<string, number | null>;
  net_income: Record<string, number | null>;
}

export interface Feat1Metrics {
  stock_code: string;
  as_of: string;
  schema_version: string;
  ohlcv_summary: OhlcvSummary;
  financial_summary: FinancialSummary;
  indicators: IndicatorBundle;
  [key: string]: unknown;
}

export type Feat1AnalysisResult = AnalysisBase<Feat1Metrics>;