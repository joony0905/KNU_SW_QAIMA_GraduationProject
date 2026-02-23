package com.qaima.dto.feature2;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Feature2AnalyzeRequestDto {
    private final String stockCode;
}
