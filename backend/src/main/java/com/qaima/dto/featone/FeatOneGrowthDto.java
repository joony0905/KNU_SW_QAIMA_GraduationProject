package com.qaima.dto.featone;

import java.math.BigDecimal;
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
public class FeatOneGrowthDto {
    private Double revenueGrowth;
    private Double epsGrowth;
    private BigDecimal freeCashFlow;
}
