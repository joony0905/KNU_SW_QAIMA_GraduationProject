import type { NewsItemDto } from "./news";
import type { AnalysisExplainSection } from "./analysisPanel";

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
  peerCoverage?: RelativePoint[];
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

export interface Feature2MacroRatePoint {
  date: string;
  value: number;
  unit: string;
  source: string;
}

export interface Feature2ExchangeRatePoint {
  date: string;
  pairCode: string;
  baseCurrency: string;
  quoteCurrency: string;
  value: number;
  unit: string;
  source: string;
}

export interface Feature2BondYieldPoint {
  date: string;
  countryCode: string;
  instrumentCode: string;
  instrumentName: string;
  maturityMonths: number | null;
  value: number;
  unit: string;
  source: string;
}

export interface Feature2MacroRates {
  krBaseRate: Feature2MacroRatePoint | null;
  usFedFundsRate: Feature2MacroRatePoint | null;
  usdKrw: Feature2ExchangeRatePoint | null;
  bondYields: Feature2BondYieldPoint[];
}

export interface Feature2TrendPoint {
  date: string;
  value: number;
}

export interface Feature2TrendSeries {
  key: string;
  label: string;
  group: string;
  unit: string;
  points: Feature2TrendPoint[];
}

export interface Feature2MacroRatesSeries {
  series: Feature2TrendSeries[];
}

export type TrendDirection = "UP" | "DOWN" | "FLAT" | "UNKNOWN";

export interface BaseRateTrendSummary {
  window: number;
  pointCount: number;
  startDate: string | null;
  endDate: string | null;
  startValue: number | null;
  endValue: number | null;
  change: number | null;
  direction: TrendDirection;
  unit: string | null;
}

export interface ShortSellingTrendSummary {
  window: number;
  pointCount: number;
  startDate: string | null;
  endDate: string | null;
  startShortVolumeRatio: number | null;
  endShortVolumeRatio: number | null;
  shortVolumeRatioChange: number | null;
  avgShortVolumeRatio: number | null;
  maxShortVolumeRatio: number | null;
  startShortAmountRatio: number | null;
  endShortAmountRatio: number | null;
  shortAmountRatioChange: number | null;
  avgShortAmountRatio: number | null;
  maxShortAmountRatio: number | null;
  direction: TrendDirection;
}

export type NewsSentimentLabel = "positive" | "neutral" | "negative";

export interface RecentNewsSentiment {
  newsId: number;
  title: string;
  publisher: string | null;
  publishedAt: string | null;
  sentimentScore: number | null;
  sentimentLabel: NewsSentimentLabel;
}

export interface NewsSentimentSummary {
  summaryDate: string | null;
  dailyAvgScore: number | null;
  dailyNewsCount: number;
  scoredNewsCount: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  strongestPositiveScore: number | null;
  strongestNegativeScore: number | null;
  recentItems: RecentNewsSentiment[];
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

export type InvestorFlowDirection =
  | "BOTH_NET_BUY"
  | "BOTH_NET_SELL"
  | "FOREIGN_BUY_INSTITUTION_SELL"
  | "FOREIGN_SELL_INSTITUTION_BUY"
  | "MIXED_OR_FLAT";

export interface InvestorFlowSummary {
  window: number;
  pointCount: number;
  startDate: string | null;
  endDate: string | null;
  foreignNetBuyValueMillionSum: number | null;
  institutionNetBuyValueMillionSum: number | null;
  combinedNetBuyValueMillionSum: number | null;
  foreignNetBuyQtySum: number | null;
  institutionNetBuyQtySum: number | null;
  combinedNetBuyQtySum: number | null;
  direction: InvestorFlowDirection | string | null;
}

export interface InvestorFlowPoint {
  tradeDate: string;
  closePrice?: number | null;
  foreignNetBuyQty: number | null;
  foreignNetBuyValueMillion: number | null;
  institutionNetBuyQty: number | null;
  institutionNetBuyValueMillion: number | null;
  individualNetBuyQty: number | null;
  individualNetBuyValueMillion: number | null;
}

export interface Feature2InvestorFlow {
  stockCode: string;
  marketCode: string | null;
  stockSummary: InvestorFlowSummary | null;
  marketSummary: InvestorFlowSummary | null;
  stockSeries: InvestorFlowPoint[];
  marketSeries: InvestorFlowPoint[];
}

// --- Feature2 전체 메트릭스 ---
export interface Feature2Metrics {
  stock: StockMeta | null;
  industry: IndustryMeta | null;
  industryIndex: IndustryIndexBlock | null;
  peerCluster: PeerCluster | null;
  shortSelling: ShortSellingMetrics | null;
  baseRate: BaseRateMetrics | null;
  macroRates?: Feature2MacroRates | null;
  macroRatesSeries?: Feature2MacroRatesSeries | null;
  investorFlow?: Feature2InvestorFlow | null;
  baseRateTrendSummary?: BaseRateTrendSummary | null;
  shortSellingTrendSummary?: ShortSellingTrendSummary | null;
  newsList: NewsItemDto[];
  newsSentimentSummary?: NewsSentimentSummary | null;
}

// --- Feature2 분석 응답 ---
export interface Feature2AnalyzeResponse {
  metrics: Feature2Metrics | null;
  explain: {
    provider?: string | null;
    model?: string | null;
    text?: string | null;
    sections?: {
      macroEnvironment?: AnalysisExplainSection | null;
      investorFlow?: AnalysisExplainSection | null;
      shortSelling?: AnalysisExplainSection | null;
      peerCluster?: AnalysisExplainSection | null;
      newsSentiment?: AnalysisExplainSection | null;
      crossSignal?: AnalysisExplainSection | null;
    } | null;
    overall?: {
      summary?: string | null;
      bullets?: string[] | null;
      risks?: string[] | null;
      conclusion?: string | null;
    } | null;
    warnings?: unknown[] | null;
  } | null;
}
