package com.qaima.dto.stock;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockCodeMappingDto {

    private final String stockCode;
    private final String companyName;
    private final String exchangeCode;
    private final String symbol;
}
