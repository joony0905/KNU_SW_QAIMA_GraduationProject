package com.qaima.dto.featone;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.financial.FinancialSummaryMetricsDto;
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
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class FeatOneAnalysisMetricsDto {
    @JsonAlias("stock_code")
    private String stockCode;

    @JsonAlias("as_of")
    private String asOf;

    @JsonAlias("ohlcv_summary")
    private OhlcvSummaryDto ohlcvSummary;

    @JsonAlias("financial_summary")
    private FinancialSummaryMetricsDto financialSummary;

    // metrics 안에 indicators를 포함하는 관통 계약의 ground truth
    private IndicatorBundleDto indicators;

    @JsonAlias("indicator_summary")
    private String indicatorSummary;

    @JsonAlias("schema_version")
    private String schemaVersion;
}
