package com.qaima.dto.feature2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class Feature2AnalyzeResponseDto {
    private final Feature2MetricsDto metrics; // 항상 존재(정책)
    private final String explain;             // optional
    @JsonIgnore
    private final List<String> warnings;      // envelope root meta로 승격할 경고
}
