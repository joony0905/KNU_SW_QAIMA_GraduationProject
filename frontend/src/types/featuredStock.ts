export type FeaturedStockTopic =
  | "GAINERS"            // 상승종목
  | "NEAR_NEW_HIGH"      // 신고가 근접 종목
  | "NEAR_NEW_LOW"       // 신저가 근접 종목
  | "LOSERS"             // 하락종목
  | "TOP_TURNOVER"       // 거래대금 상위종목
  | "VOLUME_SURGE";      // 거래량 급등종목

export interface FeaturedStockDto {
  stockId: number | null;
  stockCode: string;
  companyName: string;
  exchangeCode: string;
  price: number;
  volume: number;
  change: number;
  changeRate: number;
}
