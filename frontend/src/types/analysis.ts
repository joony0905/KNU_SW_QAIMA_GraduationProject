// DEPRECATED: Feature1-specific analysis types moved to src/types/feature1/analysis.ts and common contracts under src/types/common.
// Keep this file for backward compatibility with existing imports.
// src/types/analysis.ts
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

export interface IndicatorsSlot {
  valuation?: unknown;
  growth?: unknown;
  profitability?: unknown;
}

export interface AnalysisMetrics {
  stock_code: string;
  as_of: string;
  ohlcv_summary: OhlcvSummary;
  financial_summary: FinancialSummary;
  indicators: IndicatorsSlot;
  schema_version: string;
}

export interface AnalysisExplain {
  text: string;
}

export interface AnalysisMeta {
  warnings: string[];
}

export interface AnalysisResponse {
  metrics: AnalysisMetrics;
  explain?: AnalysisExplain | null;
  meta?: AnalysisMeta | null;
}
