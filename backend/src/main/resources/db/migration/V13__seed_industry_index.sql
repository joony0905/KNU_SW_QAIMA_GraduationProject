-- V13__seed_industry_index.sql
-- KIS 업종지수 seed
--
-- 원칙:
-- 1) industry_index master는 indexcode.txt 기준으로 seed
-- 2) industry_index_map은 exchange + industry.name 기준의 느슨한 매핑 사용
-- 3) 숫자 이름(예: 000, 063, 999 등)은 매핑 대상에서 제외
-- 4) 완벽 매핑보다 "무리하지 않고 꽂히는 것만 연결"을 우선

-- -----------------------------------------
-- 1. industry_index master seed
-- indexcode.txt 기준
-- -----------------------------------------
INSERT INTO industry_index (name, code, provider, currency)
SELECT src.name, src.code, 'KIS', 'KRW'
FROM (
    SELECT '00005' AS code, '음식료·담배' AS name UNION ALL
    SELECT '00006', '섬유·의류' UNION ALL
    SELECT '00007', '종이·목재' UNION ALL
    SELECT '00008', '화학' UNION ALL
    SELECT '00009', '제약' UNION ALL
    SELECT '00010', '비금속' UNION ALL
    SELECT '00011', '금속' UNION ALL
    SELECT '00012', '기계·장비' UNION ALL
    SELECT '00013', '전기·전자' UNION ALL
    SELECT '00014', '의료·정밀기기' UNION ALL
    SELECT '00015', '운송장비·부품' UNION ALL
    SELECT '00016', '유통' UNION ALL
    SELECT '00017', '전기·가스' UNION ALL
    SELECT '00018', '건설' UNION ALL
    SELECT '00019', '운송·창고' UNION ALL
    SELECT '00020', '통신' UNION ALL
    SELECT '00021', '금융' UNION ALL
    SELECT '00024', '증권' UNION ALL
    SELECT '00025', '보험' UNION ALL
    SELECT '00026', '일반서비스' UNION ALL
    SELECT '00027', '제조' UNION ALL
    SELECT '00028', '부동산' UNION ALL
    SELECT '00029', 'IT 서비스' UNION ALL
    SELECT '00030', '오락·문화' UNION ALL
    SELECT '11006', '일반서비스' UNION ALL
    SELECT '11009', '제조' UNION ALL
    SELECT '11010', '건설' UNION ALL
    SELECT '11011', '유통' UNION ALL
    SELECT '11013', '운송·창고' UNION ALL
    SELECT '11014', '금융' UNION ALL
    SELECT '11015', '오락·문화' UNION ALL
    SELECT '11019', '음식료·담배' UNION ALL
    SELECT '11020', '섬유·의류' UNION ALL
    SELECT '11021', '종이·목재' UNION ALL
    SELECT '11022', '출판·매체복제' UNION ALL
    SELECT '11023', '화학' UNION ALL
    SELECT '11024', '제약' UNION ALL
    SELECT '11025', '비금속' UNION ALL
    SELECT '11026', '금속' UNION ALL
    SELECT '11027', '기계·장비' UNION ALL
    SELECT '11028', '전기·전자' UNION ALL
    SELECT '11029', '의료·정밀기기' UNION ALL
    SELECT '11030', '운송장비·부품' UNION ALL
    SELECT '11031', '기타제조' UNION ALL
    SELECT '11032', '통신' UNION ALL
    SELECT '11033', 'IT 서비스'
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM industry_index ii
    WHERE ii.code = src.code
);

-- -----------------------------------------
-- 2. industry_index_map seed
-- exchange + industry.name alias 기반
-- 숫자 이름은 제외
-- -----------------------------------------
INSERT INTO industry_index_map (industry_id, index_id)
SELECT i.industry_id, ii.index_id
FROM industry i
JOIN exchange e
  ON e.exchange_id = i.exchange_id
