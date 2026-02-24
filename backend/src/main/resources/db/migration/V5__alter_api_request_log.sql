ALTER TABLE api_request_log
    CHANGE COLUMN created_at occurred_at DATETIME(6) NOT NULL,
    ADD COLUMN request_id VARCHAR(36) NOT NULL AFTER id,
    ADD COLUMN method VARCHAR(10) NOT NULL AFTER occurred_at,
    ADD COLUMN path VARCHAR(512) NOT NULL AFTER method,
    ADD COLUMN query_string LONGTEXT NULL AFTER path,
    ADD COLUMN status INT NOT NULL AFTER query_string,
    ADD COLUMN error_code VARCHAR(64) NULL AFTER status,
    ADD COLUMN latency_ms INT NOT NULL AFTER error_code,
    ADD COLUMN user_id BIGINT NULL AFTER latency_ms,
    ADD COLUMN principal VARCHAR(190) NULL AFTER user_id,
    ADD COLUMN ip VARCHAR(45) NULL AFTER principal,
    ADD COLUMN user_agent VARCHAR(512) NULL AFTER ip;

CREATE INDEX idx_api_log_occurred_at ON api_request_log (occurred_at);
CREATE INDEX idx_api_log_user_id ON api_request_log (user_id);
CREATE INDEX idx_api_log_status ON api_request_log (status);
CREATE INDEX idx_api_log_path ON api_request_log (path);
