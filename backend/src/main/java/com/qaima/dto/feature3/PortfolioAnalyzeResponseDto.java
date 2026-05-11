package com.qaima.dto.feature3;

import java.util.List;
import java.util.Map;

public record PortfolioAnalyzeResponseDto(
        PolicyEcho policy,
        Summary summary,
        PortfolioResult currentPortfolio,
        List<PortfolioResult> basicPortfolios,
        List<RiskDriver> riskDrivers,
        AdvancedResult advanced,
        OverlayResult overlays,
        ExplainResult explain,
        List<Warning> warnings,
        Freshness freshness
) {
    public record PolicyEcho(
            PricePolicy pricePolicy,
            RiskProfileEcho riskProfile,
            DataPolicy dataPolicy,
            DataQuality dataQuality,
            RiskFreePolicy riskFreePolicy
    ) {
    }

    public record PricePolicy(
            String requested,
            String used,
            List<String> warnings
    ) {
    }

    public record RiskProfileEcho(
            Double riskToleranceScore,
            Double riskAversionGamma,
            String profileType,
            Double targetVolatility
    ) {
    }

    public record DataPolicy(
            String returnType,
            Integer lookbackTradingDays,
            Integer fetchCalendarDays,
            Integer annualizationFactor,
            Integer minObservations,
            Double maxMissingRate
    ) {
    }

    public record DataQuality(
            Integer expectedTradingDayCount,
            Integer includedHoldingCount,
            Integer excludedHoldingCount,
            List<PriceSeriesQuality> priceSeries,
            List<ExcludedHolding> excludedHoldings
    ) {
    }

    public record RiskFreePolicy(
            Double rate,
            String source,
            String asOf,
            String instrumentCode,
            String instrumentName
    ) {
    }

    public record PriceSeriesQuality(
            String stockCode,
            String companyName,
            String requestedPriceBasis,
            String usedPriceBasis,
            String source,
            String cacheStatus,
            Integer expectedTradingDayCount,
            Integer availablePriceCount,
            Double missingRate,
            Boolean fallbackUsed,
            List<Warning> warnings
    ) {
    }

    public record ExcludedHolding(
            String stockCode,
            String companyName,
            String reason,
            Integer availablePriceCount,
            Integer expectedTradingDayCount,
            Double missingRate
    ) {
    }

    public record Summary(
            String riskLevel,
            String suitability,
            Double annualizedVolatility,
            Double targetVolatility,
            Double volatilityGap,
            List<String> mainRiskDrivers
    ) {
    }

    public record PortfolioResult(
            String type,
            String label,
            Double expectedReturn,
            Double rawHistoricalReturn,
            Double displayExpectedReturn,
            Boolean isDisplayCapped,
            Double additionalRequiredCash,
            Double theoreticalRiskyAllocation,
            String constraintBinding,
            Double sharpeRatio,
            Double volatility,
            Double targetVolatility,
            Double achievedVolatility,
            String riskLevel,
            String optimizationStatus,
            List<Weight> weights,
            List<RiskContribution> riskContributions,
            String userDescription
    ) {
    }

    public record Weight(
            String stockCode,
            String companyName,
            String assetType,
            Double weight,
            Double quantity,
            Double avgPrice,
            Double currentPrice,
            Double costBasisValue,
            Double marketValue,
            Double unrealizedPnl,
            Double unrealizedReturnRate
    ) {
    }

    public record RiskContribution(
            String stockCode,
            String companyName,
            Double weight,
            Double volatility,
            Double marginalRiskContribution,
            Double riskContribution,
            Double riskContributionPct
    ) {
    }

    public record RiskDriver(
            String code,
            String severity,
            String title,
            String description,
            List<String> affectedHoldings,
            String source
    ) {
    }

    public record AdvancedResult(
            List<PortfolioResult> candidatePortfolios,
            CovarianceDiagnostics covarianceDiagnostics,
            Map<String, Object> correlationMatrix,
            List<Map<String, Object>> frontier,
            Map<String, Object> expectedReturnPolicy
    ) {
    }

    public record OverlayResult(
            List<OverlayInsightCard> insightCards,
            List<HoldingOverlayRow> holdingOverlayTable,
            List<Map<String, Object>> advancedOverlayExposure
    ) {
    }

    public record OverlayInsightCard(
            String overlayType,
            String title,
            String description,
            String severity,
            String source,
            String cacheStatus,
            List<String> affectedHoldings
    ) {
    }

    public record HoldingOverlayRow(
            String stockCode,
            String companyName,
            String overlayType,
            String label,
            String value,
            String severity,
            String source,
            String cacheStatus
    ) {
    }

    public record ExplainResult(
            String provider,
            String model,
            String text,
            List<Warning> warnings
    ) {
    }

    public record CovarianceDiagnostics(
            String requestedCovarianceModel,
            String usedCovarianceModel,
            Integer sampleSize,
            Integer assetCount,
            Double shrinkageLambda,
            Double minEigenvalue,
            Double conditionNumber,
            Boolean positiveDefinite
    ) {
    }

    public record Warning(
            String code,
            String message,
            String userMessage,
            String severity,
            String target
    ) {
    }

    public record Freshness(
            String coreRiskAsOf,
            String priceSeriesAsOf,
            Boolean hasMixedFreshness,
            String newestDataAt,
            String oldestDataAt,
            String userMessage,
            List<FreshnessOverlay> overlays
    ) {
    }

    public record FreshnessOverlay(
            String overlayType,
            String status,
            String analyzedAt,
            String dataAsOf,
            String expiresAt,
            String source
    ) {
    }
}
