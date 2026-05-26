import type { FinancialDto, MarketSnapshotDto } from "../types/financial";
import type { IndicatorSection } from "../mocks/financialIndicators";

const fmtPercent = (value: number | null | undefined) =>
  value == null ? "-" : `${value.toFixed(2)}%`;

const isEnglish = (language?: string | null) => (language ?? "").toLowerCase().startsWith("en");

const fmtTimes = (value: number | null | undefined, language?: string | null) =>
  value == null ? "-" : `${value.toFixed(2)}${isEnglish(language) ? "x" : "배"}`;

const fmtWon = (value: number | null | undefined, language?: string | null) => {
  if (value == null) return "-";
  if (isEnglish(language)) {
    if (Math.abs(value) >= 1e12) return `${(value / 1e12).toFixed(2)}T KRW`;
    if (Math.abs(value) >= 1e9) return `${(value / 1e9).toFixed(2)}B KRW`;
    if (Math.abs(value) >= 1e6) return `${(value / 1e6).toFixed(2)}M KRW`;
    return `${value.toLocaleString("en-US")} KRW`;
  }
  if (Math.abs(value) >= 1e12) return `${(value / 1e12).toFixed(2)}조`;
  if (Math.abs(value) >= 1e8) return `${(value / 1e8).toFixed(0)}억`;
  return value.toLocaleString("ko-KR");
};

const fmtNumber = (value: number | null | undefined, language?: string | null) =>
  value == null ? "-" : value.toLocaleString(isEnglish(language) ? "en-US" : "ko-KR");

export function buildSnapshotSections(
  snapshot: MarketSnapshotDto,
  language?: string | null,
): IndicatorSection[] {
  const en = isEnglish(language);
  return [
    {
      sectionTitle: en ? "Market Overview" : "시장 개요",
      rows: [
        { title: en ? "Market Cap" : "시가총액", subtitle: "Market Cap", value: fmtWon(snapshot.marketCap, language) },
        { title: en ? "Float Market Cap" : "유동 시가총액", subtitle: "Float Market Cap", value: fmtWon(snapshot.floatMarketCap, language) },
        { title: "PER", subtitle: en ? "Price Earnings Ratio" : "주가수익비율", value: fmtTimes(snapshot.per, language) },
        { title: "PBR", subtitle: en ? "Price Book Ratio" : "주가순자산비율", value: fmtTimes(snapshot.pbr, language) },
        { title: "PSR", subtitle: en ? "Price Sales Ratio" : "주가매출비율", value: fmtTimes(snapshot.psr, language) },
        { title: "EPS (TTM)", subtitle: en ? "Earnings Per Share" : "주당순이익", value: fmtNumber(snapshot.epsTtm, language) },
        { title: "BPS", subtitle: en ? "Book Value Per Share" : "주당순자산", value: fmtNumber(snapshot.bps, language) },
        { title: en ? "Float Ratio" : "유통비율", subtitle: "Float Ratio", value: fmtPercent(snapshot.floatRatio) },
        { title: en ? "Treasury Ratio" : "자사주비율", subtitle: "Treasury Ratio", value: fmtPercent(snapshot.treasuryRatio) },
        { title: en ? "Shares Outstanding" : "상장주식수", subtitle: "Shares Outstanding", value: fmtNumber(snapshot.sharesOutstanding, language) },
        { title: en ? "As of Date" : "기준일", subtitle: "As of Date", value: snapshot.asOfDate ?? "-" },
      ],
    },
    {
      sectionTitle: en ? "Profitability" : "수익성",
      rows: [
        { title: "ROE", subtitle: en ? "Return on Equity" : "자기자본이익률", value: fmtPercent(snapshot.roe) },
        { title: "ROA", subtitle: en ? "Return on Assets" : "총자산이익률", value: fmtPercent(snapshot.roa) },
        { title: "Operating Margin", subtitle: en ? "Operating Margin" : "영업이익률", value: fmtPercent(snapshot.operatingMargin) },
        { title: "Net Margin", subtitle: en ? "Net Margin" : "순이익률", value: fmtPercent(snapshot.netMargin) },
      ],
    },
    {
      sectionTitle: en ? "Financial Stability" : "재무안정성",
      rows: [
        { title: "Debt Ratio", subtitle: en ? "Debt Ratio" : "부채비율", value: fmtPercent(snapshot.debtRatio) },
        { title: "Current Ratio", subtitle: en ? "Current Ratio" : "유동비율", value: fmtPercent(snapshot.currentRatio) },
        { title: "Quick Ratio", subtitle: en ? "Quick Ratio" : "당좌비율", value: fmtPercent(snapshot.quickRatio) },
        { title: "Free Cash Flow", subtitle: en ? "Free Cash Flow" : "잉여현금흐름", value: fmtWon(snapshot.freeCashFlow, language) },
        { title: "Interest Coverage Ratio", subtitle: en ? "Interest Coverage Ratio" : "이자보상비율", value: fmtTimes(snapshot.interestCoverageRatio, language) },
      ],
    },
    {
      sectionTitle: en ? "Growth" : "성장성",
      rows: [
        { title: "Revenue Growth", subtitle: en ? "Revenue Growth" : "매출 성장률", value: fmtPercent(snapshot.revenueGrowth) },
        { title: "EPS Growth", subtitle: en ? "EPS Growth" : "EPS 성장률", value: fmtPercent(snapshot.epsGrowth) },
      ],
    },
  ];
}

