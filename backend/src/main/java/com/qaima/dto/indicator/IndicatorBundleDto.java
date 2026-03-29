package com.qaima.dto.indicator;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.indicator.points.BollingerPointDto;
import com.qaima.dto.indicator.points.IndicatorPoint1Dto;
import com.qaima.dto.indicator.points.StochPointDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IndicatorBundleDto {

    private IndicatorSpecDto spec;

    /**
     * EMA series by period
     * key: "20", "60", "120"
     */
    @JsonProperty("ema")
    private Map<String, List<IndicatorPoint1Dto>> ema;

    /**
     * Bollinger Bands (fixed: 20,2)
     */
    @JsonProperty("bb20_2")
    private List<BollingerPointDto> bb20_2;

    /**
     * Stochastic (fixed: 14,3,3)
     */
    @JsonProperty("stoch14_3_3")
    private List<StochPointDto> stoch14_3_3;

    private List<String> warnings;
}
