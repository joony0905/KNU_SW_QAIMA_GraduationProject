import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";

export const mapAnalysisWireToCamel = (wire: AnalysisResponseWire): AnalysisResponse => {
  return {
    metrics: {
      stockCode: wire.metrics.stock_code,
      asOf: wire.metrics.as_of,
      schemaVersion: wire.metrics.schema_version,
      ohlcvSummary: {
        count: wire.metrics.ohlcv_summary.count,
        from: wire.metrics.ohlcv_summary.from,
        to: wire.metrics.ohlcv_summary.to,
        lastClose: wire.metrics.ohlcv_summary.last_close,
      },
      financialSummary: {
        years: wire.metrics.financial_summary.years,
        revenue: wire.metrics.financial_summary.revenue,
        operatingIncome: wire.metrics.financial_summary.operating_income,
        netIncome: wire.metrics.financial_summary.net_income,
      },
      indicators: wire.metrics.indicators,
      indicatorSummary: wire.metrics.indicator_summary,
    },
    explain: wire.explain ?? null,
    meta: wire.meta ?? null,
  };
};
