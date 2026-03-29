package com.qaima.dto.stock;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StockResponseDto(
        Long stockId,
        String stockCode,
        String companyName,
        BigDecimal price,
        BigDecimal changeRate,
        String exchangeCode,
        String currency
) {}
