CREATE TABLE financial (
    financial_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    version INT NOT NULL,
    fiscal_year INT NOT NULL,
    period_no INT NOT NULL,
    fiscal_quarter INT NULL,
    period_type VARCHAR(10) NOT NULL,
    filing_date DATE NULL,
    currency VARCHAR(10) NULL,
    source VARCHAR(50) NULL,
    revenue DECIMAL(20,2) NULL,
    gross_profit DECIMAL(20,2) NULL,
    operating_income DECIMAL(20,2) NULL,
    net_income DECIMAL(20,2) NULL,
    assets DECIMAL(20,2) NULL,
    liabilities DECIMAL(20,2) NULL,
    equity DECIMAL(20,2) NULL,
    capital_stock DECIMAL(20,2) NULL,
    retained_earnings DECIMAL(20,2) NULL,
    cash_and_equivalents DECIMAL(20,2) NULL,
    market_cap DECIMAL(20,2) NULL,
    operating_margin DECIMAL(10,4) NULL,
    net_margin DECIMAL(10,4) NULL,
    roe DECIMAL(10,4) NULL,
    per DECIMAL(10,4) NULL,
    pbr DECIMAL(10,4) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (financial_id),
    UNIQUE KEY uk_stock_fiscal_period (stock_id, fiscal_year, period_type, period_no),
    KEY idx_financials_stock_fiscal_period (stock_id, period_type, fiscal_year, period_no),
    CONSTRAINT fk_financial_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE price_ohlcv (
    stock_id BIGINT NOT NULL,
    ts DATETIME(6) NOT NULL,
    freq INT NOT NULL,
    open DECIMAL(18,6) NULL,
    high DECIMAL(18,6) NULL,
    low DECIMAL(18,6) NULL,
    close DECIMAL(18,6) NULL,
    volume DECIMAL(20,0) NULL,
    PRIMARY KEY (stock_id, ts, freq),
    CONSTRAINT fk_price_ohlcv_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE indicator_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    ts DATETIME(6) NOT NULL,
    freq VARCHAR(255) NOT NULL,
    indicator_key VARCHAR(50) NOT NULL,
    value_num DECIMAL(20,8) NULL,
    value_json VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_indicator_unique (stock_id, ts, freq, indicator_key),
    CONSTRAINT fk_indicator_value_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE industry_index (
    index_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NULL,
    currency VARCHAR(3) NULL,
    PRIMARY KEY (index_id),
    UNIQUE KEY uk_industry_index_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE industry_index_map (
    industry_id BIGINT NOT NULL,
    index_id BIGINT NOT NULL,
    PRIMARY KEY (industry_id, index_id),
    UNIQUE KEY uk_industry_index_map (industry_id, index_id),
    CONSTRAINT fk_industry_index_map_industry FOREIGN KEY (industry_id) REFERENCES industry (industry_id),
    CONSTRAINT fk_industry_index_map_index FOREIGN KEY (index_id) REFERENCES industry_index (index_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE industry_index_ohlcv (
    index_id BIGINT NOT NULL,
    ts DATETIME(6) NOT NULL,
    freq INT NOT NULL,
    open DECIMAL(18,6) NULL,
    high DECIMAL(18,6) NULL,
    low DECIMAL(18,6) NULL,
    close DECIMAL(18,6) NULL,
    volume DECIMAL(20,0) NULL,
    PRIMARY KEY (index_id, ts, freq),
    CONSTRAINT fk_industry_index_ohlcv_index FOREIGN KEY (index_id) REFERENCES industry_index (index_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_realtime_cache (
    stock_id BIGINT NOT NULL,
    ts DATETIME(6) NOT NULL,
    prev_close DECIMAL(18,6) NULL,
    last DECIMAL(18,6) NULL,
    change_ratio DECIMAL(10,6) NULL,
    volume DECIMAL(20,0) NULL,
    turnover DECIMAL(20,6) NULL,
    PRIMARY KEY (stock_id),
    CONSTRAINT fk_stock_realtime_cache_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE peer_cluster_cache (
    industry_id BIGINT NOT NULL,
    ts DATETIME(6) NOT NULL,
    method VARCHAR(255) NOT NULL,
    params_json VARCHAR(255) NULL,
    cluster_json VARCHAR(255) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (industry_id, ts, method),
    CONSTRAINT fk_peer_cluster_cache_industry FOREIGN KEY (industry_id) REFERENCES industry (industry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
