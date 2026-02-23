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
public class FeatOneAnalysisResponseDto {
    private FeatOneAnalysisMetricsDto metrics;
    private FeatOneAnalysisExplainDto explain;
    private FeatOneAnalysisMetaDto meta;
}
