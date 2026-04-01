package com.qaima.dto.news;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class NewsItemDto {
    private final Long newsId;
    private final String title;
    private final String url;
    private final String publisher;
    private final OffsetDateTime publishedAt;
    private final String summary;
    private final BigDecimal sentimentScore;
}
