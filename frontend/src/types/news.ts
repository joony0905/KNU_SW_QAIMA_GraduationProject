export interface NewsItemDto {
  newsId: number;
  title: string;
  url: string;
  publisher: string;
  publishedAt: string;
  summary: string;
  sentimentScore: number | null;
}
