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
public class FeatOneStabilityDto {
    private Double debtRatio;
    private Double currentRatio;
    private Double quickRatio;
    private Double interestCoverageRatio;
}
