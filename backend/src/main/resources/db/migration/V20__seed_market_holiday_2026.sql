-- =========================================================
-- market_holiday table 생성
-- =========================================================

CREATE TABLE IF NOT EXISTS market_holiday (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              market VARCHAR(20) NOT NULL,
                                              holiday_date DATE NOT NULL,
                                              name VARCHAR(100) NOT NULL,
                                              source VARCHAR(100) NOT NULL,
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                              CONSTRAINT pk_market_holiday PRIMARY KEY (id),
                                              CONSTRAINT uq_market_holiday UNIQUE (market, holiday_date)
);

-- 인덱스 (조회 성능용)
CREATE INDEX idx_market_holiday_market_date
    ON market_holiday (market, holiday_date);


-- =========================================================
-- 2026년 KRX 휴장일 seed 데이터
-- source: KRX_SCHEDULE_2026_MANUAL
-- 한국거래소 증시일정 기준
-- =========================================================

INSERT INTO market_holiday (market, holiday_date, name, source)
VALUES
    ('KRX', '2026-01-01', '신정', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-02-16', '설날연휴', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-02-17', '설날연휴', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-02-18', '설날연휴', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-03-02', '삼일절 대체공휴일', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-05-01', '근로자의날', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-05-05', '어린이날', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-05-25', '석가탄신일 대체공휴일', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-08-17', '광복절 대체공휴일', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-09-24', '추석연휴', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-09-25', '추석연휴', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-10-05', '개천절 대체공휴일', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-10-09', '한글날', 'KRX_SCHEDULE_2026_MANUAL'),

    ('KRX', '2026-12-25', '성탄절', 'KRX_SCHEDULE_2026_MANUAL'),
    ('KRX', '2026-12-31', '연말휴장일', 'KRX_SCHEDULE_2026_MANUAL')

-- 중복 방지 (MySQL)
ON DUPLICATE KEY UPDATE
                     name = VALUES(name),
                     source = VALUES(source);