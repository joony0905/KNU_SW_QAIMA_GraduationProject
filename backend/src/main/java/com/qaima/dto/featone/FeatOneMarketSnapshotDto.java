package com.qaima.dto.featone;

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
public class FeatOneMarketSnapshotDto {
    private String asOf;
    private String currency;
    private FeatOneValuationDto valuation;
    private FeatOneProfitabilityDto profitability;
    private FeatOneStabilityDto stability;
    private FeatOneGrowthDto growth;
    private FeatOnePerShareDto perShare;
}
