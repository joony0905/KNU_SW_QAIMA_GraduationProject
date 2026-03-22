export interface AnalysisPanelResult {
  explain?: {
    parsed?: {
      summary?: string[];
      risks?: string[];
      conclusion?: string;
    } | null;
    text?: string | null;
  } | null;

  metrics?: {
    ohlcvSummary?: {
      count: number;
      from?: string;
      to?: string;
      lastClose?: number;
    };
    indicatorSummary?: string;
    financialSummary?: {
      years: number[];
      revenue: Record<string, number>;
      operatingIncome: Record<string, number>;
      netIncome: Record<string, number>;
    };
  } | null;

  meta?: {
    warnings?: string[];
  } | null;

  summary?: string;
  highlights?: string[];
  risks?: string[];
  rating?: string;
}
