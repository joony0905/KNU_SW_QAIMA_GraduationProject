-- V1__stock_meta.sql
-- Core tables: exchange, sector, industry, stock

CREATE TABLE IF NOT EXISTS exchange (
                                        exchange_id BIGINT NOT NULL AUTO_INCREMENT,
                                        code        VARCHAR(20)  NOT NULL,
                                        name        VARCHAR(100) NOT NULL,
                                        timezone    VARCHAR(50)  NULL,
                                        country     VARCHAR(2)   NULL,
                                        PRIMARY KEY (exchange_id),
                                        UNIQUE KEY uk_exchange_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sector (
                                      sector_id BIGINT NOT NULL AUTO_INCREMENT,
                                      name      VARCHAR(100) NOT NULL,
                                      PRIMARY KEY (sector_id),
                                      UNIQUE KEY uk_sector_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS industry (
                                        industry_id BIGINT NOT NULL AUTO_INCREMENT,
                                        sector_id   BIGINT NULL,
                                        name        VARCHAR(100) NOT NULL,
                                        code        VARCHAR(50)  NULL,
                                        PRIMARY KEY (industry_id),
                                        UNIQUE KEY uk_industry_name (name),
                                        KEY idx_industry_sector_id (sector_id),
                                        CONSTRAINT fk_industry_sector
                                            FOREIGN KEY (sector_id) REFERENCES sector(sector_id)
                                                ON DELETE SET NULL
                                                ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock (
                                     stock_id     BIGINT NOT NULL AUTO_INCREMENT,
                                     exchange_id  BIGINT NOT NULL,
                                     stock_code   VARCHAR(32)  NOT NULL,
                                     isin         VARCHAR(20)  NULL,
                                     company_name VARCHAR(255) NOT NULL,
                                     industry_id  BIGINT NULL,
                                     asset_type   VARCHAR(20) NULL,
                                     currency     VARCHAR(3)  NULL DEFAULT 'KRW',
                                     listed_at    DATE NULL,
                                     delisted_at  DATE NULL,
                                     PRIMARY KEY (stock_id),
                                     KEY idx_stock_exchange_id (exchange_id),
                                     KEY idx_stock_industry_id (industry_id),
                                     CONSTRAINT uk_exchange_stock_code
                                         UNIQUE (exchange_id, stock_code),
                                     CONSTRAINT fk_stock_exchange
                                         FOREIGN KEY (exchange_id) REFERENCES exchange(exchange_id)
                                             ON DELETE RESTRICT
                                             ON UPDATE CASCADE,
                                     CONSTRAINT fk_stock_industry
                                         FOREIGN KEY (industry_id) REFERENCES industry(industry_id)
                                             ON DELETE SET NULL
                                             ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;