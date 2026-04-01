package com.qaima.service.marketmetric;

import com.qaima.domain.Financial;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import com.qaima.service.marketmetric.support.MetricMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancialFallbackCalculator {

    public SnapshotMetricView calculate(List<Financial> financials, BigDecimal sharesOutstanding, LocalDate asOfDate) {
        List<String> warnings = List.of("SNAPSHOT_FALLBACK_USED");
        Financial latest = (financials == null ? List.<Financial>of() : financials).stream()
                .filter(financial -> financial != null)
                .sorted(Comparator
                        .comparing(Financial::getFiscalYear)
                        .thenComparing(Financial::getPeriodNo)
                        .thenComparing(Financial::getReportDate, Comparator.nullsLast(LocalDate::compareTo))
                        .reversed())
                .findFirst()
                .orElse(null);

        if (latest == null) {
            return new SnapshotMetricView(
                    asOfDate, sharesOutstanding, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, warnings, "FINANCIAL_FALLBACK"
            );
        }

        return new SnapshotMetricView(
                asOfDate,
                sharesOutstanding,
                null,
                null,
                MetricMath.divide(latest.getNetIncome(), sharesOutstanding),
                MetricMath.divide(latest.getEquity(), sharesOutstanding),
                MetricMath.divide(latest.getRevenue(), sharesOutstanding),
                MetricMath.ratioPercent(latest.getNetIncome(), latest.getEquity()),
                MetricMath.ratioPercent(latest.getNetIncome(), latest.getAssets()),
                MetricMath.ratioPercent(latest.getOperatingIncome(), latest.getRevenue()),
                MetricMath.ratioPercent(latest.getNetIncome(), latest.getRevenue()),
                MetricMath.ratioPercent(latest.getLiabilities(), latest.getEquity()),
                null,
                null,
                null,
                null,
                null,
                null,
                warnings,
                "FINANCIAL_FALLBACK"
        );
    }
}
