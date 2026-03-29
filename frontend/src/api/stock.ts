// src/api/stock.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { StockDto } from "../types/stock";
import type { ApiResponse } from "../types/common/api";

export const getStockByCode = async (stockCode: string): Promise<StockDto> => {
  const res = await api.get<ApiResponse<StockDto>>(
    ENDPOINTS.stocks.getByCode(stockCode),
  );
  return res.data.data;
};

export const searchStocks = async (query: string): Promise<StockDto[]> => {
  const res = await api.get<ApiResponse<StockDto[]>>(
    ENDPOINTS.stocks.search(query),
  );
  return res.data.data;
};
