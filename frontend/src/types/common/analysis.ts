export interface AnalysisBase<TMetrics> {
  metrics: TMetrics;
  explain?: any | null;
  meta?: { warnings?: string[]; [k: string]: any } | null;
}