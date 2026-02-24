-- V3__seed_exchange.sql
-- Minimal seed so StockService.createAndSaveStockFromMeta()가 exchangeRepository.findByCode(...)에 성공하도록

INSERT INTO exchange(code, name, timezone, country) VALUES
                                                        ('KOSPI',  'KOSPI',  'Asia/Seoul',      'KR'),
                                                        ('KOSDAQ', 'KOSDAQ', 'Asia/Seoul',      'KR'),
                                                        ('KONEX',  'KONEX',  'Asia/Seoul',      'KR'),
                                                        ('KRX',    'KRX',    'Asia/Seoul',      'KR'),
                                                        ('NASDAQ', 'NASDAQ', 'America/New_York','US'),
                                                        ('NYSE',   'NYSE',   'America/New_York','US')
ON DUPLICATE KEY UPDATE
                     name=VALUES(name),
                     timezone=VALUES(timezone),
                     country=VALUES(country);