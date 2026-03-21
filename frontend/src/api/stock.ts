// src/api/stock.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { StockDto } from "../types/stock";

type ApiResponse<T> = {
  meta: any;
  data: T;
  errors: any[];
  success: boolean;
};

export const getStockByCode = async (stockCode: string): Promise<StockDto> => {
  const res = await api.get<ApiResponse<StockDto>>(
    ENDPOINTS.stocks.getByCode(stockCode)
  );
  return res.data.data;
};