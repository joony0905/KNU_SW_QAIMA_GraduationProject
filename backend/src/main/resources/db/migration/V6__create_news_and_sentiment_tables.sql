CREATE TABLE news (
    news_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(1000) NULL,
    published_at DATETIME(6) NOT NULL,
    source VARCHAR(100) NULL,
    lang VARCHAR(10) NULL,
    summary TEXT NULL,
    content_uri VARCHAR(1000) NULL,
    PRIMARY KEY (news_id),
    UNIQUE KEY uk_news_url (url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE news_security_map (
    news_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    PRIMARY KEY (news_id, stock_id),
    UNIQUE KEY uk_news_security_map (news_id, stock_id),
    CONSTRAINT fk_news_security_map_news FOREIGN KEY (news_id) REFERENCES news (news_id),
    CONSTRAINT fk_news_security_map_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sentiment_result (
    news_id BIGINT NOT NULL,
    model VARCHAR(255) NOT NULL,
    score DECIMAL(10,6) NOT NULL,
    label VARCHAR(10) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (news_id, model),
    UNIQUE KEY uk_sentiment_result (news_id, model),
    CONSTRAINT fk_sentiment_result_news FOREIGN KEY (news_id) REFERENCES news (news_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sentiment_daily_agg (
    date DATE NOT NULL,
    scope VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    stock_id BIGINT NOT NULL,
    industry_id BIGINT NOT NULL,
    cnt_total INT NULL,
    cnt_pos INT NULL,
    cnt_neg INT NULL,
    cnt_neu INT NULL,
    score_avg DECIMAL(10,6) NULL,
    score_std DECIMAL(10,6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (date, scope, model, stock_id, industry_id),
    CONSTRAINT fk_sentiment_daily_agg_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id),
    CONSTRAINT fk_sentiment_daily_agg_industry FOREIGN KEY (industry_id) REFERENCES industry (industry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
