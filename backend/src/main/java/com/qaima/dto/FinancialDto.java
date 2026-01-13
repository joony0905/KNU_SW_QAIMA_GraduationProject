package com.qaima.dto;

import lombok.*;
import java.time.LocalDate;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDto {

    private Long financialId;
    private Long stockId;
    private String ticker;
    private String companyName;

    private Integer year;
    private Integer quarter;
    private Integer Half;
    private String periodType; // "A", "Q", "H", "TTM"
    private Integer periodNo;
    private LocalDate reportDate;

    // 규모(원 단위) 지표들
    private BigDecimal revenue;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal assets;
    private BigDecimal liabilities;
    private BigDecimal equity;
    private BigDecimal capitalStock;
    private BigDecimal marketCap;

    // 비율 지표들 (%)
    private Double operatingMargin;  // 영업이익률
    private Double netMargin;        // 순이익률
    private Double roe;              // 자기자본이익률
    private Double per;
    private Double pbr;
    private Double debtRatio;        // 부채비율 (liabilities / equity * 100)
}
