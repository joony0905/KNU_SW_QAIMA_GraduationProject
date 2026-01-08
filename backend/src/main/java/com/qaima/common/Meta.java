package com.qaima.common;

import java.time.Instant;
import java.util.UUID;

/**
 * API 응답 메타데이터
 *
 * - 요청 ID, 상태(success/failure), 응답 시각
 * - warning: 부분 실패/주의 상태 전달용
 */
public class Meta {

    private String requestId;
    private String status;
    private Instant timestamp;
    private String warning;

    public Meta() {}

    private Meta(String requestId, String status, Instant timestamp) {
        this.requestId = requestId;
        this.status = status;
        this.timestamp = timestamp;
    }

    /** 성공 메타데이터 생성 */
    public static Meta success() {
        return new Meta(UUID.randomUUID().toString(), "success", Instant.now());
    }

    /** 실패 메타데이터 생성 */
    public static Meta failure() {
        return new Meta(UUID.randomUUID().toString(), "failure", Instant.now());
    }

    // ===== getters / setters =====

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