JOIN (
    SELECT 'KOSPI' AS exchange_code, '의약품' AS industry_name, '00009' AS index_code UNION ALL
    SELECT 'KOSPI', '운수장비', '00015' UNION ALL
    SELECT 'KOSPI', '섬유,의복', '00006' UNION ALL
    SELECT 'KOSPI', '음식료품', '00005' UNION ALL
    SELECT 'KOSPI', '운수창고', '00019' UNION ALL
    SELECT 'KOSPI', '서비스업', '00026' UNION ALL
    SELECT 'KOSPI', '종이,목재', '00007' UNION ALL
    SELECT 'KOSPI', '건설업', '00018' UNION ALL
    SELECT 'KOSPI', '화학', '00008' UNION ALL
    SELECT 'KOSPI', '보험', '00025' UNION ALL
    SELECT 'KOSPI', '비금속광물', '00010' UNION ALL
    SELECT 'KOSPI', '기계', '00012' UNION ALL
    SELECT 'KOSPI', '전기,전자', '00013' UNION ALL
    SELECT 'KOSPI', '철강및금속', '00011' UNION ALL
    SELECT 'KOSPI', '유통업', '00016' UNION ALL
    SELECT 'KOSPI', '증권', '00024' UNION ALL
    SELECT 'KOSPI', '의료정밀', '00014' UNION ALL
    SELECT 'KOSPI', '전기가스업', '00017' UNION ALL
    SELECT 'KOSPI', '은행', '00021' UNION ALL
    SELECT 'KOSPI', '통신업', '00020' UNION ALL
    SELECT 'KOSPI', '금융업', '00021' UNION ALL

    SELECT 'KOSDAQ', '제약', '11024' UNION ALL
    SELECT 'KOSDAQ', '도매', '11011' UNION ALL
    SELECT 'KOSDAQ', '소매', '11011' UNION ALL
    SELECT 'KOSDAQ', '섬유·의류', '11020' UNION ALL
    SELECT 'KOSDAQ', '종이·목재', '11021' UNION ALL
    SELECT 'KOSDAQ', '종합건설', '11010' UNION ALL
    SELECT 'KOSDAQ', '전문건설', '11010' UNION ALL
    SELECT 'KOSDAQ', '운송장비·부품', '11030' UNION ALL
    SELECT 'KOSDAQ', '음식료·담배', '11019' UNION ALL
    SELECT 'KOSDAQ', '여행·운송서비스', '11013' UNION ALL
    SELECT 'KOSDAQ', '기타 제조', '11031' UNION ALL
    SELECT 'KOSDAQ', '기타제조', '11031' UNION ALL
    SELECT 'KOSDAQ', '금속', '11026' UNION ALL
    SELECT 'KOSDAQ', '반도체', '11028' UNION ALL
    SELECT 'KOSDAQ', '비금속', '11025' UNION ALL
    SELECT 'KOSDAQ', '의료·정밀기기', '11029' UNION ALL
    SELECT 'KOSDAQ', '일반전기전자', '11028' UNION ALL
    SELECT 'KOSDAQ', '금융', '11014' UNION ALL
    SELECT 'KOSDAQ', '금융서비스', '11014' UNION ALL
    SELECT 'KOSDAQ', '화학', '11023' UNION ALL
    SELECT 'KOSDAQ', '기계·장비', '11027' UNION ALL
    SELECT 'KOSDAQ', '통신장비', '11032' UNION ALL
    SELECT 'KOSDAQ', '통신서비스', '11032' UNION ALL
    SELECT 'KOSDAQ', '컴퓨터서비스', '11033' UNION ALL
    SELECT 'KOSDAQ', '소프트웨어', '11033' UNION ALL
    SELECT 'KOSDAQ', '인터넷', '11033' UNION ALL
    SELECT 'KOSDAQ', '디지털컨텐츠', '11033' UNION ALL
    SELECT 'KOSDAQ', 'IT부품', '11028' UNION ALL
    SELECT 'KOSDAQ', '정보기기', '11028' UNION ALL
    SELECT 'KOSDAQ', '출판·매체복제', '11022' UNION ALL
    SELECT 'KOSDAQ', '오락·문화', '11015' UNION ALL
    SELECT 'KOSDAQ', '전문기술', '11006' UNION ALL
    SELECT 'KOSDAQ', '연구·개발', '11006' UNION ALL
    SELECT 'KOSDAQ', '사업지원', '11006' UNION ALL
    SELECT 'KOSDAQ', '교육', '11006' UNION ALL
    SELECT 'KOSDAQ', '환경', '11006' UNION ALL
    SELECT 'KOSDAQ', '기타서비스', '11006' UNION ALL
    SELECT 'KOSDAQ', '부동산', '11006'
) map
  ON map.exchange_code = e.code
 AND map.industry_name = i.name
JOIN industry_index ii
  ON ii.code = map.index_code
WHERE i.scheme = 'KRX_BZTP_S'
  AND i.name NOT REGEXP '^[0-9]+$'
  AND NOT EXISTS (
      SELECT 1
      FROM industry_index_map m
      WHERE m.industry_id = i.industry_id
  );

-- -----------------------------------------
-- 3. 검증용 참고 쿼리
-- -----------------------------------------
-- SELECT e.code AS exchange_code, i.code AS industry_code, i.name AS industry_name,
--        ii.code AS index_code, ii.name AS index_name
-- FROM industry_index_map m
-- JOIN industry i ON i.industry_id = m.industry_id
-- JOIN exchange e ON e.exchange_id = i.exchange_id
-- JOIN industry_index ii ON ii.index_id = m.index_id
-- ORDER BY e.code, i.name, i.code;
