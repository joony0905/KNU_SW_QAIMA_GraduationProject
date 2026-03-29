package com.qaima.external.dto.feature1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature1InboundMetricsDto {
    private String stockCode;
    private String asOf;
    private Feature1InboundOhlcvSummaryDto ohlcvSummary;
    private Feature1InboundFinancialSummaryDto financialSummary;
    private Feature1InboundIndicatorBundleDto indicators;
    private String indicatorSummary;
    private String schemaVersion;
}
