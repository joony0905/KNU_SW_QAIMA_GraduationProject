package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SnapshotMetricView(
        LocalDate asOfDate,
        BigDecimal sharesOutstanding,
        BigDecimal floatingShares,
        BigDecimal treasuryShares,
        BigDecimal epsTtm,
        BigDecimal bps,
        BigDecimal sps,
        BigDecimal roe,
        BigDecimal roa,
        BigDecimal operatingMargin,
        BigDecimal netMargin,
        BigDecimal debtRatio,
        BigDecimal currentAssets,
        BigDecimal currentLiabilities,
        BigDecimal inventory,
        BigDecimal interestExpense,
        BigDecimal operatingCashFlow,
        BigDecimal capex,
        List<String> warnings,
        String source
) {
    public boolean hasCoreFields() {
        return sharesOutstanding != null
                && (epsTtm != null || bps != null || sps != null);
    }
}
