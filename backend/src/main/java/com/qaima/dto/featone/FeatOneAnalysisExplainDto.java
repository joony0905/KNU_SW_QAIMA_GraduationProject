package com.qaima.dto.featone;

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
public class FeatOneAnalysisExplainDto {
    private String provider;
    private String model;
    private String text;
    private FeatOneAnalysisExplainSectionsDto sections;
    private FeatOneAnalysisExplainOverallDto overall;
    private List<AnalysisExplainDto.Warning> warnings;
}
