// src/types/watchlist.ts
// 백엔드 WatchlistResponseDto 기준 (camelCase, snake_case 금지)
// 응답 봉투(ApiResponse)는 프로젝트 공용 정의(types/common/api)를 사용한다.
export interface WatchlistItem {
  watchlistItemId: number;
  stockId: number | null;
  stockName: string | null;
  note: string | null;
  industryName: string | null;
}
