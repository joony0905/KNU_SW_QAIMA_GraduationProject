package com.qaima.external.dto.feature3;

import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import java.util.List;
import java.util.Map;

public record Feature3FastApiAnalyzeResponseDto(
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
    public PortfolioAnalyzeResponseDto toPublicDto() {
        return new PortfolioAnalyzeResponseDto(
                policy != null ? policy.toPublicDto() : null,
                summary != null ? summary.toPublicDto() : null,
                currentPortfolio != null ? currentPortfolio.toPublicDto() : null,
                basicPortfolios != null ? basicPortfolios.stream().map(PortfolioResult::toPublicDto).toList() : List.of(),
                riskDrivers != null ? riskDrivers.stream().map(RiskDriver::toPublicDto).toList() : List.of(),
                advanced != null ? advanced.toPublicDto() : null,
                overlays != null ? overlays.toPublicDto() : new PortfolioAnalyzeResponseDto.OverlayResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                explain != null ? explain.toPublicDto() : null,
                warnings != null ? warnings.stream().map(Warning::toPublicDto).toList() : List.of(),
                freshness != null ? freshness.toPublicDto() : null
        );
    }

    public record PolicyEcho(
            PricePolicy pricePolicy,
            RiskProfileEcho riskProfile,
            DataPolicy dataPolicy,
            DataQuality dataQuality,
            RiskFreePolicy riskFreePolicy
    ) {
        PortfolioAnalyzeResponseDto.PolicyEcho toPublicDto() {
            return new PortfolioAnalyzeResponseDto.PolicyEcho(
                    pricePolicy != null ? pricePolicy.toPublicDto() : null,
                    riskProfile != null ? riskProfile.toPublicDto() : null,
                    dataPolicy != null ? dataPolicy.toPublicDto() : null,
                    dataQuality != null ? dataQuality.toPublicDto() : null,
                    riskFreePolicy != null ? riskFreePolicy.toPublicDto() : null
            );
        }
    }

    public record PricePolicy(String requested, String used, List<String> warnings) {
        PortfolioAnalyzeResponseDto.PricePolicy toPublicDto() {
            return new PortfolioAnalyzeResponseDto.PricePolicy(requested, used, warnings);
        }
    }

    public record RiskProfileEcho(
            Double riskToleranceScore,
            Double riskAversionGamma,
            String profileType,
            Double targetVolatility
    ) {
        PortfolioAnalyzeResponseDto.RiskProfileEcho toPublicDto() {
            return new PortfolioAnalyzeResponseDto.RiskProfileEcho(
                    riskToleranceScore, riskAversionGamma, profileType, targetVolatility
            );
        }
    }

    public record DataPolicy(
            String returnType,
            Integer lookbackTradingDays,
            Integer fetchCalendarDays,
            Integer annualizationFactor,
            Integer minObservations,
            Double maxMissingRate
    ) {
        PortfolioAnalyzeResponseDto.DataPolicy toPublicDto() {
            return new PortfolioAnalyzeResponseDto.DataPolicy(
                    returnType, lookbackTradingDays, fetchCalendarDays, annualizationFactor,
                    minObservations, maxMissingRate
            );
        }
    }

    public record DataQuality(
            Integer expectedTradingDayCount,
            Integer includedHoldingCount,
            Integer excludedHoldingCount,
            List<PriceSeriesQuality> priceSeries,
            List<ExcludedHolding> excludedHoldings
    ) {
        PortfolioAnalyzeResponseDto.DataQuality toPublicDto() {
            return new PortfolioAnalyzeResponseDto.DataQuality(
                    expectedTradingDayCount,
                    includedHoldingCount,
                    excludedHoldingCount,
                    priceSeries != null ? priceSeries.stream().map(PriceSeriesQuality::toPublicDto).toList() : List.of(),
                    excludedHoldings != null ? excludedHoldings.stream().map(ExcludedHolding::toPublicDto).toList() : List.of()
            );
        }
    }

    public record RiskFreePolicy(
            Double rate,
            String source,
            String asOf,
            String instrumentCode,
            String instrumentName
    ) {
        PortfolioAnalyzeResponseDto.RiskFreePolicy toPublicDto() {
            return new PortfolioAnalyzeResponseDto.RiskFreePolicy(
                    rate, source, asOf, instrumentCode, instrumentName
            );
        }
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
        PortfolioAnalyzeResponseDto.PriceSeriesQuality toPublicDto() {
            return new PortfolioAnalyzeResponseDto.PriceSeriesQuality(
                    stockCode, companyName, requestedPriceBasis, usedPriceBasis, source, cacheStatus,
                    expectedTradingDayCount, availablePriceCount, missingRate, fallbackUsed,
                    warnings != null ? warnings.stream().map(Warning::toPublicDto).toList() : List.of()
            );
        }
    }

    public record ExcludedHolding(
            String stockCode,
            String companyName,
            String reason,
            Integer availablePriceCount,
            Integer expectedTradingDayCount,
            Double missingRate
    ) {
        PortfolioAnalyzeResponseDto.ExcludedHolding toPublicDto() {
            return new PortfolioAnalyzeResponseDto.ExcludedHolding(
                    stockCode, companyName, reason, availablePriceCount, expectedTradingDayCount, missingRate
            );
        }
    }

    public record Summary(
            String riskLevel,
            String suitability,
            Double annualizedVolatility,
            Double targetVolatility,
            Double volatilityGap,
            List<String> mainRiskDrivers
    ) {
        PortfolioAnalyzeResponseDto.Summary toPublicDto() {
            return new PortfolioAnalyzeResponseDto.Summary(
                    riskLevel, suitability, annualizedVolatility, targetVolatility,
                    volatilityGap, mainRiskDrivers
            );
        }
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
        PortfolioAnalyzeResponseDto.PortfolioResult toPublicDto() {
            return new PortfolioAnalyzeResponseDto.PortfolioResult(
                    type, label, expectedReturn, rawHistoricalReturn, displayExpectedReturn, isDisplayCapped,
                    additionalRequiredCash, theoreticalRiskyAllocation, constraintBinding,
                    sharpeRatio, volatility, targetVolatility, achievedVolatility,
                    riskLevel, optimizationStatus,
                    weights != null ? weights.stream().map(Weight::toPublicDto).toList() : List.of(),
                    riskContributions != null ? riskContributions.stream().map(RiskContribution::toPublicDto).toList() : List.of(),
                    userDescription
            );
        }
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
        PortfolioAnalyzeResponseDto.Weight toPublicDto() {
            return new PortfolioAnalyzeResponseDto.Weight(
                    stockCode,
                    companyName,
                    assetType,
                    weight,
                    quantity,
                    avgPrice,
                    currentPrice,
                    costBasisValue,
                    marketValue,
                    unrealizedPnl,
                    unrealizedReturnRate
            );
        }
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
        PortfolioAnalyzeResponseDto.RiskContribution toPublicDto() {
            return new PortfolioAnalyzeResponseDto.RiskContribution(
                    stockCode, companyName, weight, volatility, marginalRiskContribution,
                    riskContribution, riskContributionPct
            );
        }
    }

    public record RiskDriver(
            String code,
            String severity,
            String title,
            String description,
            List<String> affectedHoldings,
            String source
    ) {
        PortfolioAnalyzeResponseDto.RiskDriver toPublicDto() {
            return new PortfolioAnalyzeResponseDto.RiskDriver(
                    code, severity, title, description, affectedHoldings, source
            );
        }
    }

    public record AdvancedResult(
            List<PortfolioResult> candidatePortfolios,
            CovarianceDiagnostics covarianceDiagnostics,
            Map<String, Object> correlationMatrix,
            List<Map<String, Object>> frontier,
            Map<String, Object> expectedReturnPolicy
    ) {
        PortfolioAnalyzeResponseDto.AdvancedResult toPublicDto() {
            return new PortfolioAnalyzeResponseDto.AdvancedResult(
                    candidatePortfolios != null ? candidatePortfolios.stream().map(PortfolioResult::toPublicDto).toList() : List.of(),
                    covarianceDiagnostics != null ? covarianceDiagnostics.toPublicDto() : null,
                    correlationMatrix,
                    frontier != null ? frontier : List.of(),
                    expectedReturnPolicy
            );
        }
    }

    public record OverlayResult(
            List<OverlayInsightCard> insightCards,
            List<HoldingOverlayRow> holdingOverlayTable,
            List<Map<String, Object>> advancedOverlayExposure,
            List<OverlaySignal> overlaySignals,
            List<PortfolioResult> adjustedPortfolios,
            List<Map<String, Object>> visualizations,
            List<Map<String, Object>> explanations
    ) {
        PortfolioAnalyzeResponseDto.OverlayResult toPublicDto() {
            return new PortfolioAnalyzeResponseDto.OverlayResult(
                    insightCards != null ? insightCards.stream().map(OverlayInsightCard::toPublicDto).toList() : List.of(),
                    holdingOverlayTable != null ? holdingOverlayTable.stream().map(HoldingOverlayRow::toPublicDto).toList() : List.of(),
                    advancedOverlayExposure != null ? advancedOverlayExposure : List.of(),
                    overlaySignals != null ? overlaySignals.stream().map(OverlaySignal::toPublicDto).toList() : List.of(),
                    adjustedPortfolios != null ? adjustedPortfolios.stream().map(PortfolioResult::toPublicDto).toList() : List.of(),
                    visualizations != null ? visualizations : List.of(),
                    explanations != null ? explanations : List.of()
            );
        }
    }

    public record OverlaySignal(
            String stockCode,
            String companyName,
            String overlayType,
            String label,
            Double score,
            String severity,
            String source,
            String evidence
    ) {
        PortfolioAnalyzeResponseDto.OverlaySignal toPublicDto() {
            return new PortfolioAnalyzeResponseDto.OverlaySignal(
                    stockCode, companyName, overlayType, label, score, severity, source, evidence
            );
        }
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
        PortfolioAnalyzeResponseDto.OverlayInsightCard toPublicDto() {
            return new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                    overlayType, title, description, severity, source, cacheStatus,
                    affectedHoldings != null ? affectedHoldings : List.of()
            );
        }
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
        PortfolioAnalyzeResponseDto.HoldingOverlayRow toPublicDto() {
            return new PortfolioAnalyzeResponseDto.HoldingOverlayRow(
                    stockCode, companyName, overlayType, label, value, severity, source, cacheStatus
            );
        }
    }

    public record ExplainResult(
            String provider,
            String model,
            String text,
            ExplainSections sections,
            ExplainOverall overall,
            List<Warning> warnings
    ) {
        PortfolioAnalyzeResponseDto.ExplainResult toPublicDto() {
            return new PortfolioAnalyzeResponseDto.ExplainResult(
                    provider,
                    model,
                    text,
                    sections != null ? sections.toPublicDto() : null,
                    overall != null ? overall.toPublicDto() : null,
                    warnings != null ? warnings.stream().map(Warning::toPublicDto).toList() : List.of()
            );
        }
    }

    public record ExplainSections(
            ExplainSection coreRisk,
            ExplainSection overlayObservations,
            ExplainSection portfolioComparison,
            ExplainSection volatilityAnalysis,
            ExplainSection efficiencyAnalysis,
            ExplainSection finalJudgement
    ) {
        PortfolioAnalyzeResponseDto.ExplainSections toPublicDto() {
            return new PortfolioAnalyzeResponseDto.ExplainSections(
                    coreRisk != null ? coreRisk.toPublicDto() : null,
                    overlayObservations != null ? overlayObservations.toPublicDto() : null,
                    portfolioComparison != null ? portfolioComparison.toPublicDto() : null,
                    volatilityAnalysis != null ? volatilityAnalysis.toPublicDto() : null,
                    efficiencyAnalysis != null ? efficiencyAnalysis.toPublicDto() : null,
                    finalJudgement != null ? finalJudgement.toPublicDto() : null
            );
        }
    }

    public record ExplainSection(
            String title,
            String summary,
            List<String> bullets
    ) {
        PortfolioAnalyzeResponseDto.ExplainSection toPublicDto() {
            return new PortfolioAnalyzeResponseDto.ExplainSection(title, summary, bullets != null ? bullets : List.of());
        }
    }

    public record ExplainOverall(
            String summary,
            List<String> bullets,
            List<String> risks,
            String conclusion
    ) {
        PortfolioAnalyzeResponseDto.ExplainOverall toPublicDto() {
            return new PortfolioAnalyzeResponseDto.ExplainOverall(
                    summary,
                    bullets != null ? bullets : List.of(),
                    risks != null ? risks : List.of(),
                    conclusion
            );
        }
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
        PortfolioAnalyzeResponseDto.CovarianceDiagnostics toPublicDto() {
            return new PortfolioAnalyzeResponseDto.CovarianceDiagnostics(
                    requestedCovarianceModel, usedCovarianceModel, sampleSize, assetCount,
                    shrinkageLambda, minEigenvalue, conditionNumber, positiveDefinite
            );
        }
    }

    public record Warning(
            String code,
            String message,
            String userMessage,
            String severity,
            String target
    ) {
        PortfolioAnalyzeResponseDto.Warning toPublicDto() {
            return new PortfolioAnalyzeResponseDto.Warning(code, message, userMessage, severity, target);
        }
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
        PortfolioAnalyzeResponseDto.Freshness toPublicDto() {
            return new PortfolioAnalyzeResponseDto.Freshness(
                    coreRiskAsOf, priceSeriesAsOf, hasMixedFreshness, newestDataAt, oldestDataAt, userMessage,
                    overlays != null ? overlays.stream().map(FreshnessOverlay::toPublicDto).toList() : List.of()
            );
        }
    }

    public record FreshnessOverlay(
            String overlayType,
            String status,
            String analyzedAt,
            String dataAsOf,
            String expiresAt,
            String source
    ) {
        PortfolioAnalyzeResponseDto.FreshnessOverlay toPublicDto() {
            return new PortfolioAnalyzeResponseDto.FreshnessOverlay(
                    overlayType, status, analyzedAt, dataAsOf, expiresAt, source
            );
        }
    }
}
