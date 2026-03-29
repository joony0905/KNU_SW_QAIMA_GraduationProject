package com.qaima.service.marketdata.model;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

@Getter
@Builder
@Jacksonized
public class PriceSnapshot {

    private final BigDecimal price;       // 최신 종가
    private final BigDecimal prevPrice;   // 이전 종가
    private final BigDecimal changeRate;      // 등락률 (%)
}