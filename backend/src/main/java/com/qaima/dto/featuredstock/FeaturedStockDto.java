package com.qaima.dto.featuredstock;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record FeaturedStockDto(
        Long stockId,
        String stockCode,
        String companyName,
        String exchangeCode,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal change,
        BigDecimal changeRate
) {}
