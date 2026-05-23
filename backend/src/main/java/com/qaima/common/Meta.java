// backend/src/main/java/com/qaima/common/Meta.java
package com.qaima.common;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API 응답 메타데이터
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class Meta {

    private String requestId;
    private String status;
    private Instant timestamp;
    private List<String> warnings;
    private String warning;
    private Long reportId;

    public Meta() {}

    private Meta(String requestId, String status, Instant timestamp) {
        this.requestId = requestId;
        this.status = status;
        this.timestamp = timestamp;
        this.warnings = new ArrayList<>();
    }

    public static Meta success() {
        return new Meta(UUID.randomUUID().toString(), "success", Instant.now());
    }

    public static Meta failure() {
        return new Meta(UUID.randomUUID().toString(), "failure", Instant.now());
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public List<String> getWarnings() {
        if (warnings == null) warnings = new ArrayList<>();
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = (warnings != null) ? warnings : new ArrayList<>();
        this.warning = (this.warnings.isEmpty()) ? null : this.warnings.get(0);
    }

    public void addWarning(String code) {
        if (code == null || code.isBlank()) return;
        getWarnings().add(code);
        if (this.warning == null || this.warning.isBlank()) {
            this.warning = code;
        }
    }

    public String getWarning() { return warning; }

    public void setWarning(String warning) {
        this.warning = warning;
        if (warning != null && !warning.isBlank()) {
            getWarnings().add(warning);
        }
    }

    public Long getReportId() { return reportId; }

    public void setReportId(Long reportId) { this.reportId = reportId; }
}
