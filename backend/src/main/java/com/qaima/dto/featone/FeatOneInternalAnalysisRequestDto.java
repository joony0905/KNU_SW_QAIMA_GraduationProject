package com.qaima.dto.featone;

import com.qaima.domain.Freq;
import com.qaima.dto.ohlcv.OhlcvItemDto;
import java.util.List;

public record FeatOneInternalAnalysisRequestDto(
        RequestContext requestContext,
        Subject subject,
        InputData inputData,
        Options options
) {
    public static FeatOneInternalAnalysisRequestDto from(FeatOneRequestDto request) {
        return new FeatOneInternalAnalysisRequestDto(
                new RequestContext(
                        "FEATURE1",
                        null,
                        null,
                        request.getInvestLevel(),
                        request.getLlmVendor(),
                        Boolean.TRUE.equals(request.getIncludeExplain()),
                        request.getLanguageCode()
                ),
                new Subject(
                        request.getStockCode(),
                        null,
                        null,
                        request.getMarketContext() != null ? request.getMarketContext().getCurrency() : null
                ),
                new InputData(
                        request.getOhlcv() != null ? request.getOhlcv() : List.of(),
                        request.getFinancials() != null ? request.getFinancials() : List.of(),
                        request.getMarketContext(),
                        request.getMarketSnapshot()
                ),
                new Options(
                        request.getFreq(),
                        request.getFrom(),
                        request.getTo()
                )
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

    public record Subject(
            String stockCode,
            String companyName,
            String exchangeCode,
            String currency
    ) {
    }

    public record InputData(
            List<OhlcvItemDto> ohlcv,
            List<FeatOneFinancialPointDto> financials,
            FeatOneMarketContextDto marketContext,
            FeatOneMarketSnapshotDto marketSnapshot
    ) {
    }

    public record Options(
            Freq freq,
            String from,
            String to
    ) {
    }
}
