package com.qaima.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockCodeMappingDto {
    private String stockCode;
    private String companyName;
    private String exchangeCode;
    private String symbol;
}
