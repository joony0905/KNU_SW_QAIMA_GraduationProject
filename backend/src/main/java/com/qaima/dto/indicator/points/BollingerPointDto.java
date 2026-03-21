package com.qaima.dto.indicator.points;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BollingerPointDto {
    private String t;
    private BigDecimal mid;
    private BigDecimal upper;
    private BigDecimal lower;
}
