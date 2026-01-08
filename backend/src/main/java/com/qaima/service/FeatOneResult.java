package com.qaima.service;

import com.qaima.dto.FeatOneResponseDataDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeatOneResult {
    private final FeatOneResponseDataDto data;
    private final boolean chartUnavailable;
}
