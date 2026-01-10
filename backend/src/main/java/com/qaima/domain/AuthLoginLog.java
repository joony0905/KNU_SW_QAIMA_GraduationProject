package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "auth_login_log",
        indexes = {
                @Index(name = "idx_login_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_login_user_id", columnList = "user_id"),
                @Index(name = "idx_login_email", columnList = "email"),
                @Index(name = "idx_login_event_type", columnList = "event_type")
        }
)
public class AuthLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /**
     * 보안 이벤트 타입 (로그인/이메일인증/비번재설정 등)
     * 예: LOGIN_SUCCESS, LOGIN_FAILED, EMAIL_VERIFICATION_REQUESTED, EMAIL_VERIFICATION_CONFIRMED ...
     */
    @Column(name = "event_type", nullable = false, length = 64)
    @ColumnDefault("'LOGIN_SUCCESS'")
    private String eventType;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 190)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(length = 512)
    private String message;

    public static AuthLoginLog event(
            String eventType,
            boolean success,
            Long userId,
            String email,
            String ip,
            String userAgent,
            String errorCode,
            String message
    ) {
        AuthLoginLog log = new AuthLoginLog();
        log.eventType = truncate(eventType, 64);
        log.success = success;
        log.userId = userId;
        log.email = truncate(email, 190);
        log.ip = truncate(ip, 45);
        log.userAgent = truncate(userAgent, 512);
        log.errorCode = truncate(errorCode, 64);
        log.message = truncate(message, 512);
        return log;
    }

    public static AuthLoginLog success(Long userId, String email, String ip, String userAgent) {
        return event("LOGIN_SUCCESS", true, userId, email, ip, userAgent, null, null);
    }

    public static AuthLoginLog failure(String email, String ip, String userAgent, String errorCode, String message) {
        return event("LOGIN_FAILED", false, null, email, ip, userAgent, errorCode, message);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
