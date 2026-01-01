package com.qaima.Mapper;

import com.qaima.domain.Financial;
import com.qaima.dto.FinancialDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialMapper {

    public FinancialDto toDto(Financial f) {
        if (f == null) {
            return null;
        }

        BigDecimal per = f.getPer();
        BigDecimal pbr = f.getPbr();
        BigDecimal roe = f.getRoe();
        BigDecimal operatingMargin = f.getOperatingMargin();
        BigDecimal netMargin = f.getNetMargin();

        // 부채비율 = liabilities / equity * 100
        Double debtRatio = null;
        if (f.getLiabilities() != null
                && f.getEquity() != null
                && f.getEquity().signum() != 0) {

            BigDecimal ratio = f.getLiabilities()
                    .divide(f.getEquity(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            debtRatio = ratio.doubleValue();
        }

        return FinancialDto.builder()
                .stockId(f.getStock().getStockId())
                .ticker(f.getStock().getStockCode())
                .companyName(f.getStock().getCompanyName())
                .year(f.getFiscalYear())
                .quarter(f.getFiscalQuarter())
                .periodType(f.getPeriodType().name())

                .revenue(f.getRevenue())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .liabilities(f.getLiabilities())
                .equity(f.getEquity())
                .capitalStock(f.getCapitalStock())
                .marketCap(f.getMarketCap())

                .operatingMargin(operatingMargin != null ? operatingMargin.doubleValue() : null)
                .netMargin(netMargin != null ? netMargin.doubleValue() : null)
                .roe(roe != null ? roe.doubleValue() : null)
                .per(per != null ? per.doubleValue() : null)
                .pbr(pbr != null ? pbr.doubleValue() : null)
                .debtRatio(debtRatio)
                .build();
    }
}
