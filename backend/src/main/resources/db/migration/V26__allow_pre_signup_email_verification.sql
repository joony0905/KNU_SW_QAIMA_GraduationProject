ALTER TABLE email_verification
    ADD COLUMN email VARCHAR(255) NULL AFTER user_id;

UPDATE email_verification ev
JOIN users u ON u.user_id = ev.user_id
SET ev.email = LOWER(TRIM(u.email))
WHERE ev.email IS NULL;

ALTER TABLE email_verification
    MODIFY user_id BIGINT NULL,
    MODIFY email VARCHAR(255) NOT NULL;

CREATE INDEX idx_evt_email_created
    ON email_verification (email, created_at);
