package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
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
public class Feature2ExplainRequestDto {
    private String stockCode;
    private Freq freq;
    private Integer window;
    private String llmVendor;
    private String investLevel;
    private Feature2ExplainMetricsDto metrics;
}
