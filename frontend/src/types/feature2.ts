export interface Feature2AnalyzeResponse {
  metrics:
    | {
        stock?: Record<string, unknown> | null;
        industry?: Record<string, unknown> | null;
        industry_index?: { series?: Array<{ t: string; value: number }> } | null;
        peer_cluster?: Record<string, unknown> | null;
        short_selling?: Record<string, unknown> | null;
        base_rate?: Record<string, unknown> | null;
        news_list?: Array<Record<string, unknown>>;
      }
    | null;
  explain: string | null;
  meta: {
    warnings?: string[];
  } | null;
}
