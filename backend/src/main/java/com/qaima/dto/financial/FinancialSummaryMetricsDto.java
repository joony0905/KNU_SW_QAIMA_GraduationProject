package com.qaima.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSummaryMetricsDto {
    private List<Integer> years;
    private Map<Integer, BigDecimal> revenue;
    private Map<Integer, BigDecimal> operatingIncome;
    private Map<Integer, BigDecimal> netIncome;
}
