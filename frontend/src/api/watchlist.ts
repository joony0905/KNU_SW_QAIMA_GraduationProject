import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { WatchlistItem } from "../types/watchlist";
import type { ApiResponse } from "../types/common/api";

// 백엔드에 워치리스트 생성/목록 조회 엔드포인트가 없고 컨트롤러가 userId=1 로
// 고정돼 있어, 조회·추가에 필요한 watchlistId 를 기본값으로 고정한다.
// 추후 백엔드가 "내 워치리스트" 조회를 제공하면 그 값으로 대체하면 된다.
// .env 의 VITE_DEFAULT_WATCHLIST_ID 로 오버라이드 가능.
export const DEFAULT_WATCHLIST_ID = Number(
  import.meta.env.VITE_DEFAULT_WATCHLIST_ID ?? 1,
);

export const fetchWatchlist = async (
  watchlistId: number
): Promise<WatchlistItem[]> => {
  const res = await api.get<ApiResponse<WatchlistItem[]>>(
    ENDPOINTS.watchlist.getItems(watchlistId)
  );
  return res.data.data;
};

export const addWatchlistItem = async (payload: {
  stockId: number;
  watchlistId: number;
}): Promise<WatchlistItem> => {
  const res = await api.post<ApiResponse<WatchlistItem>>(
    ENDPOINTS.watchlist.addItem,
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
