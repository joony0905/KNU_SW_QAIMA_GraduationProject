// src/api/financial.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { FinancialDto } from "../types/financial";

// ApiResponse 래퍼 타입 (로그인/워치리스트와 동일)
interface ApiResponse<T> {
  meta: { status: string; warning?: string | null; warnings?: string[] } | null;
  data: T;
  errors: Array<{ code: string; message: string }>;
}

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
