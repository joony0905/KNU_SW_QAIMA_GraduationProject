// src/api/analysis.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { AnalysisResponse } from "../types/analysis";

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

export const fetchAnalysis = async (
  stockCode: string
): Promise<AnalysisResponse> => {
  // 최근 1년 데이터 기준
  const to = new Date().toISOString();
  const from = new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString();

  const res = await api.get<ApiResponse<AnalysisResponse>>(
    ENDPOINTS.analysis.getAnalysis(stockCode, "DAILY", from, to)
  );
  return res.data.data;
};
