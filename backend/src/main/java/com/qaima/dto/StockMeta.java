package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class StockMeta {
    private String stockCode;
    private String companyName;
    private String exchangeCode;
    private String countryCode;
    private String currency;
    private BigDecimal price;
    private BigDecimal changeRate;
    private String source;
}
