export interface Feature2AnalyzeResponse {
  metrics:
    | {
        stock?: Record<string, unknown> | null;
        industry?: Record<string, unknown> | null;
        industryIndex?: { series?: Array<{ t: string; value: number }> } | null;
        peerCluster?: Record<string, unknown> | null;
        shortSelling?: Record<string, unknown> | null;
        baseRate?: Record<string, unknown> | null;
        newsList?: Array<Record<string, unknown>>;
      }
    | null;
  explain: string | null;
}
