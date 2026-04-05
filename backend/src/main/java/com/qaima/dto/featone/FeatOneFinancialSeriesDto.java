package com.qaima.dto.featone;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
public class FeatOneFinancialSeriesDto {
    private List<Integer> years;
    private Map<Integer, BigDecimal> revenue;
    private Map<Integer, BigDecimal> operatingIncome;
    private Map<Integer, BigDecimal> netIncome;
}
