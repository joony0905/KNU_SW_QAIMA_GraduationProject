import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { WatchlistItem, ApiResponse } from "../types/watchlist";

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
