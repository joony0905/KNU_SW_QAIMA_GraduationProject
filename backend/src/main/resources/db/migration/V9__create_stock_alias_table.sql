CREATE TABLE stock_alias (
    alias_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    alias_name VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (alias_id),
    UNIQUE KEY uk_stock_alias_stock_norm (stock_id, normalized_alias),
    KEY idx_stock_alias_norm (normalized_alias),
    KEY idx_stock_alias_stock (stock_id),
    CONSTRAINT fk_stock_alias_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
