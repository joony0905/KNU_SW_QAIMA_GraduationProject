// src/api/portfolio.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { ApiResponse } from "../types/common/api";

export type PortfolioHoldingRequest = {
  stockCode: string;
  quantity: number;
  avgPrice: number;
};

export type PortfolioAnalyzeRequest = {
  holdings: PortfolioHoldingRequest[];
  options: string[];
  riskGamma: number;
};

export type PortfolioAnalyzeResponse = {
  riskLevel: "Low" | "Mid" | "High";
  volatility: number;
  diversification: "분산됨" | "집중됨" | "보통";
  covarianceScore: number;
  efficiency: "Efficient" | "Inefficient";
  gamma: number;
};

export const fetchPortfolioAnalysis = async (
  req: PortfolioAnalyzeRequest,
): Promise<PortfolioAnalyzeResponse> => {
  const res = await api.post<ApiResponse<PortfolioAnalyzeResponse>>(
    ENDPOINTS.portfolio.analyze(),
    req,
  );
  return res.data.data;
};
