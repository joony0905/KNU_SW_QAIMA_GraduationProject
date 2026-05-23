package com.qaima.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ===== 400 =====
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "요청 형식이 올바르지 않습니다."),

    // ===== 401/403 =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "토큰이 유효하지 않습니다."),

    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_CREDIT", "분석 토큰이 부족합니다."),

    // ===== 404 =====
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다."),
    META_NOT_FOUND(HttpStatus.NOT_FOUND, "META_NOT_FOUND", "티커 메타 정보를 찾을 수 없습니다."),

    // ===== 500 =====
    ANALYSIS_API_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_API_FAILED", "분석 API 호출에 실패했습니다."),
    FEATURE1_ANALYZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FEATURE1_ANALYZE_FAILED", "Feature1 분석에 실패했습니다."),
    FEATURE2_ANALYZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FEATURE2_ANALYZE_FAILED", "Feature2 분석에 실패했습니다."),
    FEATURE3_ANALYZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FEATURE3_ANALYZE_FAILED", "Feature3 분석에 실패했습니다."),
    PEER_CLUSTER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PEER_CLUSTER_FAILED", "유사 종목 분석에 실패했습니다."),
    NEWS_SENTIMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NEWS_SENTIMENT_FAILED", "뉴스 감성 분석에 실패했습니다."),
    EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_FAILED", "외부 API 호출에 실패했습니다."),
    EXTERNAL_DECODE_FAILED(HttpStatus.BAD_GATEWAY, "EXTERNAL_DECODE_FAILED", "외부 API 응답 파싱에 실패했습니다."),
    CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIGURATION_ERROR", "서버 설정 오류"),
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

    public static ErrorCode fromHttpStatus(int statusValue) {
        return switch (statusValue) {
            case 400, 405, 406, 409, 415, 422 -> VALIDATION_ERROR;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            default -> INTERNAL_ERROR;
        };
    }
}
