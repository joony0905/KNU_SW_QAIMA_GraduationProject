package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "api_request_log",
        indexes = {
                @Index(name = "idx_api_log_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_api_log_user_id", columnList = "user_id"),
                @Index(name = "idx_api_log_status", columnList = "status"),
                @Index(name = "idx_api_log_path", columnList = "path")
        }
)
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 512)
    private String path;

    @Lob
    @Column(name = "query_string")
    private String queryString;

    @Column(nullable = false)
    private int status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    // 로그 테이블은 join/lock 리스크 줄이려고 FK 안 거는 것을 권장
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 190)
    private String principal;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public static ApiRequestLog of(
            String requestId, String method, String path, String queryString,
            int status, String errorCode, int latencyMs,
            Long userId, String principal, String ip, String userAgent
    ) {
        ApiRequestLog log = new ApiRequestLog();
        log.requestId = requestId;
        log.method = method;
        log.path = path;
        log.queryString = queryString;
        log.status = status;
        log.errorCode = errorCode;
        log.latencyMs = latencyMs;
        log.userId = userId;
        log.principal = principal;
        log.ip = ip;
        log.userAgent = userAgent;
        return log;
    }
}
