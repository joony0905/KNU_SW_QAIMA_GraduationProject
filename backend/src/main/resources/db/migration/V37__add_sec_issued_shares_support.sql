SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE stock ADD COLUMN sec_issued_shares_synced_at DATETIME(6) NULL AFTER sec_synced_at',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'stock'
      AND column_name = 'sec_issued_shares_synced_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE stock ADD KEY idx_stock_sec_issued_shares_synced_at (sec_issued_shares_synced_at)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'stock'
      AND index_name = 'idx_stock_sec_issued_shares_synced_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE issued_shares
    MODIFY rcept_no VARCHAR(32) NOT NULL,
    MODIFY corp_code VARCHAR(10) NOT NULL,
    MODIFY corp_cls VARCHAR(10) NULL;
