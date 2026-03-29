// src/types/stock.ts

// stock API contract now follows global snake_case + ApiResponse envelope
export interface StockDto {
  stock_id?: number | null;
  stock_code: string;
  company_name: string;
  exchange_code?: string | null;
  symbol?: string | null;
  currency?: string | null;
  price?: number | null;
  change_rate?: number | null;
}
