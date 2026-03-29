package com.qaima.service.feature2.model;

import java.math.BigDecimal;

public record NewsSentimentResult(
        String url,
        BigDecimal sentimentScore
) {
}
