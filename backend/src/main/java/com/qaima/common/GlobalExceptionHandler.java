package com.qaima.common;

import com.qaima.common.exception.AnalysisApiException;
import com.qaima.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ErrorException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleQaima(ErrorException e, ServerWebExchange exchange) {
        ErrorCode ec = e.getErrorCode();
        exchange.getAttributes().put("errorCode", ec.code());
        return Mono.just(ResponseEntity.status(ec.status())
                .body(ApiResponse.error(ec.code(), e.getMessage())));
    }

    @ExceptionHandler(AnalysisApiException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAnalysisApi(AnalysisApiException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "ANALYSIS_API_FAILED");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("ANALYSIS_API_FAILED", "분석 결과를 불러올 수 없습니다.")));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNotFound(ResourceNotFoundException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "RESOURCE_NOT_FOUND");
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", e.getMessage())));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBind(WebExchangeBindException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "VALIDATION_ERROR");
        String msg = firstValidationMessage(e);
        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", msg)));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleInput(ServerWebInputException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "VALIDATION_ERROR");
        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", safeMessage(e.getReason(), "Validation error"))));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgument(IllegalArgumentException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "VALIDATION_ERROR");
        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", safeMessage(e.getMessage(), "Validation error"))));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalState(IllegalStateException e, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", "INTERNAL_ERROR");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", safeMessage(e.getMessage(), "서버 내부 오류"))));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleResponseStatus(ResponseStatusException e, ServerWebExchange exchange) {
        int statusValue = e.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(statusValue);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String code = switch (statusValue) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE_ENTITY";
            default -> "HTTP_" + statusValue;
        };

        exchange.getAttributes().put("errorCode", code);
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.error(code, safeMessage(e.getReason(), status.getReasonPhrase()))));
    }

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
