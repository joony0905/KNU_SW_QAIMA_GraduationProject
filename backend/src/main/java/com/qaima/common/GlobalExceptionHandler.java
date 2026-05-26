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
                .body(ApiResponse.error(ec.code(), localizedMessage(ec, e.getMessage(), exchange))));
    }

    @ExceptionHandler(AnalysisApiException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAnalysisApi(AnalysisApiException e, ServerWebExchange exchange) {
        return error(ErrorCode.ANALYSIS_API_FAILED, ErrorCode.ANALYSIS_API_FAILED.defaultMessage(), exchange);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleNotFound(ResourceNotFoundException e, ServerWebExchange exchange) {
        return error(ErrorCode.RESOURCE_NOT_FOUND, e.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBind(WebExchangeBindException e, ServerWebExchange exchange) {
        String msg = firstValidationMessage(e);
        return error(ErrorCode.VALIDATION_ERROR, msg, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleInput(ServerWebInputException e, ServerWebExchange exchange) {
        return error(ErrorCode.VALIDATION_ERROR, safeMessage(e.getReason(), "Validation error"), exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgument(IllegalArgumentException e, ServerWebExchange exchange) {
        return error(ErrorCode.VALIDATION_ERROR, safeMessage(e.getMessage(), "Validation error"), exchange);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalState(IllegalStateException e, ServerWebExchange exchange) {
        return error(ErrorCode.INTERNAL_ERROR, safeMessage(e.getMessage(), "서버 내부 오류"), exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleResponseStatus(ResponseStatusException e, ServerWebExchange exchange) {
        int statusValue = e.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(statusValue);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorCode errorCode = ErrorCode.fromHttpStatus(statusValue);
        exchange.getAttributes().put("errorCode", errorCode.code());
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.error(errorCode.code(), localizedMessage(errorCode, e.getReason(), exchange))));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleException(Exception e, ServerWebExchange exchange) {
        return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), exchange);
    }

    private Mono<ResponseEntity<ApiResponse<Void>>> error(ErrorCode errorCode, String message, ServerWebExchange exchange) {
        exchange.getAttributes().put("errorCode", errorCode.code());
        return Mono.just(ResponseEntity.status(errorCode.status())
                .body(ApiResponse.error(errorCode.code(), localizedMessage(errorCode, message, exchange))));
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

    private static String localizedMessage(ErrorCode errorCode, String message, ServerWebExchange exchange) {
        if (!isEnglish(exchange)) {
            return safeMessage(message, errorCode.defaultMessage());
        }
        return englishDefaultMessage(errorCode);
    }

    private static boolean isEnglish(ServerWebExchange exchange) {
        String language = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        return language != null && language.toLowerCase().startsWith("en");
    }

    public static String englishDefaultMessage(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR, BAD_REQUEST -> "The request values are invalid.";
            case UNAUTHORIZED, INVALID_TOKEN -> "Login is required.";
            case FORBIDDEN -> "You do not have permission to access this.";
            case INSUFFICIENT_CREDIT -> "Not enough analysis credits.";
            case RESOURCE_NOT_FOUND, META_NOT_FOUND -> "The requested information could not be found.";
            case ANALYSIS_API_FAILED, FEATURE1_ANALYZE_FAILED, FEATURE2_ANALYZE_FAILED, FEATURE3_ANALYZE_FAILED,
                    PEER_CLUSTER_FAILED, NEWS_SENTIMENT_FAILED ->
                    "An error occurred during analysis. Please try again shortly.";
            case EXTERNAL_API_FAILED, EXTERNAL_DECODE_FAILED, KIS_HTTP_ERROR, KIS_DECODE_ERROR, KIS_BIZ_ERROR ->
                    "An external data integration error occurred. Please try again shortly.";
            case CONFIGURATION_ERROR, INTERNAL_ERROR -> "A server error occurred. Please try again shortly.";
            case KIS_MARKET_CLOSED -> "The market is currently closed.";
        };
    }
}
