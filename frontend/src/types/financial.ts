export interface FinancialDto {
  financialId: number;
  stockId: number;
  ticker: string;
  companyName: string;

  year: number;
  quarter: number | null;
  half: number | null;
  periodType: "A" | "Q" | "H" | "TTM";
  periodNo: number | null;
  reportDate: string;

  // 절대값 재무 지표
  revenue: number | null;
  grossProfit: number | null;
  operatingIncome: number | null;
  netIncome: number | null;
  assets: number | null;
  liabilities: number | null;
  equity: number | null;
  capitalStock: number | null;
  retainedEarnings: number | null;
  cashAndEquivalents: number | null;

  // 시장 / 주당 지표
  marketCap: number | null;
  eps: number | null;
  bps: number | null;

  // 비율 지표
  operatingMargin: number | null;
  netMargin: number | null;
  roe: number | null;
  roa: number | null;
  per: number | null;
  pbr: number | null;
  psr: number | null;
  debtRatio: number | null;
  currentRatio: number | null;
  quickRatio: number | null;
  interestCoverageRatio: number | null;
  freeCashFlow: number | null;
  revenueGrowth: number | null;
  epsGrowth: number | null;
}

export interface MarketSnapshotDto {
  asOfDate: string;
  marketCap: number | null;
  floatMarketCap: number | null;
  per: number | null;
  pbr: number | null;
  psr: number | null;
  floatRatio: number | null;
  treasuryRatio: number | null;
  sharesOutstanding: number | null;
  epsTtm: number | null;
  bps: number | null;
  sps: number | null;
  roe: number | null;
  roa: number | null;
  operatingMargin: number | null;
  netMargin: number | null;
  debtRatio: number | null;
  currentRatio: number | null;
  quickRatio: number | null;
  interestCoverageRatio: number | null;
  freeCashFlow: number | null;
  revenueGrowth: number | null;
  epsGrowth: number | null;
  source: string | null;
}
