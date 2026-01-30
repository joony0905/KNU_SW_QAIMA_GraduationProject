// src/types/watchlist.ts
export interface WatchlistItem {
  watchlistItemId: number;
  stockId: number | null;
  stockName: string | null;
  note: string | null;
  industryName: string | null;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}
