package com.qaima.dto.stock;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StockCodeMappingDto {

    private final String stockCode;
    private final String companyName;
    private final String exchangeCode;
    private final String symbol;
}
