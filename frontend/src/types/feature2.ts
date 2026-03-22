export interface Feature2AnalyzeResponse {
  metrics: Record<string, unknown> | null;
  explain: string | null;
  meta: {
    warnings?: string[];
  } | null;
}
