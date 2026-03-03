package com.qaima.dto.kis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class KisTickerMetaDto {
    private String stockCode;
    private String companyName;
    private String exchangeCode;
    private String currency;
    private BigDecimal price;
    private BigDecimal changeRate;
}
