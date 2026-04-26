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
  companyName?: string | null;
  avgTurnover?: number | null;
  avgVolume?: number | null;
  corr?: number | null;
  adjustedCorr?: number | null;
  corrStability?: number | null;
  rawCorrValid?: boolean | null;
  adjustedCorrValid?: boolean | null;
  adjustedReturnSampleSize?: number | null;
  adjustedReturnCoverageRatio?: number | null;
  adjustmentBasis?: "RAW_ONLY" | "SIMPLE_SUBTRACTION" | "FALLBACK_RAW" | null;
  displayStatus?:
    | "SELECTED"
    | "ELIGIBLE_NOT_SELECTED"
    | "DISPLAY_ONLY"
    | "LOW_CORR"
    | "RAW_ONLY"
    | "ADJUSTED_ONLY"
    | "FALLBACK_RAW"
    | null;
  bestLag?: number | null;
  leadLagCorr?: number | null;
  lagConfidence?: number | null;
  relation: PeerRelation;
  liquiditySimilarityScore?: number | null;
  volatilitySimilarityScore?: number | null;
  score?: number | null;
  peerScore?: number | null;
}

export interface PeerCluster {
  method: string;
  industryId: number;
  freq: string;
  window: number;
  peerCount: number;
  requestedPeerCount?: number | null;
  effectivePeerCount?: number | null;
  rawCandidateCount?: number | null;
  evaluatedCandidateCount?: number | null;
  eligibleCandidateCount?: number | null;
  selectedPeerCount?: number | null;
  displayedCandidateCount?: number | null;
  displayLimit?: number | null;
  adjustmentMethod?: "SIMPLE_SUBTRACTION" | "BETA_RESIDUAL_RESERVED" | null;
  industryIndexCode?: string | null;
  industryIndexName?: string | null;
  adjustedReturnSampleSize?: number | null;
  adjustedReturnCoverageRatio?: number | null;
  adjustmentValid?: boolean | null;
  adjustmentFallbackReason?: string | null;
  anchorStockCode: string;
  anchorSeries?: RelativePoint[];
  industryIndexSeries?: RelativePoint[];
  centroid: RelativePoint[];
  band: BandPoint[];
  peerCentroid?: RelativePoint[];
  peerBand?: BandPoint[];
  peers: PeerItem[];
  candidates?: PeerItem[];
  asOf: string;
  interpretationNote?: string | null;
}

export interface RelatedStockCard {
  stockCode: string;
  companyName: string;
  price: number | null;
  changeAmount: number | null;
  changeRate: number | null;
  volume: number | null;
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

export interface BaseRateSeriesPoint {
  date: string;
  value: number;
  unit: string;
}

export interface ShortSellingSeriesPoint {
  reportDate: string;
  shortVolumeRatio: number | null;
  shortAmountRatio: number | null;
  shortVolumeTotal: number | null;
  totalVolume: number | null;
  shortAmountTotal: number | null;
  totalAmount: number | null;
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
