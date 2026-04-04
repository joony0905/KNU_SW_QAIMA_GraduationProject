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
        { title: "유통비율", subtitle: "Float Ratio", value: fmtPercent(snapshot.floatRatio) },
        { title: "자사주비율", subtitle: "Treasury Ratio", value: fmtPercent(snapshot.treasuryRatio) },
        { title: "상장주식수", subtitle: "Shares Outstanding", value: fmtNumber(snapshot.sharesOutstanding) },
        { title: "기준일", subtitle: "As of Date", value: snapshot.asOfDate ?? "-" },
      ],
    },
  ];
}

export function buildSectionsFromDto(
  dto: FinancialDto,
  snapshot?: MarketSnapshotDto | null,
): IndicatorSection[] {
  const per = snapshot?.per ?? dto.per;
  const pbr = snapshot?.pbr ?? dto.pbr;
  const marketCap = snapshot?.marketCap ?? dto.marketCap;

  return [
    {
      sectionTitle: "밸류에이션",
      rows: [
        { title: "PER", subtitle: "주가수익비율", value: fmtTimes(per) },
        { title: "PBR", subtitle: "주가순자산비율", value: fmtTimes(pbr) },
        { title: "PSR", subtitle: "주가매출비율", value: fmtTimes(dto.psr) },
        { title: "Market Cap", subtitle: "시가총액", value: fmtWon(marketCap) },
        { title: "EPS (TTM)", subtitle: "주당순이익", value: fmtNumber(dto.eps) },
        { title: "BPS", subtitle: "주당순자산", value: fmtNumber(dto.bps) },
      ],
    },
    {
      sectionTitle: "수익성",
      rows: [
        { title: "ROE", subtitle: "자기자본이익률", value: fmtPercent(dto.roe) },
        { title: "ROA", subtitle: "총자산이익률", value: fmtPercent(dto.roa) },
        { title: "Operating Margin", subtitle: "영업이익률", value: fmtPercent(dto.operatingMargin) },
        { title: "Net Margin", subtitle: "순이익률", value: fmtPercent(dto.netMargin) },
      ],
    },
    {
      sectionTitle: "재무안정성",
      rows: [
        { title: "Debt Ratio", subtitle: "부채비율", value: fmtPercent(dto.debtRatio) },
        { title: "Current Ratio", subtitle: "유동비율", value: fmtPercent(dto.currentRatio) },
        { title: "Quick Ratio", subtitle: "당좌비율", value: fmtPercent(dto.quickRatio) },
        { title: "Free Cash Flow", subtitle: "잉여현금흐름", value: "-" },
        { title: "Interest Coverage Ratio", subtitle: "이자보상비율", value: fmtTimes(dto.interestCoverageRatio) },
      ],
    },
    {
      sectionTitle: "성장성",
      rows: [
        { title: "Revenue Growth", subtitle: "매출 성장률", value: "-" },
        { title: "EPS Growth", subtitle: "EPS 성장률", value: "-" },
      ],
    },
  ];
}
