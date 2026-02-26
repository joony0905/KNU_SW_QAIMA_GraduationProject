-- V1__stock_meta.sql
-- Core tables: exchange, sector, industry, stock
-- 기준:
-- - Exchange: 단일 진실원
-- - Sector/Industry: KRX 지수 업종 체계 기반 (scheme + code canonical)
-- - Stock: industry_id FK만 사용 (code 조인 금지)

-- =========================
-- Exchange
-- =========================
CREATE TABLE exchange (
                          exchange_id BIGINT NOT NULL AUTO_INCREMENT,
                          code        VARCHAR(20)  NOT NULL,
                          name        VARCHAR(100) NOT NULL,
                          timezone    VARCHAR(50)  NULL,
                          country     VARCHAR(2)   NULL,
                          PRIMARY KEY (exchange_id),
                          UNIQUE KEY uk_exchange_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Sector (KRX_BZTP_M)
-- =========================
CREATE TABLE sector (
                        sector_id BIGINT NOT NULL AUTO_INCREMENT,
                        scheme    VARCHAR(30)  NOT NULL,   -- e.g. KRX_BZTP_M
                        code      VARCHAR(50)  NOT NULL,   -- idx_bztp_mcls_cd
                        name      VARCHAR(100) NOT NULL,   -- idx_bztp_mcls_cd_name
                        PRIMARY KEY (sector_id),
                        UNIQUE KEY uk_sector_scheme_code (scheme, code),
                        UNIQUE KEY uk_sector_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Industry (KRX_BZTP_S)
-- =========================
CREATE TABLE industry (
                          industry_id BIGINT NOT NULL AUTO_INCREMENT,
                          sector_id   BIGINT NULL,
                          scheme      VARCHAR(30)  NOT NULL, -- e.g. KRX_BZTP_S
                          code        VARCHAR(50)  NOT NULL, -- idx_bztp_scls_cd
                          name        VARCHAR(100) NOT NULL, -- idx_bztp_scls_cd_name
                          PRIMARY KEY (industry_id),
                          UNIQUE KEY uk_industry_scheme_code (scheme, code),
                          UNIQUE KEY uk_industry_name (name),
                          KEY idx_industry_sector_id (sector_id),
                          CONSTRAINT fk_industry_sector
                              FOREIGN KEY (sector_id) REFERENCES sector(sector_id)
                                  ON DELETE SET NULL
                                  ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Stock
-- =========================
CREATE TABLE stock (
                       stock_id     BIGINT NOT NULL AUTO_INCREMENT,
                       exchange_id  BIGINT NOT NULL,
                       stock_code   VARCHAR(32)  NOT NULL, -- 005930
                       isin         VARCHAR(20)  NULL,
                       company_name VARCHAR(255) NOT NULL,
                       industry_id  BIGINT NULL,
                       asset_type   VARCHAR(20)  NULL,
                       currency     VARCHAR(3)   NULL DEFAULT 'KRW',
                       listed_at    DATE NULL,
                       delisted_at  DATE NULL,
                       PRIMARY KEY (stock_id),
                       UNIQUE KEY uk_exchange_stock_code (exchange_id, stock_code),
                       KEY idx_stock_exchange_id (exchange_id),
                       KEY idx_stock_industry_id (industry_id),
                       CONSTRAINT fk_stock_exchange
                           FOREIGN KEY (exchange_id) REFERENCES exchange(exchange_id)
                               ON DELETE RESTRICT
                               ON UPDATE CASCADE,
                       CONSTRAINT fk_stock_industry
                           FOREIGN KEY (industry_id) REFERENCES industry(industry_id)
                               ON DELETE SET NULL
                               ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;