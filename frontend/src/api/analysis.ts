// src/api/analysis.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";
import { mapAnalysisWireToCamel } from "../mappers/analysisMapper";
import type { InvestLevel } from "../utils/investLevel";

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
  llmVendor?: string;
  investLevel?: InvestLevel;
};

export const fetchAnalysis = async (
  req: FeatOneAnalyzeRequest
): Promise<ApiResponse<AnalysisResponse>> => {
  const res = await api.post<ApiResponse<AnalysisResponseWire>>(
    ENDPOINTS.analysis.analyze(),
    {
      stockCode: req.stockCode,
      freq: req.freq,
      from: req.from,
      to: req.to,
      marketDivCode: req.marketDivCode,
      includeExplain: req.includeExplain,
      llmVendor: req.llmVendor,
      investLevel: req.investLevel,
    }
  );

  return {
    ...res.data,
    data: mapAnalysisWireToCamel(res.data.data),
  };
};
