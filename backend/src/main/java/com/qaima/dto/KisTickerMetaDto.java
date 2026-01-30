package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class KisTickerMetaDto {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal changeRate;
}
