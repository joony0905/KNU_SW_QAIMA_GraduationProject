import api from "./apiClient";
import type { Feature2AnalyzeResponse, NewsItem } from "../types/feature2";
import type { ApiResponse } from "../types/common/api";

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
): Promise<ApiResponse<NewsItem[]>> => {
  const res = await api.get<ApiResponse<NewsItem[]>>("/feature2/news", {
    params: { stockCode },
  });
  return res.data;
};
