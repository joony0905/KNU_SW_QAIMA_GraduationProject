import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { CandleSeriesResponse } from "../types/charts";

interface ApiResponse<T> {
  meta: {
    status: string;
    warning: string | null;
  };
  data: T;
  errors: Array<{ code: string; message: string }>;
}

export const fetchCandles = async (
  stockCode: string,
  freq: string,
  from: string,
  to: string
): Promise<CandleSeriesResponse> => {
  const res = await api.get<ApiResponse<CandleSeriesResponse>>(
    ENDPOINTS.charts.candles(stockCode, freq, from, to)
  );
  return res.data.data;
};

// oldest 이전 캔들 limit개 가져오기
export const fetchCandlesBefore = async (
  stockCode: string,
  freq: string,
  to: string,
  limit: number
): Promise<CandleSeriesResponse> => {
  const res = await api.get<ApiResponse<CandleSeriesResponse>>(
    ENDPOINTS.charts.candlesBefore(stockCode, freq, to, limit)
  );
  return res.data.data;
};