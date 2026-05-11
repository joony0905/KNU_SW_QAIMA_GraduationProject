package com.qaima.dto.feature3;

import java.util.List;

public record Feature3PriceSeriesResponseDto(
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
