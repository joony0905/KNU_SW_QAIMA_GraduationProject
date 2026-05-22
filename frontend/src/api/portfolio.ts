// src/api/portfolio.ts
import api from "./apiClient";
import { ENDPOINTS } from "./endpoints";
import type { ApiResponse } from "../types/common/api";
import type { InvestLevel } from "../utils/investLevel";

export type Feature3ProfileType = "CONSERVATIVE" | "NEUTRAL" | "AGGRESSIVE";
export type Feature3RiskLevel = "LOW" | "MID" | "HIGH";
export type Feature3PortfolioType = "CURRENT" | "STABLE" | "BALANCED" | "AGGRESSIVE" | "PSYCHOLOGICAL" | "MIN_VOL" | "MAX_SHARPE" | "RISK_ALLOCATION" | "UTILITY_OPTIMAL" | "THEORETICAL_UTILITY" | "OVERLAY_BALANCED" | "QUALITY_TILT" | "MOMENTUM_AWARE" | "NEWS_GUARDED" | "DIVERSIFICATION_TILT";
export type Feature3PricePolicyUsed = "YAHOO_ADJ_CLOSE" | "RAW_CLOSE" | "YAHOO_ADJ_CLOSE_WITH_KIS_FALLBACK" | "ADJUSTED_CLOSE" | "CLOSE";
export type Feature3SeriesUsedPriceBasis = "YAHOO_ADJ_CLOSE" | "RAW_CLOSE" | "ADJUSTED_CLOSE" | "CLOSE";

export type PortfolioHoldingRequest = {
  stockCode: string;
  companyName?: string;
  quantity: number;
  avgPrice: number;
  currentPrice?: number;
  currency?: string;
  assetType?: "EQUITY";
};

export type PortfolioCashPositionRequest = {
  currency: string;
  amount: number;
};

export type PortfolioAnalyzeRequest = {
  portfolioId?: number;
  investLevel?: InvestLevel;
  holdings: PortfolioHoldingRequest[];
  cashPositions?: PortfolioCashPositionRequest[];
  riskProfile: {
    riskToleranceScore: number;
    riskAversionGamma?: number;
    profileType?: Feature3ProfileType;
    targetVolatility?: number;
  };
  options?: {
    viewMode?: "BASIC" | "ADVANCED";
    priceBasis?: "ADJUSTED_CLOSE" | "CLOSE";
    covarianceModel?: "LEDOIT_WOLF" | "SAMPLE_COVARIANCE";
    returnType?: "LOG_RETURN";
    lookbackTradingDays?: number;
    fetchCalendarDays?: number;
    annualizationFactor?: number;
    cachePolicy?: "CORE_ONLY" | "REUSE_AVAILABLE" | "REFRESH_MISSING_ONLY" | "FORCE_REFRESH";
    overlayCachePolicies?: Record<string, "REUSE_AVAILABLE" | "FORCE_REFRESH">;
    selectedOverlays?: string[];
    includeFrontier?: boolean;
    includeDiagnostics?: boolean;
    includeLlmExplain?: boolean;
    llmVendor?: string;
    maxCashWeight?: number;
  };
};

export type Feature3OverlayCachePreviewRequest = {
  portfolioId?: number;
  stockCodes?: string[];
  selectedOverlays: string[];
  cachePolicy?: "CORE_ONLY" | "REUSE_AVAILABLE" | "REFRESH_MISSING_ONLY" | "FORCE_REFRESH";
  overlayCachePolicies?: Record<string, "REUSE_AVAILABLE" | "FORCE_REFRESH">;
};

export type Feature3OverlayCachePreviewResponse = {
  coreCredit: number;
  overlayRefreshCredit: number;
  totalCredit: number;
  overlays: Array<{
    overlayType: string;
    cacheStatus: "HIT" | "STALE" | "MISS" | "AVAILABLE" | "UNAVAILABLE";
    additionalCredit: number;
    userConfirmationRequired: boolean;
    userMessage: string;
    sourceCacheStatus?: "HIT" | "MISS" | "AVAILABLE" | "UNAVAILABLE";
    cacheAsOf?: string | null;
    timezone?: string | null;
    policySelectable?: boolean;
  }>;
  userMessage: string;
};

