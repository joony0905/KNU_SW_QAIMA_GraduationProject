CREATE TABLE news_sentiment_observation (
    observation_id BIGINT NOT NULL AUTO_INCREMENT,
    news_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(1000) NULL,
    publisher VARCHAR(100) NULL,
    published_at DATETIME(6) NULL,
    focus_text LONGTEXT NOT NULL,
    predicted_score DECIMAL(10,6) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    focus_text_version VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (observation_id),
    UNIQUE KEY uk_news_sentiment_observation (
        news_id,
        stock_code,
        model_version,
        prompt_version,
        focus_text_version
    ),
    KEY idx_news_sentiment_observation_created_at (created_at),
    CONSTRAINT fk_news_sentiment_observation_news FOREIGN KEY (news_id) REFERENCES news (news_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
