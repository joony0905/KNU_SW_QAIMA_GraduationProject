ALTER TABLE stock
    ADD COLUMN share_class VARCHAR(20) NOT NULL DEFAULT 'OTHER' AFTER currency;

CREATE INDEX idx_stock_share_class ON stock (share_class);
