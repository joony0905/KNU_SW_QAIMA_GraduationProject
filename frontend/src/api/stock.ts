// src/api/stock.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { StockDto } from "../types/stock";

type ApiResponse<T> = {
  meta: any;
  data: T;
  errors: any[];
};

const normalizeStock = (raw: any): StockDto => ({
  stockId: raw?.stock_id ?? raw?.stockId ?? 0,
  stockCode: raw?.stock_code ?? raw?.stockCode ?? "",
  isin: raw?.isin ?? null,
  companyName: raw?.company_name ?? raw?.companyName ?? "",
  exchangeId: raw?.exchange_id ?? raw?.exchangeId ?? 0,
  exchangeCode: raw?.exchange_code ?? raw?.exchangeCode ?? "",
  assetType: raw?.asset_type ?? raw?.assetType ?? "",
  currency: raw?.currency ?? "",
  industryId: raw?.industry_id ?? raw?.industryId ?? null,
  price: raw?.price ?? null,
  changeRate: raw?.change_rate ?? raw?.changeRate ?? null,
  listedAt: raw?.listed_at ?? raw?.listedAt ?? null,
  delistedAt: raw?.delisted_at ?? raw?.delistedAt ?? null,
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
  const rows = Array.isArray((res.data as any)?.data) ? (res.data as any).data : [];
  return rows.map(normalizeStock);
};