export type Feature3Warning = {
  code: string;
  message: string;
  userMessage?: string | null;
  severity: "INFO" | "WARN" | "ERROR";
  target?: string | null;
};

export type Feature3Weight = {
  stockCode: string;
  companyName?: string | null;
  assetType: "EQUITY" | "CASH";
  weight: number;
  quantity?: number | null;
  avgPrice?: number | null;
  currentPrice?: number | null;
  costBasisValue?: number | null;
  marketValue?: number | null;
  unrealizedPnl?: number | null;
  unrealizedReturnRate?: number | null;
};

export type Feature3RiskContribution = {
  stockCode: string;
  companyName?: string | null;
  weight: number;
  volatility?: number | null;
  marginalRiskContribution?: number | null;
  riskContribution: number;
  riskContributionPct: number;
};

export type Feature3PortfolioResult = {
  type: Feature3PortfolioType;
  label: string;
  expectedReturn?: number | null;
  rawHistoricalReturn?: number | null;
  displayExpectedReturn?: number | null;
  isDisplayCapped?: boolean;
  additionalRequiredCash?: number | null;
  theoreticalRiskyAllocation?: number | null;
  constraintBinding?: "RISKY_MAX" | "CASH_MAX" | string | null;
  sharpeRatio?: number | null;
  volatility: number;
  targetVolatility?: number | null;
  achievedVolatility?: number | null;
  riskLevel: Feature3RiskLevel;
  optimizationStatus: "SUCCESS" | "NEAREST_FEASIBLE" | "FAILED";
  weights: Feature3Weight[];
  riskContributions: Feature3RiskContribution[];
  userDescription?: string | null;
};

export type Feature3BenchmarkPolicy = {
  mode?: "SINGLE_BENCHMARK" | "MULTI_BENCHMARK" | string;
  primaryBenchmarkCode?: string | null;
  primaryBenchmarkName?: string | null;
  benchmarkCode?: string | null;
  benchmarkName?: string | null;
  source?: "DB" | "KIS_BACKFILLED" | "DB_INSUFFICIENT" | "DB_STALE" | "UNAVAILABLE" | string;
  benchmarkAvailable?: boolean;
  expectedTradingDayCount?: number;
  availablePriceCount?: number;
  missingRate?: number;
  capmCandidate?: boolean;
  candidateRule?: string;
  warnings?: string[];
  holdingCount?: number;
  benchmarks?: Feature3BenchmarkPolicy[];
};

export type Feature3CapmAsset = {
  stockCode: string;
  companyName?: string | null;
  benchmarkCode?: string | null;
  benchmarkName?: string | null;
  benchmarkSource?: string | null;
  benchmarkSelectionReason?: string | null;
  benchmarkMode?: string | null;
  historicalExpectedReturn?: number | null;
  capmExpectedReturn?: number | null;
  blendedExpectedReturn?: number | null;
  historicalWeight?: number | null;
  capmWeight?: number | null;
  confidence?: number | null;
  beta?: number | null;
  dailyAlpha?: number | null;
  annualAlpha?: number | null;
  correlation?: number | null;
  rSquared?: number | null;
  commonSampleSize?: number | null;
  annualVolatility?: number | null;
  realizedAnnualVolatility?: number | null;
  optimizerAnnualVolatility?: number | null;
  volatilityReliabilityFactor?: number | null;
  status?: "APPLIED" | "PARTIAL" | "EXCLUDED" | string;
  warnings?: string[];
};

export type Feature3SclSeries = {
  stockCode: string;
  companyName?: string | null;
  benchmarkCode?: string | null;
  benchmarkName?: string | null;
  benchmarkSource?: string | null;
  benchmarkSelectionReason?: string | null;
  line?: {
    dailyAlpha?: number | null;
    annualAlpha?: number | null;
    beta?: number | null;
  } | null;
  points?: Array<{
    date?: string;
    marketReturn: number;
    assetReturn: number;
  }>;
};

export type Feature3CapmPolicy = {
  status?: "AVAILABLE" | "DISABLED" | "UNAVAILABLE" | string;
  model?: string;
  returnUnit?: string;
  betaReturnUnit?: string;
  alphaFields?: string[];
  riskFreeRate?: number;
  equityRiskPremium?: number;
  weightFormula?: string;
  weightsSumToOne?: boolean;
  appliedAssetCount?: number;
  partialAssetCount?: number;
  excludedAssetCount?: number;
  warnings?: string[];
};

