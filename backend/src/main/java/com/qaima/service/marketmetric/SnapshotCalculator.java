package com.qaima.service.marketmetric;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.service.marketmetric.model.MarketSnapshotInput;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import com.qaima.service.marketmetric.support.MetricMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class SnapshotCalculator {

    public SnapshotMetricView calculate(MarketSnapshotInput input) {
        List<String> warnings = new ArrayList<>();
        if (input == null) {
            return emptySnapshot(warnings, null);
        }

        List<Financial> financials = input.financials() == null ? List.of() : input.financials();
        List<Financial> quarters = financials.stream()
                .filter(financial -> financial != null && financial.getPeriodType() == PeriodType.Q)
                .sorted(financialComparator().reversed())
                .toList();
        List<Financial> annuals = financials.stream()
                .filter(financial -> financial != null && financial.getPeriodType() == PeriodType.A)
                .sorted(financialComparator().reversed())
                .toList();

        BigDecimal revenueBase = null;
        BigDecimal operatingIncomeBase = null;
        BigDecimal netIncomeBase = null;
        Financial anchor = null;

        List<Financial> latestFour = latestContiguousFourQuarters(quarters);
        if (latestFour != null) {
            revenueBase = sumField(latestFour, Financial::getRevenue);
            operatingIncomeBase = sumField(latestFour, Financial::getOperatingIncome);
            netIncomeBase = sumField(latestFour, Financial::getNetIncome);
            anchor = latestFour.get(0);
        } else if (!annuals.isEmpty()) {
            Financial latestAnnual = annuals.get(0);
            revenueBase = latestAnnual.getRevenue();
            operatingIncomeBase = latestAnnual.getOperatingIncome();
            netIncomeBase = latestAnnual.getNetIncome();
            anchor = latestAnnual;
            warnings.add("TTM_FALLBACK_TO_ANNUAL");
        } else {
            return emptySnapshot(warnings, input.asOfDate());
        }

        BigDecimal equity = anchor.getEquity();
        BigDecimal assets = anchor.getAssets();
        BigDecimal liabilities = anchor.getLiabilities();

        return new SnapshotMetricView(
                input.asOfDate(),
                input.sharesOutstanding(),
                input.floatingShares(),
                input.treasuryShares(),
                MetricMath.divide(netIncomeBase, input.sharesOutstanding()),
                MetricMath.divide(equity, input.sharesOutstanding()),
                MetricMath.divide(revenueBase, input.sharesOutstanding()),
                MetricMath.ratioPercent(netIncomeBase, equity),
                MetricMath.ratioPercent(netIncomeBase, assets),
                MetricMath.ratioPercent(operatingIncomeBase, revenueBase),
                MetricMath.ratioPercent(netIncomeBase, revenueBase),
                MetricMath.ratioPercent(liabilities, equity),
                anchor.getCurrentAssets(),
                anchor.getCurrentLiabilities(),
                anchor.getInventories(),
                anchor.getInterestExpense(),
                anchor.getOperatingCashFlow(),
                sumNullable(anchor.getCapexPpe(), anchor.getCapexIntangible()),
                warnings,
                "BATCH_SNAPSHOT"
        );
    }

    private List<Financial> latestContiguousFourQuarters(List<Financial> quarters) {
        if (quarters == null || quarters.size() < 4) {
            return null;
        }

        List<Financial> latestFour = quarters.subList(0, 4);
        for (int i = 0; i < latestFour.size() - 1; i++) {
            Financial current = latestFour.get(i);
            Financial next = latestFour.get(i + 1);
            if (!isPreviousQuarter(current, next)) {
                return null;
            }
        }
        return latestFour;
    }

    private boolean isPreviousQuarter(Financial current, Financial next) {
        int currentYear = current.getFiscalYear();
        int currentQuarter = current.getPeriodNo();
        int expectedYear = currentQuarter == 1 ? currentYear - 1 : currentYear;
        int expectedQuarter = currentQuarter == 1 ? 4 : currentQuarter - 1;
        return next.getFiscalYear() == expectedYear && next.getPeriodNo() == expectedQuarter;
    }

    private SnapshotMetricView emptySnapshot(List<String> warnings, LocalDate asOfDate) {
        return new SnapshotMetricView(
                asOfDate, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, warnings, "BATCH_SNAPSHOT"
        );
    }

    private Comparator<Financial> financialComparator() {
        return Comparator
                .comparing(Financial::getFiscalYear)
                .thenComparing(Financial::getPeriodNo)
                .thenComparing(Financial::getReportDate, Comparator.nullsLast(LocalDate::compareTo));
    }

    private BigDecimal sumField(List<Financial> financials, Function<Financial, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (Financial financial : financials) {
            BigDecimal value = getter.apply(financial);
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total;
    }

    private BigDecimal sumNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }
}
