package com.qaima.dto.featone;

import com.qaima.dto.indicator.IndicatorBundleDto;
import com.qaima.dto.ohlcv.OhlcvSummaryDto;
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
public class FeatOneAnalysisMetricsDto {
    private String stockCode;
    private String asOf;
    private OhlcvSummaryDto ohlcvSummary;
    private FeatOneFinancialSeriesDto financialSeries;
    private FeatOneMarketSnapshotDto marketSnapshot;
    // metrics 안에 indicators를 포함하는 관통 계약의 ground truth
    private IndicatorBundleDto indicators;
    private String indicatorSummary;
    private String schemaVersion;
}
