CREATE INDEX idx_sec_13f_holding_aggregate
    ON sec_13f_holding (stock_id, report_period, manager_cik, filing_date, accession_number);
