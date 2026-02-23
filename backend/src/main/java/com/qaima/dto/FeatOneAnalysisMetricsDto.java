package com.qaima.dto;

import com.qaima.dto.indicator.IndicatorBundleDto;
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
    private String asOf;
    private OhlcvSummaryDto ohlcvSummary;
    private FinancialSummaryMetricsDto financialSummary;
    // metrics 안에 indicators를 포함하는 관통 계약의 ground truth
    private IndicatorBundleDto indicators;
    private String schemaVersion;
}
