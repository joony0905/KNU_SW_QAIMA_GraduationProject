ALTER TABLE login_session
    MODIFY user_agent VARCHAR(512) NULL;

ALTER TABLE api_request_log
    MODIFY query_string TEXT NULL;
