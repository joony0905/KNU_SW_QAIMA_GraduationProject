package com.qaima.dto.financial;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class FinancialSummaryMetricsDto {
    private List<Integer> years;
    private Map<Integer, BigDecimal> revenue;

    @JsonAlias("operating_income")
    private Map<Integer, BigDecimal> operatingIncome;

    @JsonAlias("net_income")
    private Map<Integer, BigDecimal> netIncome;
}
