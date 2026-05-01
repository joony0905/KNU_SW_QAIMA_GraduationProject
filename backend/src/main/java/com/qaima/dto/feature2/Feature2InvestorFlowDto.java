package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class Feature2InvestorFlowDto {

    private String stockCode;
    private String marketCode;
    private InvestorFlowSummary stockSummary;
    private InvestorFlowSummary marketSummary;
    private List<InvestorFlowPoint> stockSeries;
    private List<InvestorFlowPoint> marketSeries;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class InvestorFlowSummary {
        private Integer window;
        private Integer pointCount;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal foreignNetBuyValueMillionSum;
        private BigDecimal institutionNetBuyValueMillionSum;
        private BigDecimal combinedNetBuyValueMillionSum;
        private BigDecimal foreignNetBuyQtySum;
        private BigDecimal institutionNetBuyQtySum;
        private BigDecimal combinedNetBuyQtySum;
        private String direction;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class InvestorFlowPoint {
        private LocalDate tradeDate;
        private BigDecimal closePrice;
        private BigDecimal foreignNetBuyQty;
        private BigDecimal foreignNetBuyValueMillion;
        private BigDecimal institutionNetBuyQty;
        private BigDecimal institutionNetBuyValueMillion;
        private BigDecimal individualNetBuyQty;
        private BigDecimal individualNetBuyValueMillion;
    }
}
