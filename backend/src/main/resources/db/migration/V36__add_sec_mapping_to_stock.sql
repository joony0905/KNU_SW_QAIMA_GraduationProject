ALTER TABLE stock
    ADD COLUMN sec_cik VARCHAR(10) NULL AFTER dart_synced_at,
    ADD COLUMN sec_company_name VARCHAR(255) NULL AFTER sec_cik,
    ADD COLUMN sec_synced_at DATETIME(6) NULL AFTER sec_company_name,
    ADD KEY idx_stock_sec_cik (sec_cik),
    ADD KEY idx_stock_sec_synced_at (sec_synced_at);
