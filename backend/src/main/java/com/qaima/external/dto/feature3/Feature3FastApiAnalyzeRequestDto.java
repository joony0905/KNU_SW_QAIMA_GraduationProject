package com.qaima.external.dto.feature3;

import java.util.List;

public record Feature3FastApiAnalyzeRequestDto(
        Long portfolioId,
        List<Holding> holdings,
        List<CashPosition> cashPositions,
        RiskProfile riskProfile,
        Options options
) {
    public record Holding(
            String stockCode,
            String companyName,
            Double quantity,
            Double avgPrice,
            Double currentPrice,
            String currency,
            String assetType
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
            Double riskFreeRate,
            String riskFreeRateSource,
            String riskFreeRateAsOf,
            Double maxCashWeight
    ) {
    }
}
