// src/api/analysis.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { AnalysisResponse } from "../types/analysis";

export type Freq =
  | "ONE_MIN"
  | "FIVE_MIN"
  | "FIFTEEN_MIN"
  | "ONE_H"
  | "ONE_D"
  | "ONE_W"
  | "ONE_M";

export type FeatOneAnalyzeRequest = {
  stockCode: string;
  freq: Freq;
  from: string;
  to: string;
  marketDivCode: string;
  includeExplain: boolean;
};

interface ApiResponse<T> {
  meta: {
    status: string;
    warning: string | null;
  };
  data: T;
  errors: Array<{ code: string; message: string }>;
}

export const fetchAnalysis = async (
  req: FeatOneAnalyzeRequest
): Promise<AnalysisResponse> => {
  const res = await api.post<ApiResponse<AnalysisResponse>>(
    ENDPOINTS.analysis.analyze(),
    req
  );
  return res.data.data;
};