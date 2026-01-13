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
    private Integer half;

    private String periodType;   // "A"(연간), "Q"(분기), "H"(반기), "TTM"(최근 12개월)
    private Integer periodNo;    // Q:1~4, H:1~2, A/TTM:0(or null)
    private LocalDate reportDate; // 보고서 기준일

    // 규모(원 단위) 지표들
    private BigDecimal revenue;             // 매출액
    private BigDecimal grossProfit;         // 매출총이익 = 매출액 - 매출원가
    private BigDecimal operatingIncome;     // 영업이익
    private BigDecimal netIncome;           // 당기순이익

    private BigDecimal assets;              // 자산총계
    private BigDecimal liabilities;         // 부채총계
    private BigDecimal equity;              // 자본총계/순자산 = 자산 - 부채

    private BigDecimal capitalStock;        // 자본금 추가
    private BigDecimal retainedEarnings;    // 이익잉여금 추가
    private BigDecimal cashAndEquivalents;  // 현금 및 현금성자산 추가

    private BigDecimal marketCap;           // 시가총액, 기준일 주가 * 상장주식수 (외부 시세, 발행주식수 필요)

    // 비율 지표들 (%)
    private Double operatingMargin; // 영업이익률(%) = operatingIncome / revenue * 100
    private Double netMargin;       // 순이익률(%) = netIncome / revenue * 100
    private Double roe;             // ROE(%) = netIncome / equity * 100  (기간 정합성 주의: 연간/TTM 기준 권장)
    private Double per;             // PER = marketCap / netIncome (또는 주가/주당순이익)  (기간 정합성 주의)
    private Double pbr;             // PBR = marketCap / equity (또는 주가/주당순자산)
    private Double debtRatio;       // 부채비율(%) = liabilities / equity * 100  (equity=0 또는 null 방어 필요)
}
