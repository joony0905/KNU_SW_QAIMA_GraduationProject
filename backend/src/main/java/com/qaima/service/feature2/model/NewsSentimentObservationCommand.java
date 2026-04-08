package com.qaima.service.feature2.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NewsSentimentObservationCommand(
        Long newsId,
        String stockCode,
        String title,
        String url,
        String publisher,
        OffsetDateTime publishedAt,
        String focusText,
        BigDecimal predictedScore,
        String modelVersion,
        String promptVersion,
        String focusTextVersion
) {
}
