CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NULL,
    phone VARCHAR(30) NULL,
    experience VARCHAR(20) NULL,
    birthdate VARCHAR(6) NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    email_verified BIT(1) NOT NULL,
    email_verified_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    last_login_ip VARCHAR(45) NULL,
    locked_until DATETIME(6) NULL,
    glossary_hover BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token_hash (token_hash),
    KEY idx_evt_user (user_id),
    KEY idx_evt_expires (expires_at),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pwd_reset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pwd_reset_token_hash (token_hash),
    KEY idx_prt_user (user_id),
    KEY idx_prt_expires (expires_at),
    CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE login_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(100) NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    refresh_token_hash VARCHAR(200) NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_login_session_refresh_token_hash (refresh_token_hash),
    CONSTRAINT fk_login_session_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    success BIT(1) NOT NULL,
    email VARCHAR(190) NULL,
    user_id BIGINT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    error_code VARCHAR(64) NULL,
    message VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_login_occurred_at (occurred_at),
    KEY idx_login_user_id (user_id),
    KEY idx_login_email (email),
    KEY idx_login_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
