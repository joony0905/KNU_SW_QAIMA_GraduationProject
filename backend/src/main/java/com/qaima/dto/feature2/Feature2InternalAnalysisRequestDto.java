package com.qaima.dto.feature2;

import com.qaima.domain.Freq;

public record Feature2InternalAnalysisRequestDto(
        RequestContext requestContext,
        Subject subject,
        InputData inputData,
        Options options
) {
    public static Feature2InternalAnalysisRequestDto from(Feature2ExplainRequestDto request) {
        return new Feature2InternalAnalysisRequestDto(
                new RequestContext(
                        "FEATURE2",
                        null,
                        null,
                        request.getInvestLevel(),
                        request.getLlmVendor(),
                        true
                ),
                new Subject(request.getStockCode(), null, null, null),
                new InputData(request.getMetrics()),
                new Options(request.getFreq(), request.getWindow())
        );
    }

    public record RequestContext(
            String feature,
            String requestId,
            String asOf,
            String investLevel,
            String llmVendor,
            Boolean includeLlmExplain
    ) {
    }

    public record Subject(
            String stockCode,
            String companyName,
            String exchangeCode,
            String currency
    ) {
    }

    public record InputData(
            Feature2ExplainMetricsDto metrics
    ) {
    }

    public record Options(
            Freq freq,
            Integer window
    ) {
    }
}
