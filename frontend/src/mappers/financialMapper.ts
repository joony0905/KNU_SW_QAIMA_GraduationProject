import type { FinancialDto, MarketSnapshotDto } from "../types/financial";
import type { IndicatorSection } from "../mocks/financialIndicators";

const fmtPercent = (value: number | null | undefined) =>
  value == null ? "-" : `${value.toFixed(2)}%`;

const fmtTimes = (value: number | null | undefined) =>
  value == null ? "-" : `${value.toFixed(2)}배`;

const fmtWon = (value: number | null | undefined) => {
  if (value == null) return "-";
  if (Math.abs(value) >= 1e12) return `${(value / 1e12).toFixed(2)}조`;
  if (Math.abs(value) >= 1e8) return `${(value / 1e8).toFixed(0)}억`;
  return value.toLocaleString("ko-KR");
};

const fmtNumber = (value: number | null | undefined) =>
  value == null ? "-" : value.toLocaleString("ko-KR");

export function buildSnapshotSections(
  snapshot: MarketSnapshotDto,
): IndicatorSection[] {
  return [
    {
      sectionTitle: "시장 개요",
      rows: [
        { title: "시가총액", subtitle: "Market Cap", value: fmtWon(snapshot.marketCap) },
        { title: "유동 시가총액", subtitle: "Float Market Cap", value: fmtWon(snapshot.floatMarketCap) },
        { title: "PER", subtitle: "주가수익비율", value: fmtTimes(snapshot.per) },
        { title: "PBR", subtitle: "주가순자산비율", value: fmtTimes(snapshot.pbr) },
        { title: "PSR", subtitle: "주가매출비율", value: fmtTimes(snapshot.psr) },
        { title: "EPS (TTM)", subtitle: "주당순이익", value: fmtNumber(snapshot.epsTtm) },
        { title: "BPS", subtitle: "주당순자산", value: fmtNumber(snapshot.bps) },
        { title: "유통비율", subtitle: "Float Ratio", value: fmtPercent(snapshot.floatRatio) },
        { title: "자사주비율", subtitle: "Treasury Ratio", value: fmtPercent(snapshot.treasuryRatio) },
        { title: "상장주식수", subtitle: "Shares Outstanding", value: fmtNumber(snapshot.sharesOutstanding) },
        { title: "기준일", subtitle: "As of Date", value: snapshot.asOfDate ?? "-" },
      ],
    },
    {
      sectionTitle: "수익성",
      rows: [
        { title: "ROE", subtitle: "자기자본이익률", value: fmtPercent(snapshot.roe) },
        { title: "ROA", subtitle: "총자산이익률", value: fmtPercent(snapshot.roa) },
        { title: "Operating Margin", subtitle: "영업이익률", value: fmtPercent(snapshot.operatingMargin) },
        { title: "Net Margin", subtitle: "순이익률", value: fmtPercent(snapshot.netMargin) },
      ],
    },
    {
      sectionTitle: "재무안정성",
      rows: [
        { title: "Debt Ratio", subtitle: "부채비율", value: fmtPercent(snapshot.debtRatio) },
        { title: "Current Ratio", subtitle: "유동비율", value: fmtPercent(snapshot.currentRatio) },
        { title: "Quick Ratio", subtitle: "당좌비율", value: fmtPercent(snapshot.quickRatio) },
        { title: "Free Cash Flow", subtitle: "잉여현금흐름", value: fmtWon(snapshot.freeCashFlow) },
        { title: "Interest Coverage Ratio", subtitle: "이자보상비율", value: fmtTimes(snapshot.interestCoverageRatio) },
      ],
    },
    {
      sectionTitle: "성장성",
      rows: [
        { title: "Revenue Growth", subtitle: "매출 성장률", value: fmtPercent(snapshot.revenueGrowth) },
        { title: "EPS Growth", subtitle: "EPS 성장률", value: fmtPercent(snapshot.epsGrowth) },
      ],
    },
  ];
}

export function buildSectionsFromDto(
  dto: FinancialDto | null,
  snapshot?: MarketSnapshotDto | null,
): IndicatorSection[] {
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
      sectionTitle: "밸류에이션",
      rows: [
        { title: "PER", subtitle: "주가수익비율", value: fmtTimes(per) },
        { title: "PBR", subtitle: "주가순자산비율", value: fmtTimes(pbr) },
        { title: "PSR", subtitle: "주가매출비율", value: fmtTimes(psr) },
        { title: "Market Cap", subtitle: "시가총액", value: fmtWon(marketCap) },
        { title: "EPS (TTM)", subtitle: "주당순이익", value: fmtNumber(eps) },
        { title: "BPS", subtitle: "주당순자산", value: fmtNumber(bps) },
      ],
    },
    {
      sectionTitle: "수익성",
      rows: [
        { title: "ROE", subtitle: "자기자본이익률", value: fmtPercent(roe) },
        { title: "ROA", subtitle: "총자산이익률", value: fmtPercent(roa) },
        { title: "Operating Margin", subtitle: "영업이익률", value: fmtPercent(operatingMargin) },
        { title: "Net Margin", subtitle: "순이익률", value: fmtPercent(netMargin) },
      ],
    },
    {
      sectionTitle: "재무안정성",
      rows: [
        { title: "Debt Ratio", subtitle: "부채비율", value: fmtPercent(debtRatio) },
        { title: "Current Ratio", subtitle: "유동비율", value: fmtPercent(currentRatio) },
        { title: "Quick Ratio", subtitle: "당좌비율", value: fmtPercent(quickRatio) },
        { title: "Free Cash Flow", subtitle: "잉여현금흐름", value: fmtWon(freeCashFlow) },
        { title: "Interest Coverage Ratio", subtitle: "이자보상비율", value: fmtTimes(interestCoverageRatio) },
      ],
    },
    {
      sectionTitle: "성장성",
      rows: [
        { title: "Revenue Growth", subtitle: "매출 성장률", value: fmtPercent(revenueGrowth) },
        { title: "EPS Growth", subtitle: "EPS 성장률", value: fmtPercent(epsGrowth) },
      ],
    },
  ];
}
