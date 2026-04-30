package com.qaima.service.bondyield;

import java.util.Arrays;

public enum FredBondYieldInstrument {
    US2Y("US2Y", "미국채 2년", 24, "GS2"),
    US5Y("US5Y", "미국채 5년", 60, "GS5"),
    US10Y("US10Y", "미국채 10년", 120, "GS10");

    public static final String CYCLE_MONTHLY = "M";
    public static final String COUNTRY_US = "US";
    public static final String SOURCE_FRED = "FRED";
    public static final String STAT_NAME = "Treasury Constant Maturity Rate";

    private final String instrumentCode;
    private final String instrumentName;
    private final int maturityMonths;
    private final String seriesId;

    FredBondYieldInstrument(
            String instrumentCode,
            String instrumentName,
            int maturityMonths,
            String seriesId
    ) {
        this.instrumentCode = instrumentCode;
        this.instrumentName = instrumentName;
        this.maturityMonths = maturityMonths;
        this.seriesId = seriesId;
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

    public String seriesId() {
        return seriesId;
    }

    public static FredBondYieldInstrument fromCode(String instrumentCode) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            throw new IllegalArgumentException("instrumentCode is required");
        }
        return Arrays.stream(values())
                .filter(instrument -> instrument.instrumentCode.equalsIgnoreCase(instrumentCode.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported FRED bond yield instrument: " + instrumentCode));
    }
}
