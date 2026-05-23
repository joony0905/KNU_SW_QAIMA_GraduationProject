CREATE TABLE portfolio (
    portfolio_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cash_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (portfolio_id),
    UNIQUE KEY uk_portfolio_user (user_id),
    CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE portfolio_holding (
    portfolio_holding_id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_id BIGINT NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(255) NOT NULL,
    quantity DECIMAL(24, 6) NOT NULL,
    average_price DECIMAL(24, 6) NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (portfolio_holding_id),
    KEY idx_portfolio_holding_portfolio (portfolio_id),
    CONSTRAINT fk_portfolio_holding_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
