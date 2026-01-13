package com.qaima.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MarketStackCandlesResponse {

    private List<CandleData> data;

    @Data
    public static class CandleData {
        private long t;
        private BigDecimal o;
        private BigDecimal h;
        private BigDecimal l;
        private BigDecimal c;
        private long v;
    }
}
