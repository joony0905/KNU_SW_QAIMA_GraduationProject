import api from "./apiClient";
import type { Feature2AnalyzeResponse } from "../types/feature2";

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

export const fetchFeature2Analysis = async (
  stockCode: string,
): Promise<Feature2AnalyzeResponse> => {
  const res = await api.post<ApiResponse<Feature2AnalyzeResponse>>(
    "/feature2/analyze",
    { stockCode },
  );
  return res.data.data;
};
