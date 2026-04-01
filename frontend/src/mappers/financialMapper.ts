import type { FinancialDto } from "../types/financial";
import type { IndicatorSection } from "../mocks/financialIndicators";

const fmtPercent = (value: number | null) =>
  value == null ? "-" : `${value.toFixed(1)}%`;

const fmtTimes = (value: number | null) =>
  value == null ? "-" : `${value.toFixed(1)}배`;

export function buildSectionsFromDto(dto: FinancialDto): IndicatorSection[] {
  console.log("[buildSectionsFromDto] called with:", dto);
  return [
    {
      sectionTitle: "수익성",
      rows: [
        {
          title: "EPS",
          subtitle: "주당순이익",
          value: "-",
        },
        {
          title: "ROE",
          subtitle: "자기자본이익률",
          value: fmtPercent(dto.roe),
        },
        // ROA 부분을 이렇게 수정:
        {
          title: "ROA",
          subtitle: "총자산이익률",
          value:
            dto.netIncome && dto.assets && dto.assets > 0
              ? fmtPercent((Number(dto.netIncome) / Number(dto.assets)) * 100)
              : "-",
        },
        {
          title: "Operating Margin",
          subtitle: "영업이익률",
          value: fmtPercent(dto.operatingMargin),
        },
        {
          title: "Net Margin",
          subtitle: "순이익률",
          value: fmtPercent(dto.netMargin),
        },
      ],
    },
    {
      sectionTitle: "가치(밸류에이션)",
      rows: [
        { title: "PER", subtitle: "주가수익비율", value: fmtTimes(dto.per) },
        { title: "PBR", subtitle: "주가순자산비율", value: fmtTimes(dto.pbr) },
        {
          title: "PSR",
          subtitle: "주가매출비율",
          value:
            dto.marketCap && dto.revenue && dto.revenue > 0
              ? fmtTimes(Number(dto.marketCap) / Number(dto.revenue))
              : "-",
        },
        // BPS는 DTO에 없으니 일단 자리만
        { title: "BPS", subtitle: "주당순자산가치", value: "-" },
      ],
    },
    {
      sectionTitle: "재무안정성",
      rows: [
        {
          title: "Debt Ratio",
          subtitle: "부채비율",
          value: fmtPercent(dto.debtRatio),
        },
        {
          title: "Interest Coverage Ratio",
          subtitle: "이자보상비율",
          value: "-", // DTO에 없으므로 나중에 계산 or 추가
        },
      ],
    },
    {
      sectionTitle: "유동성",
      rows: [
        {
          title: "Current Ratio",
          subtitle: "유동비율",
          value: "-",
        },
        {
          title: "Quick Ratio",
          subtitle: "당좌비율",
          value: "-",
        },
      ],
    },
  ];
}
