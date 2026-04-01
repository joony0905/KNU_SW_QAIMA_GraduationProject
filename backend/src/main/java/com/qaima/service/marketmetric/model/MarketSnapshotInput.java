package com.qaima.service.marketmetric.model;

import com.qaima.domain.Financial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MarketSnapshotInput(
        List<Financial> financials,
        BigDecimal sharesOutstanding,
        BigDecimal floatingShares,
        BigDecimal treasuryShares,
        LocalDate asOfDate,
        BigDecimal currentAssets,
        BigDecimal currentLiabilities,
        BigDecimal inventory,
        BigDecimal interestExpense,
        BigDecimal operatingCashFlow,
        BigDecimal capex
) {
}
