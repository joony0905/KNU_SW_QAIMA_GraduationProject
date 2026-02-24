CREATE TABLE api_request_log (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 created_at DATETIME(6) NOT NULL,
                                 PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;