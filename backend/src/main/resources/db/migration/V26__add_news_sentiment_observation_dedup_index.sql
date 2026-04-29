CREATE INDEX idx_news_sentiment_observation_dedup_news
    ON news_sentiment_observation (
        stock_code,
        model_version,
        input_format_version,
        published_at,
        title(191)
    );
