package com.qaima.service.feature2.model;

import com.qaima.domain.Industry;
import com.qaima.dto.industry.IndustryMetaDto;

public record Feature2IndustryContext(
        Industry industry,
        IndustryMetaDto industryMeta
) {
}
