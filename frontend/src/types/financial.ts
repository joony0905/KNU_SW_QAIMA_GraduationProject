export interface FinancialDto {
  financialId: number;
  stockId: number;
  ticker: string;
  companyName: string;

  year: number;
  quarter: number | null;
  periodType: "A" | "Q" | "TTM";
  reportDate: string; // LocalDate → 문자열로 온다고 가정

  // 규모 지표 (원 단위) - 숫자 or 문자열, 팀 규칙에 맞춰 선택
  revenue: number | null;
  operatingIncome: number | null;
  netIncome: number | null;
  assets: number | null;
  liabilities: number | null;
  equity: number | null;
  capitalStock: number | null;
  marketCap: number | null;

  // 비율 지표 (%)
  operatingMargin: number | null;
  netMargin: number | null;
  roe: number | null;
  per: number | null;
  pbr: number | null;
  debtRatio: number | null;
  roa: number | null;
  eps: number | null;
  bps: number | null;
  psr: number | null;
  currentRatio: number | null;
  quickRatio: number | null;
  interestCoverageRatio: number | null;
}
