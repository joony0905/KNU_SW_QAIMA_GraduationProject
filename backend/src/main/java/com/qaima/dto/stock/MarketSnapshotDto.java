package com.qaima.dto.stock;

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
public class MarketSnapshotDto {

    // 스냅샷 기준일
    private LocalDate asOfDate;
    // 시가총액 = 현재가 × 발행주식수
    private BigDecimal marketCap;
    // 유통시가총액 = 현재가 × 유통주식수
    private BigDecimal floatMarketCap;
    // 주가수익비율
    private Double per;
    // 주가순자산비율
    private Double pbr;
    // 주가매출비율
    private Double psr;
    // 유통주식수 / 발행주식수
    private Double floatRatio;
    // 자기주식수 / 발행주식수
    private Double treasuryRatio;
    // 계산에 사용한 기준 발행주식수
    private BigDecimal sharesOutstanding;
    // 최근 12개월 기준 주당순이익
    private BigDecimal epsTtm;
    // 주당순자산
    private BigDecimal bps;
    // 주당매출
    private BigDecimal sps;
    private Double roe;
    private Double roa;
    private Double operatingMargin;
    private Double netMargin;
    private Double debtRatio;
    private Double currentRatio;
    private Double quickRatio;
    private Double interestCoverageRatio;
    private BigDecimal freeCashFlow;
    private Double revenueGrowth;
    private Double epsGrowth;
    // 값 산출 기준 출처
    private String source;
}
