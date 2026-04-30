-- V28__seed_feature2_benchmark_index.sql
-- 기능2 비교/벤치마크용 대표 지수 seed
--
-- rawidxcode.txt 기준:
-- 00001 종합
-- 11001 KOSDAQ
-- frgn_code.mst 기준:
-- P + COMP = NASDAQ Composite / 나스닥 종합

INSERT INTO industry_index (name, code, provider, currency)
SELECT src.name, src.code, 'KIS', src.currency
FROM (
    SELECT '00001' AS code, 'KOSPI' AS name, 'KRW' AS currency UNION ALL
    SELECT '11001', 'KOSDAQ', 'KRW' UNION ALL
    SELECT 'COMP', 'NASDAQ Composite', 'USD'
) src
WHERE NOT EXISTS (
    SELECT 1
    FROM industry_index ii
    WHERE ii.code = src.code
);
