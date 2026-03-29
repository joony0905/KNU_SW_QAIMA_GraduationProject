package com.qaima.external.dto.feature1;

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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature1InboundFinancialSummaryDto {
    private List<Integer> years;
    private Map<Integer, BigDecimal> revenue;
    private Map<Integer, BigDecimal> operatingIncome;
    private Map<Integer, BigDecimal> netIncome;
}
