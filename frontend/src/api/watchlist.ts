import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { WatchlistItem } from "../types/watchlist";
import type { ApiResponse } from "../types/common/api";

// 로그인 사용자(JWT) 기준 기본 워치리스트. 백엔드가 목록을 자동 생성하므로
// 프론트에서 watchlistId 를 알 필요가 없다.
export const fetchWatchlist = async (): Promise<WatchlistItem[]> => {
  const res = await api.get<ApiResponse<WatchlistItem[]>>(
    ENDPOINTS.watchlist.getMyItems
  );
  return res.data.data;
};

export const addWatchlistItem = async (payload: {
  stockId: number;
}): Promise<WatchlistItem> => {
  const res = await api.post<ApiResponse<WatchlistItem>>(
    ENDPOINTS.watchlist.addMyItem,
    payload
  );
  return res.data.data;
};

export const deleteWatchlistItem = async (itemId: number): Promise<void> => {
  await api.delete(ENDPOINTS.watchlist.deleteItem(itemId));
};

export const updateWatchlistNote = async (
  itemId: number,
  note: string
): Promise<WatchlistItem> => {
  const res = await api.patch<ApiResponse<WatchlistItem>>(
    ENDPOINTS.watchlist.updateItem(itemId),
    { note }
  );
  return res.data.data;
};
