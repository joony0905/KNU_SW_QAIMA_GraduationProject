ALTER TABLE stock
    ADD COLUMN dart_corp_code VARCHAR(8) NULL AFTER currency,
    ADD COLUMN dart_corp_name VARCHAR(255) NULL AFTER dart_corp_code,
    ADD COLUMN dart_modified_date DATE NULL AFTER dart_corp_name,
    ADD COLUMN dart_synced_at DATETIME(6) NULL AFTER dart_modified_date,
    ADD KEY idx_stock_dart_corp_code (dart_corp_code),
    ADD KEY idx_stock_dart_modified_date (dart_modified_date);
