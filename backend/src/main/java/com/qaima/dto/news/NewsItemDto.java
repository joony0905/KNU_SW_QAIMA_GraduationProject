package com.qaima.dto.news;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NewsItemDto {
    private final Long newsId;
    private final String title;
    private final String url;
    private final OffsetDateTime publishedAt;
    private final String source;
    private final String lang;
    private final String summary;
}
