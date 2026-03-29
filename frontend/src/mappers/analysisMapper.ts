import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";

export const mapAnalysisWireToCamel = (wire: AnalysisResponseWire): AnalysisResponse => {
  const metrics: any = (wire as any)?.metrics ?? {};
  const ohlcvSummary: any = metrics.ohlcvSummary ?? {};
  const financialSummary: any = metrics.financialSummary ?? {};

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
      financialSummary: {
        years: Array.isArray(financialSummary.years) ? financialSummary.years : [],
        revenue: financialSummary.revenue ?? {},
        operatingIncome: financialSummary.operatingIncome ?? {},
        netIncome: financialSummary.netIncome ?? {},
      },
      indicators: (metrics as any).indicators,
      indicatorSummary: metrics.indicatorSummary ?? null,
    },
    explain: (wire as any)?.explain ?? null,
  };
};
