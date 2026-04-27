ALTER TABLE users
    ADD COLUMN credit_balance BIGINT NOT NULL DEFAULT 0 AFTER glossary_hover;

CREATE TABLE user_credit_ledger (
    ledger_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    reason VARCHAR(100) NULL,
    reference_type VARCHAR(30) NULL,
    reference_id VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ledger_id),
    KEY idx_user_credit_ledger_user_created (user_id, created_at),
    KEY idx_user_credit_ledger_reference (reference_type, reference_id),
    CONSTRAINT fk_user_credit_ledger_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
