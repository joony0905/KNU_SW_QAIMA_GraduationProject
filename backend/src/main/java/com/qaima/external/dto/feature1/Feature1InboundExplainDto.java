package com.qaima.external.dto.feature1;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.common.AnalysisExplainDto;
import java.util.List;
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
public class Feature1InboundExplainDto {
    private String provider;
    private String model;
    private String text;
    private Feature1InboundExplainSectionsDto sections;
    private Feature1InboundExplainOverallDto overall;
    private List<AnalysisExplainDto.Warning> warnings;
}
