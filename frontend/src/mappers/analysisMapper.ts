import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";

export const mapAnalysisWireToCamel = (wire: AnalysisResponseWire): AnalysisResponse => {
  const metrics: any = (wire as any)?.metrics ?? {};
  const ohlcvSummary: any = metrics.ohlcv_summary ?? {};
  const financialSummary: any = metrics.financial_summary ?? {};

  return {
    metrics: {
      stockCode: metrics.stock_code ?? "",
      asOf: metrics.as_of ?? "",
      schemaVersion: metrics.schema_version ?? "",
      ohlcvSummary: {
        count: Number(ohlcvSummary.count ?? 0),
        from: ohlcvSummary.from ?? null,
        to: ohlcvSummary.to ?? null,
        lastClose: ohlcvSummary.last_close ?? null,
      },
      financialSummary: {
        years: Array.isArray(financialSummary.years) ? financialSummary.years : [],
        revenue: financialSummary.revenue ?? {},
        operatingIncome: financialSummary.operating_income ?? {},
        netIncome: financialSummary.net_income ?? {},
      },
      indicators: (metrics as any).indicators,
      indicatorSummary: metrics.indicator_summary ?? null,
    },
    explain: (wire as any)?.explain ?? null,
    meta: null,
  };
};
