package com.qaima.external.dto.feature3;

public record Feature3InternalAnalysisRequestDto(
        RequestContext requestContext,
        Portfolio portfolio,
        Feature3FastApiAnalyzeRequestDto.InputData inputData,
        Feature3FastApiAnalyzeRequestDto.Options options
) {
    public static Feature3InternalAnalysisRequestDto from(Feature3FastApiAnalyzeRequestDto request) {
        return new Feature3InternalAnalysisRequestDto(
                new RequestContext(
                        "FEATURE3",
                        null,
                        null,
                        request.investLevel(),
                        request.options() != null ? request.options().llmVendor() : null,
                        request.options() != null && Boolean.TRUE.equals(request.options().includeLlmExplain()),
                        request.options() != null ? request.options().languageCode() : null
                ),
                new Portfolio(
                        request.portfolioId(),
                        request.holdings(),
                        request.cashPositions(),
                        request.riskProfile()
                ),
                request.inputData(),
                request.options()
        );
    }

    public record RequestContext(
            String feature,
            String requestId,
            String asOf,
            String investLevel,
            String llmVendor,
            Boolean includeLlmExplain,
            String languageCode
    ) {
    }

    public record Portfolio(
            Long portfolioId,
            java.util.List<Feature3FastApiAnalyzeRequestDto.Holding> holdings,
            java.util.List<Feature3FastApiAnalyzeRequestDto.CashPosition> cashPositions,
            Feature3FastApiAnalyzeRequestDto.RiskProfile riskProfile
    ) {
    }
}