export type Feature3Scl = {
  summary?: Record<string, unknown>;
  assets?: Feature3CapmAsset[];
  series?: Feature3SclSeries[];
};

export type Feature3Sml = {
  mode?: "SINGLE_BENCHMARK" | "MULTI_BENCHMARK" | string;
  summary?: Record<string, unknown>;
  line?: Array<{ beta: number; expectedReturn: number }>;
  assets?: Feature3CapmAsset[];
  groups?: Array<{
    benchmarkCode?: string | null;
    benchmarkName?: string | null;
    source?: string | null;
    availablePriceCount?: number | null;
    missingRate?: number | null;
    capmCandidate?: boolean | null;
    warnings?: string[];
    line?: Array<{ beta: number; expectedReturn: number }>;
    assets?: Feature3CapmAsset[];
  }>;
};

export type PortfolioAnalyzeResponse = {
  policy: {
    pricePolicy: {
      requested: "ADJUSTED_CLOSE" | "CLOSE";
      used: Feature3PricePolicyUsed;
      warnings: string[];
    };
    riskProfile: {
      riskToleranceScore: number;
      riskAversionGamma: number;
      profileType: Feature3ProfileType;
      targetVolatility: number;
    };
    dataPolicy: {
      returnType: "LOG_RETURN";
      lookbackTradingDays: number;
      fetchCalendarDays: number;
      annualizationFactor: number;
      minObservations: number;
      maxMissingRate: number;
      maxCommonMissingRate: number;
    };
    dataQuality: {
      expectedTradingDayCount: number;
      commonPriceCount: number;
      commonReturnSampleSize: number;
      commonMissingRate: number;
      includedHoldingCount: number;
      excludedHoldingCount: number;
      priceSeries: Array<{
        stockCode: string;
        companyName?: string | null;
        requestedPriceBasis: "ADJUSTED_CLOSE" | "CLOSE";
        usedPriceBasis: Feature3SeriesUsedPriceBasis;
        source: string;
        cacheStatus: string;
        expectedTradingDayCount: number;
        availablePriceCount: number;
        missingRate: number;
        fallbackUsed: boolean;
        warnings: Feature3Warning[];
      }>;
      excludedHoldings: Array<{
        stockCode: string;
        companyName?: string | null;
        reason: string;
        availablePriceCount: number;
        expectedTradingDayCount: number;
        missingRate: number;
      }>;
    };
    riskFreePolicy?: {
      rate: number;
      source: string;
      asOf?: string | null;
      instrumentCode?: string | null;
      instrumentName?: string | null;
    } | null;
  };
  summary: {
    riskLevel: Feature3RiskLevel;
    suitability: "CONSERVATIVE_THAN_PROFILE" | "ALIGNED" | "AGGRESSIVE_THAN_PROFILE";
    annualizedVolatility: number;
    targetVolatility: number;
    volatilityGap: number;
    mainRiskDrivers: string[];
  };
  currentPortfolio: Feature3PortfolioResult;
  basicPortfolios: Feature3PortfolioResult[];
  riskDrivers: Array<{
    code: string;
    severity: Feature3RiskLevel;
    title: string;
    description: string;
    affectedHoldings: string[];
    source: "CORE_RISK" | "FEATURE1" | "FEATURE2";
  }>;
  advanced?: {
    candidatePortfolios: Feature3PortfolioResult[];
    covarianceDiagnostics?: {
      requestedCovarianceModel: "LEDOIT_WOLF" | "SAMPLE_COVARIANCE";
      usedCovarianceModel: "LEDOIT_WOLF" | "SAMPLE_COVARIANCE";
      sampleSize: number;
      assetCount: number;
      shrinkageLambda?: number | null;
      minEigenvalue?: number | null;
      conditionNumber?: number | null;
      positiveDefinite?: boolean | null;
    } | null;
    correlationMatrix?: Record<string, unknown> | null;
    frontier?: Array<{
      volatility: number;
      expectedReturn: number;
      rawHistoricalReturn?: number | null;
      displayExpectedReturn?: number | null;
      isDisplayCapped?: boolean;
      sharpeRatio?: number | null;
    }>;
    expectedReturnPolicy?: (Record<string, unknown> & {
      assets?: Feature3CapmAsset[];
      averageCapmWeight?: number;
      averageBlendConfidence?: number;
      capmWeightFormula?: string;
      blendFormula?: string;
    }) | null;
    benchmarkPolicy?: Feature3BenchmarkPolicy | null;
    capmPolicy?: Feature3CapmPolicy | null;
    scl?: Feature3Scl | null;
    sml?: Feature3Sml | null;
  } | null;
  overlays?: {
    insightCards: Array<{
      overlayType: string;
      title: string;
      description: string;
      severity: "INFO" | "WARN" | "ERROR";
      source: "FEATURE1" | "FEATURE2" | "FEATURE2_INDUSTRY" | "FEATURE2_PEERCLUSTER" | "FEATURE2_NEWS" | "FEATURE3";
      cacheStatus: "HIT" | "STALE" | "MISS" | "BYPASSED";
      affectedHoldings: string[];
    }>;
    holdingOverlayTable: Array<{
      stockCode: string;
      companyName?: string | null;
      overlayType: string;
      label: string;
      value?: string | null;
      severity: "INFO" | "WARN" | "ERROR";
      source: "FEATURE1" | "FEATURE2" | "FEATURE2_INDUSTRY" | "FEATURE2_PEERCLUSTER" | "FEATURE2_NEWS" | "FEATURE3";
      cacheStatus: "HIT" | "STALE" | "MISS" | "BYPASSED";
    }>;
    advancedOverlayExposure: Array<Record<string, unknown>>;
    overlaySignals: Array<{
      stockCode: string;
      companyName?: string | null;
      overlayType: string;
      label: string;
      score: number;
      severity: "INFO" | "WARN" | "ERROR";
      source: string;
      evidence?: string | null;
    }>;
    adjustedPortfolios: Feature3PortfolioResult[];
    visualizations: Array<{
      type: string;
      title: string;
      chartType: string;
      items: Array<{
        stockCode: string;
        companyName?: string | null;
        label: string;
        score: number;
        severity: "INFO" | "WARN" | "ERROR";
        evidence?: string | null;
      }>;
    }>;
    explanations: Array<{
      stockCode: string;
      companyName?: string | null;
      overlayType: string;
      title: string;
      description: string;
      score: number;
      severity: "INFO" | "WARN" | "ERROR";
    }>;
  };
  explain?: {
    provider: string;
    model?: string | null;
    text?: string | null;
    sections?: {
      coreRisk?: Feature3ExplainSection | null;
      overlayObservations?: Feature3ExplainSection | null;
      portfolioComparison?: Feature3ExplainSection | null;
      volatilityAnalysis?: Feature3ExplainSection | null;
      efficiencyAnalysis?: Feature3ExplainSection | null;
      finalJudgement?: Feature3ExplainSection | null;
    } | null;
    overall?: {
      summary?: string | null;
      bullets?: string[] | null;
      risks?: string[] | null;
      conclusion?: string | null;
    } | null;
    warnings: Feature3Warning[];
  } | null;
  warnings: Feature3Warning[];
  freshness: {
    coreRiskAsOf?: string | null;
    priceSeriesAsOf?: string | null;
    hasMixedFreshness: boolean;
    newestDataAt?: string | null;
    oldestDataAt?: string | null;
    userMessage?: string | null;
    overlays: Array<{
      overlayType: string;
      status: string;
      analyzedAt?: string | null;
      dataAsOf?: string | null;
      expiresAt?: string | null;
      source: string;
    }>;
  };
};

export type Feature3ExplainSection = {
  title?: string | null;
  summary?: string | null;
  bullets?: string[] | null;
};

export const fetchPortfolioAnalysis = async (
  req: PortfolioAnalyzeRequest,
): Promise<PortfolioAnalyzeResponse> => {
  const res = await api.post<ApiResponse<PortfolioAnalyzeResponse>>(
    ENDPOINTS.portfolio.analyze(),
    req,
  );
  return res.data.data;
};

export const previewFeature3OverlayCache = async (
  req: Feature3OverlayCachePreviewRequest,
): Promise<Feature3OverlayCachePreviewResponse> => {
  const res = await api.post<ApiResponse<Feature3OverlayCachePreviewResponse>>(
    ENDPOINTS.portfolio.overlayCachePreview(),
    req,
  );
  return res.data.data;
};
