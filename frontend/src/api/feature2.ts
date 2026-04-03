import api from "./apiClient";
import type { Feature2AnalyzeResponse } from "../types/feature2";
import type { ApiResponse } from "../types/common/api";
import type { NewsItemDto } from "../types/news";

export const fetchFeature2Analysis = async (
  stockCode: string,
  freq?: string,
  window?: number,
): Promise<ApiResponse<Feature2AnalyzeResponse>> => {
  const body: Record<string, unknown> = { stockCode };
  if (freq) body.freq = freq;
  if (window) body.window = window;
  const res = await api.post<ApiResponse<Feature2AnalyzeResponse>>(
    "/feature2/analyze",
    body,
  );
  return res.data;
};

export const fetchFeature2NewsList = async (
  stockCode: string,
): Promise<ApiResponse<NewsItemDto[]>> => {
  const res = await api.get<ApiResponse<NewsItemDto[]>>("/feature2/news", {
    params: { stockCode },
  });
  return res.data;
};
