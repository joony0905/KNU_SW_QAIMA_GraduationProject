package com.qaima.external.dto.feature2news;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Feature2NewsSentimentExternalEnvelopeDto {
    private Feature2NewsSentimentExternalMetaDto meta;
    private Feature2NewsSentimentExternalResponseDto data;
    private List<Object> errors;
}
