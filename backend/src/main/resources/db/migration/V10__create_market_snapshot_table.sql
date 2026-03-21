CREATE TABLE market_snapshot (
    snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    as_of_date DATE NOT NULL,
    market_cap DECIMAL(20,0) NULL,
    per DECIMAL(10,4) NULL,
    pbr DECIMAL(10,4) NULL,
    shares_outstanding DECIMAL(20,0) NULL,
    source VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_market_snapshot_stock_date (stock_id, as_of_date),
    KEY idx_market_snapshot_stock_date (stock_id, as_of_date),
    CONSTRAINT fk_market_snapshot_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
