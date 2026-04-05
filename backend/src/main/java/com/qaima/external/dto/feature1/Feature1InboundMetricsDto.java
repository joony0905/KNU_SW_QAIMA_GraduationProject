package com.qaima.external.dto.feature1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.featone.FeatOneFinancialSeriesDto;
import com.qaima.dto.featone.FeatOneMarketSnapshotDto;
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
    private FeatOneFinancialSeriesDto financialSeries;
    private FeatOneMarketSnapshotDto marketSnapshot;
    private Feature1InboundIndicatorBundleDto indicators;
    private String indicatorSummary;
    private String schemaVersion;
}
