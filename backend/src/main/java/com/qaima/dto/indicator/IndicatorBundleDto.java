package com.qaima.dto.indicator;

import com.qaima.dto.indicator.points.BollingerPointDto;
import com.qaima.dto.indicator.points.IndicatorPoint1Dto;
import com.qaima.dto.indicator.points.StochPointDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorBundleDto {
    private IndicatorSpecDto spec;
    private List<IndicatorPoint1Dto> ema20;
    private List<BollingerPointDto> bb20_2;
    private List<StochPointDto> stoch14_3_3;
    private List<String> warnings;
}
