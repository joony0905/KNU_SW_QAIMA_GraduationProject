// src/api/analysis.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { AnalysisResponse, AnalysisResponseWire } from "../types/analysis";
import type { ApiResponse } from "../types/common/api";
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

export const fetchAnalysis = async (
  req: FeatOneAnalyzeRequest
): Promise<ApiResponse<AnalysisResponse>> => {
  // Spring public contract: snake_case request payload.
  // Frontend internal naming stays camelCase for minimal impact.
  const payload = {
    stock_code: req.stockCode,
    freq: req.freq,
    from: req.from,
    to: req.to,
    market_div_code: req.marketDivCode,
    include_explain: req.includeExplain,
  };

  const res = await api.post<ApiResponse<AnalysisResponseWire>>(
    ENDPOINTS.analysis.analyze(),
    payload
  );
  // removed transitional envelope compatibility layer
  // frontend now consumes official meta/data/errors contract directly
  return {
    ...res.data,
    data: mapAnalysisWireToCamel(res.data.data),
  };
};
