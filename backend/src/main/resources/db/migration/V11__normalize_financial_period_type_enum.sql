-- Normalize financial.period_type so schema validation is stable across environments.
-- Target type: enum('q','h','a','ttm')
SET @current_column_type := (
    SELECT COLUMN_TYPE
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'financial'
      AND COLUMN_NAME = 'period_type'
    LIMIT 1
);

SET @alter_sql := IF(
    @current_column_type = 'enum(''q'',''h'',''a'',''ttm'')',
    'SELECT 1',
    'ALTER TABLE financial MODIFY COLUMN period_type ENUM(''q'',''h'',''a'',''ttm'') NOT NULL'
);

PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
