package com.qaima.dto.feature3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

public record PortfolioAnalyzeRequestDto(
        Long portfolioId,
        String investLevel,
        @NotEmpty List<@Valid Holding> holdings,
        List<@Valid CashPosition> cashPositions,
        @Valid @NotNull RiskProfile riskProfile,
        @Valid Options options
) {
    public record Holding(
            @NotNull String stockCode,
            String companyName,
            @Positive Double quantity,
            @Positive Double avgPrice,
            @Positive Double currentPrice,
            String currency,
            String assetType
    ) {
    }

    public record CashPosition(
            String currency,
            @PositiveOrZero Double amount
    ) {
    }

    public record RiskProfile(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double riskToleranceScore,
            @DecimalMin("1.0") @DecimalMax("10.0") Double riskAversionGamma,
            String profileType,
            @PositiveOrZero Double targetVolatility
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
            Map<String, String> overlayCachePolicies,
            List<String> selectedOverlays,
            Boolean includeFrontier,
            Boolean includeDiagnostics,
            Boolean includeLlmExplain,
            String llmVendor,
            @DecimalMin("0.0") @DecimalMax("1.0") Double maxCashWeight
    ) {
    }
}
