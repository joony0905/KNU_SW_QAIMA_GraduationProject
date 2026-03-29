package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature2AnalyzeResponseDto {
    private final Feature2MetricsDto metrics; // 항상 존재(정책)
    private final String explain;             // optional
    private final Feature2MetaDto meta;       // warnings
}
