package com.qaima.service.featone;

import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeatOneResult {
    private final FeatOneAnalysisResponseDto data;
    private final boolean chartUnavailable;
}
