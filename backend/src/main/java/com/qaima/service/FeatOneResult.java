package com.qaima.service;

import com.qaima.dto.FeatOneAnalysisResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeatOneResult {
    private final FeatOneAnalysisResponseDto data;
    private final boolean chartUnavailable;
}
