CREATE TABLE dictionary (
    term VARCHAR(255) NOT NULL,
    initial VARCHAR(2) NOT NULL,
    description TINYTEXT NOT NULL,
    source VARCHAR(255) NULL,
    tag VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (term),
    KEY idx_dictionary_initial_term (initial, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
