// src/types/watchlist.ts
export interface WatchlistItem {
  watchlistItemId: number;
  stockId: number | null;
  stockName: string | null;
  note: string | null;
  industryName: string | null;
}

export interface ApiResponse<T> {
  meta: { status: string; warning?: string | null; warnings?: string[] } | null;
  data: T;
  errors: Array<{ code: string; message: string }>;
}
