package com.qaima.dto.feature2;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Feature2AnalyzeResponseDto {
    private final Feature2MetricsDto metrics; // 항상 존재(정책)
    private final String explain;             // optional
    private final Feature2MetaDto meta;       // warnings
}