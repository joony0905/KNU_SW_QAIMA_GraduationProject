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
public class Feature1InboundIndicatorSpecDto {
    private Integer emaPeriod;
    private Integer bollingerPeriod;
    private Integer bollingerStdDev;
    private Integer stochasticKPeriod;
    private Integer stochasticDPeriod;
    private Integer stochasticSmooth;
}