export function buildSectionsFromDto(
  dto: FinancialDto | null,
  snapshot?: MarketSnapshotDto | null,
  language?: string | null,
): IndicatorSection[] {
  const en = isEnglish(language);
  const base = dto ?? null;
  const per = snapshot?.per ?? base?.per;
  const pbr = snapshot?.pbr ?? base?.pbr;
  const psr = snapshot?.psr ?? base?.psr;
  const marketCap = snapshot?.marketCap ?? base?.marketCap;
  const eps = snapshot?.epsTtm ?? base?.eps;
  const bps = snapshot?.bps ?? base?.bps;
  const roe = snapshot?.roe ?? base?.roe;
  const roa = snapshot?.roa ?? base?.roa;
  const operatingMargin = snapshot?.operatingMargin ?? base?.operatingMargin;
  const netMargin = snapshot?.netMargin ?? base?.netMargin;
  const debtRatio = snapshot?.debtRatio ?? base?.debtRatio;
  const currentRatio = snapshot?.currentRatio ?? base?.currentRatio;
  const quickRatio = snapshot?.quickRatio ?? base?.quickRatio;
  const interestCoverageRatio = snapshot?.interestCoverageRatio ?? base?.interestCoverageRatio;
  const freeCashFlow = snapshot?.freeCashFlow ?? base?.freeCashFlow;
  const revenueGrowth = snapshot?.revenueGrowth ?? base?.revenueGrowth;
  const epsGrowth = snapshot?.epsGrowth ?? base?.epsGrowth;

  return [
    {
      sectionTitle: en ? "Valuation" : "밸류에이션",
      rows: [
        { title: "PER", subtitle: en ? "Price Earnings Ratio" : "주가수익비율", value: fmtTimes(per, language) },
        { title: "PBR", subtitle: en ? "Price Book Ratio" : "주가순자산비율", value: fmtTimes(pbr, language) },
        { title: "PSR", subtitle: en ? "Price Sales Ratio" : "주가매출비율", value: fmtTimes(psr, language) },
        { title: "Market Cap", subtitle: en ? "Market Cap" : "시가총액", value: fmtWon(marketCap, language) },
        { title: "EPS (TTM)", subtitle: en ? "Earnings Per Share" : "주당순이익", value: fmtNumber(eps, language) },
        { title: "BPS", subtitle: en ? "Book Value Per Share" : "주당순자산", value: fmtNumber(bps, language) },
      ],
    },
    {
      sectionTitle: en ? "Profitability" : "수익성",
      rows: [
        { title: "ROE", subtitle: en ? "Return on Equity" : "자기자본이익률", value: fmtPercent(roe) },
        { title: "ROA", subtitle: en ? "Return on Assets" : "총자산이익률", value: fmtPercent(roa) },
        { title: "Operating Margin", subtitle: en ? "Operating Margin" : "영업이익률", value: fmtPercent(operatingMargin) },
        { title: "Net Margin", subtitle: en ? "Net Margin" : "순이익률", value: fmtPercent(netMargin) },
      ],
    },
    {
      sectionTitle: en ? "Financial Stability" : "재무안정성",
      rows: [
        { title: "Debt Ratio", subtitle: en ? "Debt Ratio" : "부채비율", value: fmtPercent(debtRatio) },
        { title: "Current Ratio", subtitle: en ? "Current Ratio" : "유동비율", value: fmtPercent(currentRatio) },
        { title: "Quick Ratio", subtitle: en ? "Quick Ratio" : "당좌비율", value: fmtPercent(quickRatio) },
        { title: "Free Cash Flow", subtitle: en ? "Free Cash Flow" : "잉여현금흐름", value: fmtWon(freeCashFlow, language) },
        { title: "Interest Coverage Ratio", subtitle: en ? "Interest Coverage Ratio" : "이자보상비율", value: fmtTimes(interestCoverageRatio, language) },
      ],
    },
    {
      sectionTitle: en ? "Growth" : "성장성",
      rows: [
        { title: "Revenue Growth", subtitle: en ? "Revenue Growth" : "매출 성장률", value: fmtPercent(revenueGrowth) },
        { title: "EPS Growth", subtitle: en ? "EPS Growth" : "EPS 성장률", value: fmtPercent(epsGrowth) },
      ],
    },
  ];
}
