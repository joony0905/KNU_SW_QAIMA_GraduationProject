package com.qaima.dto.krx;

public enum KrxShortSellingMarket {
    KOSPI("STK", "KOSPI"),
    KOSDAQ("KSQ", "KOSDAQ"),
    KONEX("KNX", "KONEX");

    private final String krxMarketId;
    private final String exchangeCode;

    KrxShortSellingMarket(String krxMarketId, String exchangeCode) {
        this.krxMarketId = krxMarketId;
        this.exchangeCode = exchangeCode;
    }

    public String krxMarketId() {
        return krxMarketId;
    }

    public String exchangeCode() {
        return exchangeCode;
    }
}
