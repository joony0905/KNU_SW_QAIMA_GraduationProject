package com.qaima.external.dto.feature3;

import java.util.List;

public record Feature3FastApiAnalyzeRequestDto(
        Long portfolioId,
        String investLevel,
        List<Holding> holdings,
        List<CashPosition> cashPositions,
        RiskProfile riskProfile,
        Options options,
        List<OverlaySignal> overlaySignals,
        InputData inputData
) {
    public record Holding(
            String stockCode,
            String companyName,
            Double quantity,
            Double avgPrice,
            Double currentPrice,
            String currency,
            String assetType,
            String exchangeCode
    ) {
    }

    public record CashPosition(
            String currency,
            Double amount
    ) {
    }

    public record RiskProfile(
            Double riskToleranceScore,
            Double riskAversionGamma,
            String profileType,
            Double targetVolatility
    ) {
    }

    public record Options(
            String viewMode,
            String priceBasis,
            String covarianceModel,
            String returnType,
            Integer lookbackTradingDays,
            Integer fetchCalendarDays,
            Integer annualizationFactor,
            String cachePolicy,
            List<String> selectedOverlays,
            Boolean includeFrontier,
            Boolean includeDiagnostics,
            Boolean includeLlmExplain,
            String llmVendor,
            String languageCode,
            Double riskFreeRate,
            String riskFreeRateSource,
            String riskFreeRateAsOf,
            Double maxCashWeight
    ) {
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
    }

    public record InputData(
            List<PriceSeries> priceSeries,
            List<BenchmarkSeries> benchmarkSeries,
            List<OverlaySignal> overlaySignals
    ) {
    }

    public record PriceSeries(
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
            List<PricePoint> data,
            List<Warning> warnings
    ) {
    }

    public record BenchmarkSeries(
            String benchmarkCode,
            String benchmarkName,
            String source,
            Boolean benchmarkAvailable,
            Integer expectedTradingDayCount,
            Integer availablePriceCount,
            Double missingRate,
            List<PricePoint> data,
            List<Warning> warnings
    ) {
    }

    public record PricePoint(
            String ts,
            Double close
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
}
