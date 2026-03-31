export interface NewsListItem {
  newsId: number;
  title: string;
  url: string;
  publisher: string;
  publishedAt: string;
  summary?: string | null;
  sentimentScore?: number | null;
}

export interface Feature2AnalyzeResponse {
  metrics:
    | {
        stock?: Record<string, unknown> | null;
        industry?: Record<string, unknown> | null;
        industryIndex?: { series?: Array<{ t: string; value: number }> } | null;
        peerCluster?: Record<string, unknown> | null;
        shortSelling?: Record<string, unknown> | null;
        baseRate?: Record<string, unknown> | null;
        newsList?: NewsListItem[];
      }
    | null;
  explain: string | null;
}
