package com.qaima.common;

import com.qaima.common.exception.AnalysisApiException;
import com.qaima.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 표준 에러
     */
    @ExceptionHandler(ErrorException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleQaima(ErrorException e, ServerWebExchange exchange) {
        ErrorCode ec = e.getErrorCode();
        exchange.getAttributes().put("errorCode", ec.code());
        return Mono.just(ResponseEntity.status(ec.status())
                .body(ApiResponse.error(ec.code(), e.getMessage())));
    }

    /**
     * 분석 API 실패
     */
    @ExceptionHandler(AnalysisApiException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAnalysisApi(AnalysisApiException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "ANALYSIS_API_FAILED");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("ANALYSIS_API_FAILED", "분석 결과를 불러올 수 없습니다.")));
    }

    /**
     * 리소스 없음
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNotFound(ResourceNotFoundException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "RESOURCE_NOT_FOUND");
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", e.getMessage())));
    }

    /**
     * 입력값 검증 실패 (WebFlux 바인딩/검증 예외)
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBind(WebExchangeBindException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "VALIDATION_ERROR");
        String msg = firstValidationMessage(e);
        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", msg)));
    }

    /**
     * 사용자 입력/비즈니스 검증 실패
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgument(IllegalArgumentException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "VALIDATION_ERROR");
        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", safeMessage(e.getMessage(), "Validation error"))));
    }

    /**
     * 처리 실패/상태 이상 (메일 발송 실패 등)
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalState(IllegalStateException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "INTERNAL_ERROR");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", safeMessage(e.getMessage(), "서버 내부 오류"))));
    }

    /**
     * 나머지 예외 (내부)
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleException(Exception e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "INTERNAL_ERROR");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 내부 오류")));
    }

    private static String firstValidationMessage(WebExchangeBindException e) {
        if (e.getFieldErrors() == null || e.getFieldErrors().isEmpty()) {
            return "Validation error";
        }
        String msg = e.getFieldErrors().get(0).getDefaultMessage();
        return safeMessage(msg, "Validation error");
    }

    private static String safeMessage(String msg, String fallback) {
        return (msg == null || msg.isBlank()) ? fallback : msg;
    }
}
