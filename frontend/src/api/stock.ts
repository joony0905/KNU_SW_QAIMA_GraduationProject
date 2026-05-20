// src/api/stock.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { StockDto } from "../types/stock";
import type { ApiResponse } from "../types/common/api";

const normalizeStock = (raw: any): StockDto => ({
  stockId: raw?.stock_id ?? raw?.stockId ?? 0,
  stockCode: raw?.stock_code ?? raw?.stockCode ?? "",
  companyName: raw?.company_name ?? raw?.companyName ?? "",
  exchangeCode: raw?.exchange_code ?? raw?.exchangeCode ?? "",
  currency: raw?.currency ?? "",
  price: raw?.price ?? null,
  changeRate: raw?.change_rate ?? raw?.changeRate ?? null,
});

export const getStockByCode = async (stockCode: string): Promise<StockDto> => {
  const res = await api.get<ApiResponse<StockDto>>(
    ENDPOINTS.stocks.getByCode(stockCode),
  );
  return normalizeStock((res.data as any)?.data);
};

export const searchStocks = async (query: string): Promise<StockDto[]> => {
  const res = await api.get<ApiResponse<StockDto[]>>(
    ENDPOINTS.stocks.search(query),
  );
  return res.data.data;
};
