package com.qaima.service.exchangerate;

import java.util.Arrays;

public enum ExchangeRateInstrument {
    USD_KRW("USD_KRW", "USD", "KRW", "731Y001", "0000001");

    public static final String CYCLE_DAILY = "D";
    public static final String SOURCE_BOK_ECOS = "BOK_ECOS";

    private final String pairCode;
    private final String baseCurrency;
    private final String quoteCurrency;
    private final String statCode;
    private final String itemCode;

    ExchangeRateInstrument(
            String pairCode,
            String baseCurrency,
            String quoteCurrency,
            String statCode,
            String itemCode
    ) {
        this.pairCode = pairCode;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.statCode = statCode;
        this.itemCode = itemCode;
    }

    public String pairCode() {
        return pairCode;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    public String quoteCurrency() {
        return quoteCurrency;
    }

    public String statCode() {
        return statCode;
    }

    public String itemCode() {
        return itemCode;
    }

    public static ExchangeRateInstrument fromPairCode(String pairCode) {
        if (pairCode == null || pairCode.isBlank()) {
            throw new IllegalArgumentException("pairCode is required");
        }
        return Arrays.stream(values())
                .filter(instrument -> instrument.pairCode.equalsIgnoreCase(pairCode.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported exchange rate pair: " + pairCode));
    }
}
