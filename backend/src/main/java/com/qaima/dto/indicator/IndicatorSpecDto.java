package com.qaima.dto.indicator;

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
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class IndicatorSpecDto {
    private Integer emaPeriod;
    private Integer bollingerPeriod;
    private Integer bollingerStdDev;
    private Integer stochasticKPeriod;
    private Integer stochasticDPeriod;
    private Integer stochasticSmooth;
}
