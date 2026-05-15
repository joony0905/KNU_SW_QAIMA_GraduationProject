export type FeaturedStockTopic =
  | "GAINERS"            // 상승종목
  | "NEAR_UPPER_LIMIT"   // 상한가 임박종목
  | "LOSERS"             // 하락종목
  | "BROKE_UPPER_LIMIT"  // 상한가 이탈종목
  | "TOP_VOLUME"         // 거래량 상위종목
  | "TOP_TURNOVER"       // 거래대금 상위종목
  | "VOLUME_SURGE";      // 거래량 급등종목

export interface FeaturedStockDto {
  stockId: number;
  stockCode: string;
  companyName: string;
  exchangeCode: string;
  price: number;
  volume: number;
  change: number;
  changeRate: number;
}
