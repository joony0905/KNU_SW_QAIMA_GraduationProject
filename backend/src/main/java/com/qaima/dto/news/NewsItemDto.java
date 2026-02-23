package com.qaima.dto.news;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class NewsItemDto {
    private final Long newsId;
    private final String title;
    private final String url;
    private final OffsetDateTime publishedAt;
    private final String source;
    private final String lang;
    private final String summary;
}
