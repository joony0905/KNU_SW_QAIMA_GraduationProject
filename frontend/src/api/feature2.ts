import api from "./apiClient";
import type { Feature2AnalyzeResponse } from "../types/feature2";
import type { ApiResponse } from "../types/common/api";
import type { NewsListItem } from "../types/feature2";

export const fetchFeature2Analysis = async (
  stockCode: string,
): Promise<ApiResponse<Feature2AnalyzeResponse>> => {
  const res = await api.post<ApiResponse<Feature2AnalyzeResponse>>(
    "/feature2/analyze",
    { stockCode },
  );
  return res.data;
};

export const fetchFeature2NewsList = async (
  stockCode: string,
): Promise<ApiResponse<NewsListItem[]>> => {
  const res = await api.get<ApiResponse<NewsListItem[]>>("/feature2/news", {
    params: { stockCode },
  });
  return res.data;
};
