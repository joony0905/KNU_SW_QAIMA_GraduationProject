// src/api/financial.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { FinancialDto } from "../types/financial";

// ApiResponse 래퍼 타입 (로그인/워치리스트와 동일)
interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

/**
 * 최근 N년치 재무제표 조회
 */
export const fetchFinancials = async (
  ticker: string,
  years = 5
): Promise<FinancialDto[]> => {
  const res = await api.get<ApiResponse<FinancialDto[]>>(
    ENDPOINTS.stocks.financials(ticker, years)
  );
  return res.data.data;
};

/**
 * 특정 연도 재무제표 조회
 */
export const fetchFinancialsByYear = async (
  ticker: string,
  year: number
): Promise<FinancialDto[]> => {
  const res = await api.get<ApiResponse<FinancialDto[]>>(
    ENDPOINTS.stocks.financialsByYear(ticker, year)
  );
  return res.data.data;
};
