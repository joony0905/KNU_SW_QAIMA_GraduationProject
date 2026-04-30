package com.qaima.service.bondyield;

import java.util.Arrays;

public enum BondYieldInstrument {
    KR3Y("KR3Y", "국고채 3년", 36, "817Y002", "010200000"),
    KR10Y("KR10Y", "국고채 10년", 120, "817Y002", "010210000");

    public static final String CYCLE_DAILY = "D";
    public static final String COUNTRY_KR = "KR";
    public static final String SOURCE_BOK_ECOS = "BOK_ECOS";

    private final String instrumentCode;
    private final String instrumentName;
    private final int maturityMonths;
    private final String statCode;
    private final String itemCode;

    BondYieldInstrument(
            String instrumentCode,
            String instrumentName,
            int maturityMonths,
            String statCode,
            String itemCode
    ) {
        this.instrumentCode = instrumentCode;
        this.instrumentName = instrumentName;
        this.maturityMonths = maturityMonths;
        this.statCode = statCode;
        this.itemCode = itemCode;
    }

    public String instrumentCode() {
        return instrumentCode;
    }

    public String instrumentName() {
        return instrumentName;
    }

    public int maturityMonths() {
        return maturityMonths;
    }

    public String statCode() {
        return statCode;
    }

    public String itemCode() {
        return itemCode;
    }

    public static BondYieldInstrument fromCode(String instrumentCode) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            throw new IllegalArgumentException("instrumentCode is required");
        }
        return Arrays.stream(values())
                .filter(instrument -> instrument.instrumentCode.equalsIgnoreCase(instrumentCode.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported bond yield instrument: " + instrumentCode));
    }
}
