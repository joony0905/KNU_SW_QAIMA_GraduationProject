import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { ApiResponse } from "../types/common/api";
import type { FeaturedStockDto, FeaturedStockTopic } from "../types/featuredStock";

export const fetchFeaturedStocks = async (
  topic: FeaturedStockTopic,
  limit = 10,
): Promise<FeaturedStockDto[]> => {
  const res = await api.get<ApiResponse<FeaturedStockDto[]>>(
    ENDPOINTS.featuredStocks.getByTopic(topic, limit),
  );
  return res.data.data;
};
