// src/api/analysis.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";
import { mapAnalysisWireToCamel } from "../mappers/analysisMapper";

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
  const res = await api.post<ApiResponse<AnalysisResponseWire>>(
    ENDPOINTS.analysis.analyze(),
    req
  );
  return mapAnalysisWireToCamel(res.data.data);
};
