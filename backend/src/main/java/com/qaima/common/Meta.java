// backend/src/main/java/com/qaima/common/Meta.java
package com.qaima.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API 응답 메타데이터
 *
 * - requestId, status(success/failure), timestamp
 * - warnings: 부분 실패/주의 상태 전달용 (누적)
 *
 * 호환성: 26/2/24
 * - 기존 warning(String) 필드는 유지하되, 신규 정책은 warnings(List<String>)를 사용한다.
 * - setWarning(String)은 내부적으로 warnings에 add도 수행한다.
 */
public class Meta {

    private String requestId;
    private String status;
    private Instant timestamp;

    /** 신규 정책: 누적 warnings */
    private List<String> warnings;

    /**
     * 레거시 호환용 단일 warning (가능하면 warnings를 사용)
     * - 기존 클라이언트가 warning 필드를 읽는 경우를 위해 유지
     */
    private String warning;

    public Meta() {}

    private Meta(String requestId, String status, Instant timestamp) {
        this.requestId = requestId;
        this.status = status;
        this.timestamp = timestamp;
        this.warnings = new ArrayList<>();
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

    /** 신규: warnings list */
    public List<String> getWarnings() {
        if (warnings == null) warnings = new ArrayList<>();
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = (warnings != null) ? warnings : new ArrayList<>();
        // 레거시 warning도 최대한 동기화(첫 번째 warning)
        this.warning = (this.warnings.isEmpty()) ? null : this.warnings.get(0);
    }

    public void addWarning(String code) {
        if (code == null || code.isBlank()) return;
        getWarnings().add(code);
        if (this.warning == null || this.warning.isBlank()) {
            this.warning = code;
        }
    }

    /** 레거시: 단일 warning */
    public String getWarning() { return warning; }

    /**
     * 레거시 setter:
     * - warning 단일 필드 설정 + warnings에도 누적(add)
     * - 신규 코드에서는 addWarning / setWarnings 사용 권장
     */
    public void setWarning(String warning) {
        this.warning = warning;
        if (warning != null && !warning.isBlank()) {
            getWarnings().add(warning);
        }
    }
}