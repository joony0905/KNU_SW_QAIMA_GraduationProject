export interface AnalysisBase<TMetrics> {
  metrics: TMetrics;
  explain?: string | null;
  warnings?: string[];
  meta?: Record<string, any>;
}
