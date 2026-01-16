import type { Candle } from "./candle";

export interface CandleSeriesResponse {
  stockCode: string;
  freq: string;
  source: string;
  timezone: string;
  data: Candle[];
}
