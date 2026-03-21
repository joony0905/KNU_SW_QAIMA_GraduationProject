package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IndustryMetaDto {
    private final Long industryId;
    private final String name;
    private final String code;
}
