CREATE TABLE base_rate (
    base_rate_id BIGINT NOT NULL AUTO_INCREMENT,
    base_date DATE NOT NULL,
    raw_time VARCHAR(16) NOT NULL,
    cycle VARCHAR(1) NOT NULL,
    rate_value DECIMAL(10,6) NOT NULL,
    unit_name VARCHAR(20) NOT NULL,
    stat_code VARCHAR(20) NOT NULL,
    stat_name VARCHAR(255) NOT NULL,
    item_code VARCHAR(20) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'BOK_ECOS',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (base_rate_id),
    UNIQUE KEY uk_base_rate_cycle_time_stat_item (cycle, raw_time, stat_code, item_code),
    KEY idx_base_rate_base_date (base_date),
    KEY idx_base_rate_stat_item_base_date (stat_code, item_code, base_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
