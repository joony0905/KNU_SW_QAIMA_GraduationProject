package com.qaima.dto.featone;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FeatOneFinancialPointDto {
    private Integer fiscalYear;
    private Integer fiscalQuarter;
    private Integer fiscalHalf;
    private Integer periodNo;
    private String periodType;
    private LocalDate reportDate;
    private BigDecimal revenue;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal assets;
    private BigDecimal liabilities;
    private BigDecimal equity;
    private BigDecimal currentAssets;
    private BigDecimal currentLiabilities;
    private BigDecimal inventories;
    private BigDecimal interestExpense;
    private BigDecimal operatingCashFlow;
    private BigDecimal capex;
}
