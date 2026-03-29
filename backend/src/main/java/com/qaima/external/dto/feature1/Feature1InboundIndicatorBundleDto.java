package com.qaima.external.dto.feature1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.indicator.points.BollingerPointDto;
import com.qaima.dto.indicator.points.IndicatorPoint1Dto;
import com.qaima.dto.indicator.points.StochPointDto;
import java.util.List;
import java.util.Map;
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
public class Feature1InboundIndicatorBundleDto {
    private Feature1InboundIndicatorSpecDto spec;

    @JsonProperty("ema")
    private Map<String, List<IndicatorPoint1Dto>> ema;

    @JsonProperty("bb20_2")
    private List<BollingerPointDto> bb20_2;

    @JsonProperty("stoch14_3_3")
    private List<StochPointDto> stoch14_3_3;

    private List<String> warnings;
}
