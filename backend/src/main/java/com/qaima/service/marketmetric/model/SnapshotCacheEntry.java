package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class SnapshotCacheEntry {
    private final LocalDate asOfDate;
    private final BigDecimal sharesOutstanding;
    private final BigDecimal floatingShares;
    private final BigDecimal treasuryShares;
    private final BigDecimal epsTtm;
    private final BigDecimal bps;
    private final BigDecimal sps;
    private final BigDecimal roe;
    private final BigDecimal roa;
    private final BigDecimal operatingMargin;
    private final BigDecimal netMargin;
    private final BigDecimal debtRatio;
    private final BigDecimal currentAssets;
    private final BigDecimal currentLiabilities;
    private final BigDecimal inventory;
    private final BigDecimal interestExpense;
    private final BigDecimal operatingCashFlow;
    private final BigDecimal capex;
    private final List<String> warnings;
    private final String source;
}
