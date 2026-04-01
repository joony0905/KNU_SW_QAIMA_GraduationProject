import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { NewsItemDto } from "../types/news";

export const fetchNewsByStock = async (
  stockCode: string,
  limit = 10,
): Promise<NewsItemDto[]> => {
  const res = await api.get(
    ENDPOINTS.news.listByStock(stockCode, limit),
  );
  const body = res.data;
  return Array.isArray(body) ? body : body.data;
};
