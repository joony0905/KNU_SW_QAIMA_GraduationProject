ALTER TABLE news_sentiment_observation
    ADD COLUMN predicted_label VARCHAR(20) NULL AFTER predicted_score,
    ADD COLUMN negative_prob DECIMAL(10, 6) NULL AFTER predicted_label,
    ADD COLUMN neutral_prob DECIMAL(10, 6) NULL AFTER negative_prob,
    ADD COLUMN positive_prob DECIMAL(10, 6) NULL AFTER neutral_prob,
    ADD COLUMN input_format_version VARCHAR(100) NULL AFTER focus_text_version;

CREATE INDEX idx_news_sentiment_observation_model_format_created_at
    ON news_sentiment_observation (model_version, input_format_version, created_at);
