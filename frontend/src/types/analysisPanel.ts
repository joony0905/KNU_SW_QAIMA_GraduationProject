export interface AnalysisPanelResult {
  explain?: {
    text?: string | null;
    sections?: {
      priceFlow?: {
        title?: string | null;
        summary?: string | null;
        bullets?: string[] | null;
      } | null;
      marketSnapshot?: {
        title?: string | null;
        summary?: string | null;
        bullets?: string[] | null;
      } | null;
      indicators?: {
        title?: string | null;
        summary?: string | null;
        bullets?: string[] | null;
      } | null;
      financialTimeline?: {
        title?: string | null;
        summary?: string | null;
        bullets?: string[] | null;
      } | null;
    } | null;
    overall?: {
      summary?: string | null;
      bullets?: string[] | null;
      risks?: string[] | null;
      conclusion?: string | null;
    } | null;
  } | null;

  warnings?: string[] | null;

  metrics?: {
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
