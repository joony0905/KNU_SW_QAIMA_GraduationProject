package com.qaima.dto.financial;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Integer half;

    // A: 연간, Q: 분기, H: 반기, TTM: 최근 12개월
    private String periodType;
    private Integer periodNo;
    private LocalDate reportDate;

    // 절대값 재무 지표
    private BigDecimal revenue;              // 매출액
    private BigDecimal grossProfit;          // 매출총이익
    private BigDecimal operatingIncome;      // 영업이익
    private BigDecimal netIncome;            // 당기순이익
    private BigDecimal assets;               // 자산총계
    private BigDecimal liabilities;          // 부채총계
    private BigDecimal equity;               // 자본총계
    private BigDecimal capitalStock;         // 자본금
    private BigDecimal retainedEarnings;     // 이익잉여금
    private BigDecimal cashAndEquivalents;   // 현금 및 현금성자산

    // 시장 / 주당 지표
    private BigDecimal marketCap;            // 최신 market snapshot 기준 시가총액
    private BigDecimal eps;                  // 주당순이익 = netIncome / shares
    private BigDecimal bps;                  // 주당순자산 = equity / shares

    // 비율 지표
    private Double operatingMargin;          // 영업이익률
    private Double netMargin;                // 순이익률
    private Double roe;                      // 자기자본이익률
    private Double roa;                      // 총자산이익률 = netIncome / assets
    private Double per;                      // 주가수익비율
    private Double pbr;                      // 주가순자산비율
    private Double psr;                      // 주가매출비율 = marketCap / revenue
    private Double debtRatio;                // 부채비율 = liabilities / equity * 100
}
