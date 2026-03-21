import type { Feat1AnalysisResult } from "./feature1/analysis";

export interface ParsedExplainText {
  stock_code?: string;
  summary?: string[];
  risks?: string[];
  conclusion?: string;
}

export interface AnalysisResponse {
  metrics: {
    stockCode: string;
    asOf: string;
    schemaVersion: string;
    ohlcvSummary: {
      count: number;
      from?: string | null;
      to?: string | null;
      lastClose?: number | null;
    };
    financialSummary: {
      years: number[];
      revenue: Record<string, number | null>;
      operatingIncome: Record<string, number | null>;
      netIncome: Record<string, number | null>;
    };
    indicators: Feat1AnalysisResult["metrics"]["indicators"];
    indicatorSummary?: string | null;
  };
  explain?: {
    text?: string;
  } | null;
  meta?: {
    warnings?: string[];
  } | null;
}

export type AnalysisResponseWire = Feat1AnalysisResult;
