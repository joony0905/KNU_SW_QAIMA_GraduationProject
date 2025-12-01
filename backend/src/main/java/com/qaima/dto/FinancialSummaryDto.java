package com.qaima.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSummaryDto {

    private Integer fiscalYear;
    private Integer fiscalQuarter;
    private String periodType;   // "Q" / "A" / "TTM"

    private LocalDate reportDate;

    private BigDecimal revenue;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;

    private BigDecimal assets;
    private BigDecimal equity;
    private BigDecimal liabilities;

    private BigDecimal roe;
    private BigDecimal per;
    private BigDecimal pbr;
}
