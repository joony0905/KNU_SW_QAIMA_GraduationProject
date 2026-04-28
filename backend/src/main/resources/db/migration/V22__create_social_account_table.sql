CREATE TABLE social_account (
    social_account_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255) NULL,
    provider_name VARCHAR(100) NULL,
    linked_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (social_account_id),
    CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_social_account_user_id
    ON social_account (user_id);
