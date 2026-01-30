// src/api/stock.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { StockDto } from "../types/stock";

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}

export const getStockByCode = async (stockCode: string): Promise<StockDto> => {
  const res = await api.get<ApiResponse<StockDto>>(
    ENDPOINTS.stocks.getByCode(stockCode)
  );
  return res.data.data;
};
