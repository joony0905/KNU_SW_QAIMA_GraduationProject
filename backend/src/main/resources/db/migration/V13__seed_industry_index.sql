-- V13__seed_industry_index.sql
-- Feature2 산업지수용 KIS 업종지수 마스터/매핑 seed
-- 전제:
-- 1) industry 는 이미 적재되어 있음
-- 2) industry_index / industry_index_map 테이블은 이미 생성되어 있음
-- 3) MVP 기준: industry 1개당 대표 index 1개만 사용

-- -----------------------------------------
-- 1. industry_index seed
-- -----------------------------------------
INSERT INTO industry_index (name, code, provider, currency)
SELECT '제약', '00009', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00009'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '운송장비·부품', '00015', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00015'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '섬유·의류', '00006', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00006'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '음식료·담배', '00005', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00005'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '운송·창고', '00019', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00019'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '일반서비스', '00026', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00026'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '종이·목재', '00007', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00007'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '건설', '00018', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00018'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '화학', '00008', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00008'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '보험', '00025', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00025'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '비금속', '00010', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00010'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '기계·장비', '00012', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00012'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '전기·전자', '00013', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00013'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '금속', '00011', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00011'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '유통', '00016', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00016'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '증권', '00024', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00024'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '의료·정밀기기', '00014', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00014'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '전기·가스', '00017', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00017'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '은행', '00021', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00021'
);

INSERT INTO industry_index (name, code, provider, currency)
SELECT '통신', '00020', 'KIS', 'KRW'
WHERE NOT EXISTS (
    SELECT 1 FROM industry_index WHERE code = '00020'
);

-- -----------------------------------------
-- 2. industry_index_map seed
-- MVP 기준 명시 매핑
-- -----------------------------------------

-- 의약품(009) -> 제약(00009)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00009'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '009'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 운수장비(015) -> 운송장비·부품(00015)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00015'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '015'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 섬유,의복(006) -> 섬유·의류(00006)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00006'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '006'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 음식료품(005) -> 음식료·담배(00005)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00005'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '005'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 운수창고(019) -> 운송·창고(00019)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00019'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '019'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 서비스업(026) -> 일반서비스(00026)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00026'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '026'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 종이,목재(007) -> 종이·목재(00007)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00007'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '007'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 건설업(018) -> 건설(00018)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00018'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '018'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 화학(008) -> 화학(00008)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00008'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '008'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 보험(025) -> 보험(00025)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00025'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '025'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 비금속광물(010) -> 비금속(00010)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00010'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '010'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 기계(012) -> 기계·장비(00012)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00012'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '012'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 전기,전자(013) -> 전기·전자(00013)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00013'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '013'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 철강및금속(011) -> 금속(00011)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00011'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '011'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 유통업(016) -> 유통(00016)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00016'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '016'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 증권(024) -> 증권(00024)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00024'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '024'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 의료정밀(014) -> 의료·정밀기기(00014)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00014'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '014'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 전기가스업(017) -> 전기·가스(00017)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00017'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '017'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 은행(022) -> 은행(00021)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00021'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '022'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 통신업(020) -> 통신(00020)
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00020'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '020'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- 금융업(021) -> 금융(00021) 로 우선 매핑
-- 주의: 은행(022)도 같은 00021로 들어가므로
-- 이 부분은 향후 KIS 세부 금융지수 정책 확인 후 재검토 권장
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
         JOIN industry_index ii ON ii.code = '00021'
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.code = '021'
  AND NOT EXISTS (
    SELECT 1
    FROM industry_index_map m
    WHERE m.industry_id = i.industry_id
      AND m.index_id = ii.index_id
);

-- -----------------------------------------
-- 3. 권장 검증 쿼리
-- -----------------------------------------
-- SELECT i.code AS industry_code, i.name AS industry_name,
--        ii.code AS index_code, ii.name AS index_name
-- FROM industry_index_map m
-- JOIN industry i ON i.industry_id = m.industry_id
-- JOIN industry_index ii ON ii.index_id = m.index_id
-- ORDER BY i.code;