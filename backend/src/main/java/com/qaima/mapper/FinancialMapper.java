package com.qaima.mapper;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.dto.financial.FinancialDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class FinancialMapper {

    private static final int METRIC_SCALE = 4;

    private static Double bdToDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public FinancialDto toDto(Financial financial) {
        return toDto(financial, null, null, null);
    }

    public FinancialDto toDto(Financial financial, BigDecimal marketCapOverride) {
        return toDto(financial, marketCapOverride, null, null);
    }

    public FinancialDto toDto(
            Financial financial,
            BigDecimal marketCapOverride,
            BigDecimal currentPriceOverride,
            BigDecimal sharesOverride
    ) {
        if (financial == null) {
            return null;
        }

        PeriodType periodType = financial.getPeriodType();
        Integer quarter = null;
        Integer half = null;
        if (periodType == PeriodType.Q) {
            quarter = financial.getFiscalQuarter() != null ? financial.getFiscalQuarter() : financial.getPeriodNo();
        } else if (periodType == PeriodType.H) {
            half = financial.getPeriodNo();
        }

        Double debtRatio = null;
        if (financial.getLiabilities() != null
                && financial.getEquity() != null
                && financial.getEquity().signum() != 0) {
            debtRatio = financial.getLiabilities()
                    .divide(financial.getEquity(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        BigDecimal marketCap = marketCapOverride;
        boolean annualLike = periodType == PeriodType.A || periodType == PeriodType.TTM;

        BigDecimal eps = null;
        BigDecimal bps = null;
        if (annualLike && sharesOverride != null && sharesOverride.signum() != 0) {
            if (financial.getNetIncome() != null) {
                eps = financial.getNetIncome().divide(sharesOverride, METRIC_SCALE, RoundingMode.HALF_UP);
            }
            if (financial.getEquity() != null) {
                bps = financial.getEquity().divide(sharesOverride, METRIC_SCALE, RoundingMode.HALF_UP);
            }
        }

        Double operatingMargin = null;
        if (financial.getOperatingIncome() != null
                && financial.getRevenue() != null
                && financial.getRevenue().signum() != 0) {
            operatingMargin = financial.getOperatingIncome()
                    .divide(financial.getRevenue(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double netMargin = null;
        if (financial.getNetIncome() != null
                && financial.getRevenue() != null
                && financial.getRevenue().signum() != 0) {
            netMargin = financial.getNetIncome()
                    .divide(financial.getRevenue(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double roe = null;
        if (annualLike
                && financial.getNetIncome() != null
                && financial.getEquity() != null
                && financial.getEquity().signum() != 0) {
            roe = financial.getNetIncome()
                    .divide(financial.getEquity(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double roa = null;
        if (annualLike
                && financial.getNetIncome() != null
                && financial.getAssets() != null
                && financial.getAssets().signum() != 0) {
            roa = financial.getNetIncome()
                    .divide(financial.getAssets(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double currentRatio = null;
        if (financial.getCurrentAssets() != null
                && financial.getCurrentLiabilities() != null
                && financial.getCurrentLiabilities().signum() != 0) {
            currentRatio = financial.getCurrentAssets()
                    .divide(financial.getCurrentLiabilities(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double quickRatio = null;
        if (financial.getCurrentAssets() != null
                && financial.getCurrentLiabilities() != null
                && financial.getCurrentLiabilities().signum() != 0) {
            BigDecimal quickAssets = financial.getInventories() != null
                    ? financial.getCurrentAssets().subtract(financial.getInventories())
                    : financial.getCurrentAssets();
            quickRatio = quickAssets
                    .divide(financial.getCurrentLiabilities(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Double interestCoverageRatio = null;
        if (financial.getOperatingIncome() != null
                && financial.getInterestExpense() != null
                && financial.getInterestExpense().signum() != 0) {
            interestCoverageRatio = financial.getOperatingIncome()
                    .divide(financial.getInterestExpense(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        BigDecimal freeCashFlow = null;
        if (financial.getOperatingCashFlow() != null) {
            BigDecimal capex = BigDecimal.ZERO;
            boolean hasCapex = false;
            if (financial.getCapexPpe() != null) {
                capex = capex.add(financial.getCapexPpe());
                hasCapex = true;
            }
            if (financial.getCapexIntangible() != null) {
                capex = capex.add(financial.getCapexIntangible());
                hasCapex = true;
            }
            freeCashFlow = hasCapex ? financial.getOperatingCashFlow().subtract(capex) : financial.getOperatingCashFlow();
        }

        Double per = null;
        if (currentPriceOverride != null && eps != null && eps.signum() != 0) {
            per = currentPriceOverride
                    .divide(eps, METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (marketCap != null
                && annualLike
                && financial.getNetIncome() != null
                && financial.getNetIncome().signum() != 0) {
            per = marketCap
                    .divide(financial.getNetIncome(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Double pbr = null;
        if (currentPriceOverride != null && bps != null && bps.signum() != 0) {
            pbr = currentPriceOverride
                    .divide(bps, METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (marketCap != null
                && financial.getEquity() != null
                && financial.getEquity().signum() != 0) {
            pbr = marketCap
                    .divide(financial.getEquity(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Double psr = null;
        if (marketCap != null
                && annualLike
                && financial.getRevenue() != null
                && financial.getRevenue().signum() != 0) {
            psr = marketCap
                    .divide(financial.getRevenue(), METRIC_SCALE, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return FinancialDto.builder()
                .financialId(financial.getFinancialId())
                .stockId(financial.getStock().getStockId())
                .ticker(financial.getStock().getStockCode())
                .companyName(financial.getStock().getCompanyName())
                .year(financial.getFiscalYear())
                .quarter(quarter)
                .half(half)
                .periodType(periodType != null ? periodType.name() : null)
                .periodNo(financial.getPeriodNo())
                .reportDate(financial.getReportDate())
                .revenue(financial.getRevenue())
                .grossProfit(financial.getGrossProfit())
                .operatingIncome(financial.getOperatingIncome())
                .netIncome(financial.getNetIncome())
                .assets(financial.getAssets())
                .liabilities(financial.getLiabilities())
                .equity(financial.getEquity())
                .capitalStock(financial.getCapitalStock())
                .retainedEarnings(financial.getRetainedEarnings())
                .cashAndEquivalents(financial.getCashAndEquivalents())
                .marketCap(marketCap)
                .eps(eps)
                .bps(bps)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .roe(roe)
                .roa(roa)
                .per(per)
                .pbr(pbr)
                .psr(psr)
                .debtRatio(debtRatio)
                .currentRatio(currentRatio)
                .quickRatio(quickRatio)
                .interestCoverageRatio(interestCoverageRatio)
                .freeCashFlow(freeCashFlow)
                .build();
    }
}
