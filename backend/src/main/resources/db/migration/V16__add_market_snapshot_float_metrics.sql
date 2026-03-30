ALTER TABLE market_snapshot
    ADD COLUMN float_market_cap DECIMAL(20,0) NULL AFTER market_cap,
    ADD COLUMN float_ratio DECIMAL(10,4) NULL AFTER pbr,
    ADD COLUMN treasury_ratio DECIMAL(10,4) NULL AFTER float_ratio;
