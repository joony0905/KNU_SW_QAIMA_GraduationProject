import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { NewsItemDto } from "../types/news";
import type { ApiResponse } from "../types/common/api";

export const fetchNewsByStock = async (
  stockCode: string,
  limit = 15,
): Promise<NewsItemDto[]> => {
  const res = await api.get<ApiResponse<NewsItemDto[]>>(
    ENDPOINTS.news.listByStock(stockCode, limit),
  );
  return res.data.data;
};
