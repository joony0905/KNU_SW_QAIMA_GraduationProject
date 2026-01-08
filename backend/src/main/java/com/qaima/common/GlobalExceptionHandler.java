package com.qaima.common;

import com.qaima.common.exception.AnalysisApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리기
 *
 * - 모든 예외를 ApiResponse 형태로 감싸서 반환
 * - 프론트엔드에서 일관된 응답 구조로 처리 가능
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 분석 API 실패 */
    @ExceptionHandler(AnalysisApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleAnalysisApi(AnalysisApiException e) {
        ApiResponse<Void> body =
                ApiResponse.error("ANALYSIS_API_FAILED", "분석 결과를 불러올 수 없습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** 입력값 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation error";

        ApiResponse<Void> body = ApiResponse.error("VALIDATION_ERROR", msg);
        return ResponseEntity.badRequest().body(body);
    }

    /** 나머지 예외 (진짜 내부 오류) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        ApiResponse<Void> body =
                ApiResponse.error("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

