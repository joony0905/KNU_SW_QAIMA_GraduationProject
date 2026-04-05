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
public class FeatOneProfitabilityDto {
    private Double roe;
    private Double roa;
    private Double operatingMargin;
    private Double netMargin;
}
