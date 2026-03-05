package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IndustryIndexMetaDto {
    private final Long indexId;
    private final String name;
    private final String code;
    private final String provider;
    private final String currency;
}
