package com.qaima.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record StockResponseDto(
        Long stockId,
        String stockCode,
        String companyName,
        BigDecimal price,
        BigDecimal changeRate,
        String exchangeCode,
        String currency
) {}
