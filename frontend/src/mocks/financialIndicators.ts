export type IndicatorCell = {
  title: string;
  subtitle: string;
  value: string;
};

export type IndicatorSection = {
  sectionTitle: string;
  rows: IndicatorCell[];
};

export const profitabilitySection: IndicatorSection = {
  sectionTitle: "수익성",
  rows: [
    { title: "EPS", subtitle: "주당순이익", value: "12.5%" },
    { title: "ROE", subtitle: "자기자본이익률", value: "12.5%" },
    { title: "ROA", subtitle: "총자산이익률", value: "12.5%" },
    { title: "Operating Margin", subtitle: "영업이익률", value: "12.5%" },
    { title: "Net Margin", subtitle: "순이익률", value: "12.5%" },
  ],
};

export const valuationSection: IndicatorSection = {
  sectionTitle: "가치(밸류에이션)",
  rows: [
    { title: "PER", subtitle: "주가수익비율", value: "20배" },
    { title: "PBR", subtitle: "주가순자산비율", value: "2배" },
    { title: "PSR", subtitle: "주가매출비율", value: "2배" },
    { title: "BPS", subtitle: "주당순자산가치", value: "12.5%" },
  ],
};

export const stabilitySection: IndicatorSection = {
  sectionTitle: "재무안정성",
  rows: [
    { title: "Debt Ratio", subtitle: "부채비율", value: "35%" },
    {
      title: "Interest Coverage Ratio",
      subtitle: "이자보상비율",
      value: "12.5%",
    },
  ],
};

export const liquiditySection: IndicatorSection = {
  sectionTitle: "유동성",
  rows: [
    { title: "Current Ratio", subtitle: "유동비율", value: "12.5%" },
    { title: "Quick Ratio", subtitle: "당좌비율", value: "12.5%" },
  ],
};
