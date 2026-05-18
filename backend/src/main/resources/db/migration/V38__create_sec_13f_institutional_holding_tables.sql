CREATE TABLE stock_security_identifier (
    stock_security_identifier_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    identifier_type VARCHAR(20) NOT NULL,
    identifier_value VARCHAR(32) NOT NULL,
    issuer_name VARCHAR(255) NULL,
    source VARCHAR(50) NOT NULL,
    confidence INT NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (stock_security_identifier_id),
    UNIQUE KEY uk_stock_security_identifier_stock_type_value (stock_id, identifier_type, identifier_value),
    KEY idx_stock_security_identifier_type_value (identifier_type, identifier_value),
    KEY idx_stock_security_identifier_stock (stock_id),
    CONSTRAINT fk_stock_security_identifier_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sec_13f_import_file (
    sec_13f_import_file_id BIGINT NOT NULL AUTO_INCREMENT,
    source_file VARCHAR(255) NOT NULL,
    source_path VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    submission_rows BIGINT NOT NULL DEFAULT 0,
    cover_page_rows BIGINT NOT NULL DEFAULT 0,
    info_table_rows BIGINT NOT NULL DEFAULT 0,
    matched_info_table_rows BIGINT NOT NULL DEFAULT 0,
    skipped_unmapped_rows BIGINT NOT NULL DEFAULT 0,
    skipped_derivative_rows BIGINT NOT NULL DEFAULT 0,
    skipped_non_share_rows BIGINT NOT NULL DEFAULT 0,
    parsed_holding_rows BIGINT NOT NULL DEFAULT 0,
    created_filings INT NOT NULL DEFAULT 0,
    updated_filings INT NOT NULL DEFAULT 0,
    created_holdings INT NOT NULL DEFAULT 0,
    updated_holdings INT NOT NULL DEFAULT 0,
    unchanged_holdings INT NOT NULL DEFAULT 0,
    aggregated_rows INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sec_13f_import_file_id),
    UNIQUE KEY uk_sec_13f_import_file_source_file (source_file),
    KEY idx_sec_13f_import_file_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sec_13f_filing (
    sec_13f_filing_id BIGINT NOT NULL AUTO_INCREMENT,
    accession_number VARCHAR(32) NOT NULL,
    manager_cik VARCHAR(10) NOT NULL,
    manager_name VARCHAR(255) NULL,
    filing_date DATE NOT NULL,
    report_period DATE NOT NULL,
    submission_type VARCHAR(20) NOT NULL,
    is_amendment BOOLEAN NOT NULL DEFAULT FALSE,
    amendment_no VARCHAR(16) NULL,
    amendment_type VARCHAR(50) NULL,
    source_file VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sec_13f_filing_id),
    UNIQUE KEY uk_sec_13f_filing_accession (accession_number),
    KEY idx_sec_13f_filing_manager_period (manager_cik, report_period),
    KEY idx_sec_13f_filing_report_period (report_period),
    KEY idx_sec_13f_filing_source_file (source_file)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sec_13f_holding (
    sec_13f_holding_id BIGINT NOT NULL AUTO_INCREMENT,
    sec_13f_filing_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    manager_cik VARCHAR(10) NOT NULL,
    report_period DATE NOT NULL,
    filing_date DATE NOT NULL,
    cusip VARCHAR(16) NOT NULL,
    name_of_issuer VARCHAR(255) NULL,
    title_of_class VARCHAR(150) NULL,
    filing_row_count INT NOT NULL DEFAULT 1,
    shares DECIMAL(30,0) NOT NULL,
    value_raw DECIMAL(30,0) NOT NULL,
    value_unit VARCHAR(20) NOT NULL,
    market_value_usd DECIMAL(30,0) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'SEC_13F_DATA_SET',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sec_13f_holding_id),
    UNIQUE KEY uk_sec_13f_holding_accession_stock_cusip (accession_number, stock_id, cusip),
    KEY idx_sec_13f_holding_stock_period (stock_id, report_period),
    KEY idx_sec_13f_holding_cusip_period (cusip, report_period),
    KEY idx_sec_13f_holding_manager_period (manager_cik, report_period),
    CONSTRAINT fk_sec_13f_holding_filing FOREIGN KEY (sec_13f_filing_id) REFERENCES sec_13f_filing (sec_13f_filing_id),
    CONSTRAINT fk_sec_13f_holding_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_institutional_holding_quarterly (
    stock_institutional_holding_quarterly_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id BIGINT NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    report_period DATE NOT NULL,
    cusip VARCHAR(16) NULL,
    institution_count INT NOT NULL DEFAULT 0,
    filing_row_count INT NOT NULL DEFAULT 0,
    shares_held DECIMAL(30,0) NOT NULL,
    shares_change DECIMAL(30,0) NULL,
    shares_change_rate DECIMAL(20,8) NULL,
    market_value_usd DECIMAL(30,0) NOT NULL,
    shares_outstanding DECIMAL(30,0) NULL,
    holding_ratio DECIMAL(20,8) NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'SEC_13F_DATA_SET',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (stock_institutional_holding_quarterly_id),
    UNIQUE KEY uk_stock_inst_holding_quarterly_stock_period_source (stock_id, report_period, source),
    KEY idx_stock_inst_holding_quarterly_code_period (stock_code, report_period),
    KEY idx_stock_inst_holding_quarterly_period (report_period),
    CONSTRAINT fk_stock_inst_holding_quarterly_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO stock_security_identifier (
    stock_id,
    identifier_type,
    identifier_value,
    issuer_name,
    source,
    confidence,
    active,
    created_at,
    updated_at
)
SELECT s.stock_id, 'CUSIP', '037833100', 'APPLE INC', 'SEC_13F_MANUAL_SEED', 100, TRUE, NOW(6), NOW(6)
FROM stock s
JOIN exchange e ON e.exchange_id = s.exchange_id
WHERE UPPER(s.stock_code) = 'AAPL'
  AND UPPER(e.code) IN ('NASDAQ', 'NYSE')
LIMIT 1
ON DUPLICATE KEY UPDATE
    issuer_name = VALUES(issuer_name),
    source = VALUES(source),
    confidence = VALUES(confidence),
    active = TRUE,
    updated_at = NOW(6);

INSERT INTO stock_security_identifier (
    stock_id,
    identifier_type,
    identifier_value,
    issuer_name,
    source,
    confidence,
    active,
    created_at,
    updated_at
)
SELECT s.stock_id, 'CUSIP', '594918104', 'MICROSOFT CORP', 'SEC_13F_MANUAL_SEED', 100, TRUE, NOW(6), NOW(6)
FROM stock s
JOIN exchange e ON e.exchange_id = s.exchange_id
WHERE UPPER(s.stock_code) = 'MSFT'
  AND UPPER(e.code) IN ('NASDAQ', 'NYSE')
LIMIT 1
ON DUPLICATE KEY UPDATE
    issuer_name = VALUES(issuer_name),
    source = VALUES(source),
    confidence = VALUES(confidence),
    active = TRUE,
    updated_at = NOW(6);

INSERT INTO stock_security_identifier (
    stock_id,
    identifier_type,
    identifier_value,
    issuer_name,
    source,
    confidence,
    active,
    created_at,
    updated_at
)
SELECT s.stock_id, 'CUSIP', '67066G104', 'NVIDIA CORPORATION', 'SEC_13F_MANUAL_SEED', 100, TRUE, NOW(6), NOW(6)
FROM stock s
JOIN exchange e ON e.exchange_id = s.exchange_id
WHERE UPPER(s.stock_code) = 'NVDA'
  AND UPPER(e.code) IN ('NASDAQ', 'NYSE')
LIMIT 1
ON DUPLICATE KEY UPDATE
    issuer_name = VALUES(issuer_name),
    source = VALUES(source),
    confidence = VALUES(confidence),
    active = TRUE,
    updated_at = NOW(6);
