package com.qaima.dto;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MarketStackCandlesResponse {

    private List<CandleData> data;

    @Data
    public static class CandleData {
        @JsonProperty("date")
        private String date;

        @JsonProperty("open")
        @JsonAlias("o")
        private BigDecimal open;

        @JsonProperty("high")
        @JsonAlias("h")
        private BigDecimal high;

        @JsonProperty("low")
        @JsonAlias("l")
        private BigDecimal low;

        @JsonProperty("close")
        @JsonAlias("c")
        private BigDecimal close;

        @JsonProperty("volume")
        @JsonAlias("v")
        private Long volume;

        @JsonAlias("t")
        private Long epochSeconds;
    }
}
