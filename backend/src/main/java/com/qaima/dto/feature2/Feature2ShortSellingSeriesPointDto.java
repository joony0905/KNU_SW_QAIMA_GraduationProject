package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class Feature2ShortSellingSeriesPointDto {

    private LocalDate reportDate;
    private BigDecimal shortVolumeRatio;
    private BigDecimal shortAmountRatio;
    private BigDecimal shortVolumeTotal;
    private BigDecimal totalVolume;
    private BigDecimal shortAmountTotal;
    private BigDecimal totalAmount;
}
