package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneAnalysisMetricsDto {
    private String stockCode;
    private OffsetDateTime asOf;
    private OhlcvSummaryDto ohlcvSummary;
    private FinancialSummaryMetricsDto financialSummary;
    private IndicatorSlotsDto indicators;
    private String schemaVersion;
}
