// src/types/stock.ts

export interface StockDto {
  stockId?: number | null;
  stockCode: string;
  companyName: string;
  exchangeCode?: string | null;
  symbol?: string | null;
  currency?: string | null;
  price?: number | null;
  changeRate?: number | null;
}
