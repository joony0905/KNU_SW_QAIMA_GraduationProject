import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";

export const mapAnalysisWireToCamel = (wire: AnalysisResponseWire): AnalysisResponse => {
  const metrics: any = (wire as any)?.metrics ?? {};
  const ohlcvSummary: any = metrics.ohlcvSummary ?? metrics.ohlcv_summary ?? {};
  const financialSummary: any = metrics.financialSummary ?? metrics.financial_summary ?? {};

  return {
    metrics: {
      stockCode: metrics.stockCode ?? metrics.stock_code ?? "",
      asOf: metrics.asOf ?? metrics.as_of ?? "",
      schemaVersion: metrics.schemaVersion ?? metrics.schema_version ?? "",
      ohlcvSummary: {
        count: Number(ohlcvSummary.count ?? 0),
        from: ohlcvSummary.from ?? null,
        to: ohlcvSummary.to ?? null,
        lastClose: ohlcvSummary.lastClose ?? ohlcvSummary.last_close ?? null,
      },
      financialSummary: {
        years: Array.isArray(financialSummary.years) ? financialSummary.years : [],
        revenue: financialSummary.revenue ?? {},
        operatingIncome: financialSummary.operatingIncome ?? financialSummary.operating_income ?? {},
        netIncome: financialSummary.netIncome ?? financialSummary.net_income ?? {},
      },
      indicators: (metrics as any).indicators,
      indicatorSummary: metrics.indicatorSummary ?? metrics.indicator_summary ?? null,
    },
    explain: (wire as any)?.explain ?? null,
  };
};
