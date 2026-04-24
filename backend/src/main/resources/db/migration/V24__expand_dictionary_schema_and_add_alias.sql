ALTER TABLE dictionary
    ADD COLUMN source_org VARCHAR(100) NULL AFTER source,
    ADD COLUMN source_url VARCHAR(500) NULL AFTER source_org,
    ADD COLUMN source_type VARCHAR(50) NULL AFTER source_url,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED' AFTER source_type,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER status;

CREATE TABLE dictionary_alias (
    alias_id BIGINT NOT NULL AUTO_INCREMENT,
    canonical_term VARCHAR(255) NOT NULL,
    alias_term VARCHAR(255) NOT NULL,
    normalized_alias_term VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NULL,
    notes VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (alias_id),
    CONSTRAINT fk_dictionary_alias_canonical_term
        FOREIGN KEY (canonical_term) REFERENCES dictionary (term)
        ON DELETE CASCADE,
    CONSTRAINT uk_dictionary_alias_normalized UNIQUE (normalized_alias_term),
    KEY idx_dictionary_alias_canonical_term (canonical_term),
    KEY idx_dictionary_alias_alias_term (alias_term),
    KEY idx_dictionary_alias_normalized (normalized_alias_term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
