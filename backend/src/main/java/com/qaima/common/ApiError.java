package com.qaima.common;

/**
 * 에러 정보 클래스
 *
 * - code: 에러 유형 코드
 * - message: 상세 메시지
 */
public class ApiError {

    private String code;
    private String message;

    public ApiError() {}

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
