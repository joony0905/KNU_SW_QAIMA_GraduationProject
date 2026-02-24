CREATE TABLE watchlist (
    watchlist_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_pref VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (watchlist_id),
    UNIQUE KEY uk_user_watchlist_name (user_id, name),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE watchlist_item (
    watchlist_item_id BIGINT NOT NULL AUTO_INCREMENT,
    watchlist_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    position INT NULL,
    note VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (watchlist_item_id),
    UNIQUE KEY uk_watchlist_stock (watchlist_id, stock_id),
    CONSTRAINT fk_watchlist_item_watchlist FOREIGN KEY (watchlist_id) REFERENCES watchlist (watchlist_id),
    CONSTRAINT fk_watchlist_item_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
