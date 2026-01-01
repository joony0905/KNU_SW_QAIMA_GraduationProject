-- Exchange 기본 데이터 초기화
INSERT INTO exchange (exchange_id, code, name, timezone, country)
VALUES
    (1, 'KRX',    'Korea Exchange',             'Asia/Seoul', 'KR'),
    (2, 'KOSDAQ', 'KOSDAQ Market',              'Asia/Seoul', 'KR'),
    (3, 'NYSE',   'New York Stock Exchange',    'America/New_York', 'US'),
    (4, 'NASDAQ', 'NASDAQ Stock Market',        'America/New_York', 'US');
