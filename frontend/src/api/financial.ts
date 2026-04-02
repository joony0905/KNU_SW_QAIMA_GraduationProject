// src/api/financial.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { FinancialDto, MarketSnapshotDto } from "../types/financial";
import type { ApiResponse } from "../types/common/api";

/**
 * 최근 N년치 재무제표 조회
 */
export const fetchFinancials = async (
  ticker: string,
  years = 5,
  periodType?: string,
  periodNo?: number
): Promise<FinancialDto[]> => {
  const res = await api.get(
    ENDPOINTS.stocks.financials(ticker, years, periodType, periodNo)
  );
  const body = res.data;
  return Array.isArray(body) ? body : body.data;
};

/**
 * 특정 연도 재무제표 조회
 */
export const fetchFinancialsByYear = async (
  ticker: string,
  year: number,
  periodType?: string,
  periodNo?: number
): Promise<FinancialDto[]> => {
  const res = await api.get(
    ENDPOINTS.stocks.financialsByYear(ticker, year, periodType, periodNo)
  );
  const body = res.data;
  return Array.isArray(body) ? body : body.data;
};

/**
 * 시장 스냅샷 조회 (실시간 PER/PBR/시가총액)
 */
export const fetchMarketSnapshot = async (
  stockCode: string,
  asOfDate?: string
): Promise<MarketSnapshotDto> => {
  const res = await api.get<ApiResponse<MarketSnapshotDto>>(
    ENDPOINTS.stocks.marketSnapshot(stockCode, asOfDate)
  );
  return res.data.data;
};
