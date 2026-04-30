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
public class Feature2MacroRatesDto {

    private RatePoint krBaseRate;
    private RatePoint usFedFundsRate;
    private ExchangeRatePoint usdKrw;
    private List<BondYieldPoint> bondYields;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class RatePoint {
        private LocalDate date;
        private BigDecimal value;
        private String unit;
        private String source;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class ExchangeRatePoint {
        private LocalDate date;
        private String pairCode;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal value;
        private String unit;
        private String source;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class BondYieldPoint {
        private LocalDate date;
        private String countryCode;
        private String instrumentCode;
        private String instrumentName;
        private Integer maturityMonths;
        private BigDecimal value;
        private String unit;
        private String source;
    }
}
