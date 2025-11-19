package com.qaima.dto;

import lombok.Data;

import java.util.List;

@Data
public class MarketStackTickersResponse {

    private Pagination pagination;
    private List<TickerData> data;

    @Data
    public static class Pagination {
        private int limit;
        private int offset;
        private int count;
        private int total;
    }

    @Data
    public static class TickerData {
        private String name;                // 회사명
        private String symbol;              // 티커
        private Boolean has_intraday;       // intraday 데이터 보유 여부
        private Boolean has_eod;            // EOD 데이터 보유 여부
        private StockExchange stock_exchange;
    }

    @Data
    public static class StockExchange {
        private String name;            // "KOREA EXCHANGE"
        private String acronym;         // "KRX"
        private String mic;             // "XKRX"
        private String country;         // "South Korea"
        private String country_code;    // "KR"
        private String city;            // "Seoul"
    }
}
