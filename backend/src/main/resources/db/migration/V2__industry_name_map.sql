-- V2__industry_name_map.sql
-- Mapping table: provider+scheme+(code or name_norm) -> industry_id

CREATE TABLE IF NOT EXISTS industry_name_map (
                                                 industry_name_map_id BIGINT NOT NULL AUTO_INCREMENT,

                                                 provider   VARCHAR(20)  NOT NULL,
                                                 scheme     VARCHAR(30)  NOT NULL,
                                                 market     VARCHAR(20)  NULL,

                                                 code       VARCHAR(50)  NULL,
                                                 name_norm  VARCHAR(255) NULL,

                                                 industry_id BIGINT NOT NULL,

                                                 confidence VARCHAR(20) NOT NULL,
                                                 is_active  TINYINT(1)  NOT NULL DEFAULT 1,

                                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                 PRIMARY KEY (industry_name_map_id),

                                                 KEY idx_inm_industry_id (industry_id),
                                                 KEY idx_inm_provider_scheme (provider, scheme),
                                                 KEY idx_inm_name_lookup (provider, scheme, market, name_norm),

                                                 CONSTRAINT fk_inm_industry
                                                     FOREIGN KEY (industry_id) REFERENCES industry(industry_id)
                                                         ON DELETE RESTRICT
                                                         ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- "부분 유니크"를 MySQL에서 MVP로 흉내내는 방식: 유니크 2개
-- 1) 코드 기반 매핑: (provider, scheme, code)
--    code가 NULL이면 유니크 충돌이 잘 안 걸릴 수 있으므로(=NULL 다수 허용),
--    애플리케이션은 code 매핑 row는 반드시 code를 채우는 규칙을 지켜야 함.
CREATE UNIQUE INDEX uk_inm_code
    ON industry_name_map(provider, scheme, code);

-- 2) 이름 기반 매핑: (provider, scheme, market, name_norm)
--    name 매핑 row는 반드시 market+name_norm을 채우는 규칙을 지켜야 함.
CREATE UNIQUE INDEX uk_inm_name
    ON industry_name_map(provider, scheme, market, name_norm);