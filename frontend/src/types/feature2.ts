import type { NewsItemDto } from "./news";

// --- 종목 메타 ---
export interface StockMeta {
  stockCode: string;
  companyName: string;
  exchangeCode: string;
  countryCode: string;
  currency: string;
  price: number;
  changeRate: number;
  source: string;
  sectorCode: string;
  sectorName: string;
  industryCode: string;
  industryName: string;
  kospi200: boolean;
}

// --- 산업 메타 ---
export interface IndustryMeta {
  industryId: number;
  name: string;
  code: string;
}

// --- 산업지수 차트 ---
export interface RelativePoint {
  t: string;
  value: number;
}

export interface IndustryIndexBlock {
  indexId: number;
  name: string;
  code: string;
  currency: string;
  series: RelativePoint[];
}

// --- 피어 클러스터 (상관계수 + 유사종목) ---
export type PeerRelation = "LEADER" | "FOLLOWER" | "COINCIDENT" | "UNKNOWN";

export interface BandPoint {
  t: string;
  p20: number;
  p80: number;
}

export interface PeerItem {
  stockCode: string;
  companyName: string;
  avgTurnover: number;
  avgVolume: number;
  corr: number;
  bestLag: number;
  leadLagCorr: number;
  relation: PeerRelation;
  score: number;
}

export interface PeerCluster {
  method: string;
  industryId: number;
  freq: string;
  window: number;
  peerCount: number;
  anchorStockCode: string;
  centroid: RelativePoint[];
  band: BandPoint[];
  peers: PeerItem[];
  asOf: string;
}

// --- 공매도 ---
export interface ShortSellingMetrics {
  shortSellingId: number;
  stockId: number;
  stockCode: string;
  companyName: string;
  reportDate: string;
  marketCode: string;
  securityType: string;
  shortVolumeTotal: number;
  shortVolumeUptickApplied: number;
  shortVolumeUptickExempt: number;
  totalVolume: number;
  shortVolumeRatio: number;
  shortAmountTotal: number;
  shortAmountUptickApplied: number;
  shortAmountUptickExempt: number;
  totalAmount: number;
  shortAmountRatio: number;
  source: string;
  sourceScreenId: string;
}

// --- 금리 ---
export interface BaseRateMetrics {
  date: string;
  value: number;
  unit: string;
}

// --- Feature2 전체 메트릭스 ---
export interface Feature2Metrics {
  stock: StockMeta | null;
  industry: IndustryMeta | null;
  industryIndex: IndustryIndexBlock | null;
  peerCluster: PeerCluster | null;
  shortSelling: ShortSellingMetrics | null;
  baseRate: BaseRateMetrics | null;
  newsList: NewsItemDto[];
}

// --- Feature2 분석 응답 ---
export interface Feature2AnalyzeResponse {
  metrics: Feature2Metrics | null;
  explain: string | null;
}
