ALTER TABLE users
    MODIFY COLUMN credit_balance BIGINT NOT NULL DEFAULT 5;

UPDATE users
SET credit_balance = 5
WHERE credit_balance = 0;
