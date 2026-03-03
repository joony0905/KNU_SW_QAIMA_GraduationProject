package com.qaima.mapper;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.dto.financial.FinancialDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialMapper {

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    public FinancialDto toDto(Financial f) {
        if (f == null) return null;

        // periodType/periodNo
        Integer quarter = null;
        Integer half = null;

        PeriodType pt = f.getPeriodType();
        if (pt == PeriodType.Q) {
            quarter = (f.getFiscalQuarter() != null) ? f.getFiscalQuarter() : f.getPeriodNo();
        } else if (pt == PeriodType.H) {
            half = f.getPeriodNo(); // 1 or 2
        }

        // 부채비율 = liabilities / equity * 100
        Double debtRatio = null;
        if (f.getLiabilities() != null && f.getEquity() != null && f.getEquity().signum() != 0) {
            BigDecimal ratio = f.getLiabilities()
                    .divide(f.getEquity(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            debtRatio = ratio.doubleValue();
        }

        return FinancialDto.builder()
                // 식별/메타
                .financialId(f.getFinancialId())
                .stockId(f.getStock().getStockId())
                .ticker(f.getStock().getStockCode())
                .companyName(f.getStock().getCompanyName())
                .year(f.getFiscalYear())
                .quarter(quarter)
                .half(half)
                .periodType(pt != null ? pt.name() : null)
                .periodNo(f.getPeriodNo())
                .reportDate(f.getReportDate())

                // 규모(원 단위)
                .revenue(f.getRevenue())
                .grossProfit(f.getGrossProfit())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .liabilities(f.getLiabilities())
                .equity(f.getEquity())
                .capitalStock(f.getCapitalStock())
                .retainedEarnings(f.getRetainedEarnings())
                .cashAndEquivalents(f.getCashAndEquivalents())
                .marketCap(f.getMarketCap())

                // 비율/배수(Double)
                .operatingMargin(bdToDouble(f.getOperatingMargin()))
                .netMargin(bdToDouble(f.getNetMargin()))
                .roe(bdToDouble(f.getRoe()))
                .per(bdToDouble(f.getPer()))
                .pbr(bdToDouble(f.getPbr()))
                .debtRatio(debtRatio)

                .build();
    }
}
