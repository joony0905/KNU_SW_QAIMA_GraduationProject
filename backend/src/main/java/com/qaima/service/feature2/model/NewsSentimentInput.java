package com.qaima.service.feature2.model;

import java.time.OffsetDateTime;

public record NewsSentimentInput(
        String url,
        String title,
        String publisher,
        OffsetDateTime publishedAt,
        String focusText
) {
}
