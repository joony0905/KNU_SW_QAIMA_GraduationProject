CREATE TABLE api_request_log (
                                 id BIGINT NOT NULL AUTO_INCREMENT,

                                 request_id VARCHAR(36) NOT NULL,
                                 occurred_at DATETIME(6) NOT NULL,

                                 method VARCHAR(10) NOT NULL,
                                 path VARCHAR(512) NOT NULL,
                                 query_string TINYTEXT NULL,

                                 status INT NOT NULL,
                                 error_code VARCHAR(64) NULL,

                                 latency_ms INT NOT NULL,
                                 user_id BIGINT NULL,

                                 principal VARCHAR(190) NULL,
                                 ip VARCHAR(45) NULL,
                                 user_agent VARCHAR(512) NULL,

                                 PRIMARY KEY (id),

                                 KEY idx_api_log_occurred_at (occurred_at),
                                 KEY idx_api_log_user_id (user_id),
                                 KEY idx_api_log_status (status),
                                 KEY idx_api_log_path (path)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;