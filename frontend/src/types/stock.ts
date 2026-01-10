// src/types/stock.ts
export interface StockDto {
  stockId: number;
  stockCode: string;
  isin: string | null;
  companyName: string;
  exchangeId: number;
  exchangeCode: string;
  assetType: string;
  currency: string;
  industryId: number | null;
  price: number | null;
  changeRate: number | null;
  listedAt: string | null; // "2024-01-01"
  delistedAt: string | null;
}
