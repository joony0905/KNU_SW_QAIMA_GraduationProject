import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";

export const mapAnalysisWireToCamel = (wire: AnalysisResponseWire): AnalysisResponse => {
  const metrics: any = (wire as any)?.metrics ?? {};
  const ohlcvSummary: any = metrics.ohlcvSummary ?? {};
  const financialSeries: any = metrics.financialSeries ?? {};
  const marketSnapshot: any = metrics.marketSnapshot ?? {};

  return {
    metrics: {
      stockCode: metrics.stockCode ?? "",
      asOf: metrics.asOf ?? "",
      schemaVersion: metrics.schemaVersion ?? "",
      ohlcvSummary: {
        count: Number(ohlcvSummary.count ?? 0),
        from: ohlcvSummary.from ?? null,
        to: ohlcvSummary.to ?? null,
        lastClose: ohlcvSummary.lastClose ?? null,
      },
      financialSeries: {
        years: Array.isArray(financialSeries.years) ? financialSeries.years : [],
        revenue: financialSeries.revenue ?? {},
        operatingIncome: financialSeries.operatingIncome ?? {},
        netIncome: financialSeries.netIncome ?? {},
      },
      marketSnapshot: {
        asOf: marketSnapshot.asOf ?? null,
        currency: marketSnapshot.currency ?? null,
        valuation: marketSnapshot.valuation ?? {},
        profitability: marketSnapshot.profitability ?? {},
        stability: marketSnapshot.stability ?? {},
        growth: marketSnapshot.growth ?? {},
        perShare: marketSnapshot.perShare ?? {},
      },
      indicators: (metrics as any).indicators,
      indicatorSummary: metrics.indicatorSummary ?? null,
    },
    warnings: Array.isArray((wire as any)?.warnings) ? (wire as any).warnings : [],
    explain: {
      text: (wire as any)?.explain?.text ?? null,
      sections: {
        priceFlow: (wire as any)?.explain?.sections?.priceFlow ?? null,
        marketSnapshot: (wire as any)?.explain?.sections?.marketSnapshot ?? null,
        indicators: (wire as any)?.explain?.sections?.indicators ?? null,
        financialTimeline: (wire as any)?.explain?.sections?.financialTimeline ?? null,
      },
      overall: (wire as any)?.explain?.overall ?? null,
    },
  };
};
