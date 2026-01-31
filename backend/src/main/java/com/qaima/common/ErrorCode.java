package com.qaima.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ===== 400 =====
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),

    // ===== 401/403 =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "토큰이 유효하지 않습니다."),

    // ===== 404 =====
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다."),

    // ===== 500 =====
    ANALYSIS_API_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_API_FAILED", "분석 API 호출에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류"),

    //502
    KIS_HTTP_ERROR(HttpStatus.BAD_GATEWAY, "KIS_HTTP_ERROR", "KIS 호출에 실패했습니다."),
    KIS_DECODE_ERROR(HttpStatus.BAD_GATEWAY, "KIS_DECODE_ERROR", "KIS 응답 파싱에 실패했습니다."),
    KIS_BIZ_ERROR(HttpStatus.BAD_GATEWAY, "KIS_BIZ_ERROR", "KIS 응답이 정상 처리되지 않았습니다."),

    // ===== 200 이지만 상태 알림(권장: 서비스에서 warning으로 처리) =====
    KIS_MARKET_CLOSED(HttpStatus.SERVICE_UNAVAILABLE, "KIS_MARKET_CLOSED", "장 운영 시간이 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
}
