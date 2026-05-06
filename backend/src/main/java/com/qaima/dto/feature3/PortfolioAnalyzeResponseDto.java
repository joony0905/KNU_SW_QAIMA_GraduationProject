package com.qaima.dto.feature3;

public record PortfolioAnalyzeResponseDto(
        String riskLevel,
        Double volatility,
        String diversification,
        Double covarianceScore,
        String efficiency,
        Double gamma
) {
}
