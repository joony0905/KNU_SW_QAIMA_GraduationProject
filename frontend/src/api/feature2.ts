import api from "./apiClient";
import type { Feature2AnalyzeResponse } from "../types/feature2";
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
