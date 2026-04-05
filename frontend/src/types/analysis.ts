import type { Feat1AnalysisResult } from "./feature1/analysis";

export interface ExplainSection {
  title?: string | null;
  summary?: string | null;
  bullets?: string[] | null;
}

export interface ExplainOverall {
  summary?: string | null;
  bullets?: string[] | null;
  risks?: string[] | null;
  conclusion?: string | null;
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
    financialSeries: {
      years: number[];
      revenue: Record<string, number | null>;
      operatingIncome: Record<string, number | null>;
      netIncome: Record<string, number | null>;
    };
    marketSnapshot: {
      asOf?: string | null;
      currency?: string | null;
      valuation?: {
        per?: number | null;
        pbr?: number | null;
        psr?: number | null;
        marketCap?: number | null;
      } | null;
      profitability?: {
        roe?: number | null;
        roa?: number | null;
        operatingMargin?: number | null;
        netMargin?: number | null;
      } | null;
      stability?: {
        debtRatio?: number | null;
        currentRatio?: number | null;
        quickRatio?: number | null;
        interestCoverageRatio?: number | null;
      } | null;
      growth?: {
        revenueGrowth?: number | null;
        epsGrowth?: number | null;
        freeCashFlow?: number | null;
      } | null;
      perShare?: {
        eps?: number | null;
        bps?: number | null;
      } | null;
    };
    indicators: Feat1AnalysisResult["metrics"]["indicators"];
    indicatorSummary?: string | null;
  };
  warnings?: string[] | null;
  explain?: {
    text?: string | null;
    sections?: {
      priceFlow?: ExplainSection | null;
      marketSnapshot?: ExplainSection | null;
      indicators?: ExplainSection | null;
      financialTimeline?: ExplainSection | null;
    } | null;
    overall?: ExplainOverall | null;
  } | null;
}

export type AnalysisResponseWire = Feat1AnalysisResult;
