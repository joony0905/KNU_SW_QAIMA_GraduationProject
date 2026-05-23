package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.common.AnalysisExplainDto;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature2ExplainResponseDto {
    private AnalysisExplainDto explain;
    private List<String> warnings;
}
