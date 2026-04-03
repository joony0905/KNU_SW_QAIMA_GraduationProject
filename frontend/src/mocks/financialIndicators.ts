export type IndicatorCell = {
  title: string;
  subtitle: string;
  value: string;
};

export type IndicatorSection = {
  sectionTitle: string;
  rows: IndicatorCell[];
};
