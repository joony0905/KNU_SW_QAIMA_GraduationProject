package com.qaima.dto.feature3;

import java.util.List;

public record Feature3BenchmarkSeriesResponseDto(
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
