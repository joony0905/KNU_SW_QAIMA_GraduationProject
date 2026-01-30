package com.qaima.filter;

import com.qaima.domain.ApiRequestLog;
import com.qaima.service.ApiRequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 활동(사용/트래픽) 기록용 요청 로그.
 * - /api/v1/auth/**, /api/v1/email/** 는 Auth(보안 이벤트) 로그로만 남기기 위해 여기서는 제외
 * - chain.filter(exchange)는 항상 호출(permitAll 요청도 정상 처리)
 */
@Component
@Order(-101)
@RequiredArgsConstructor
public class ApiRequestLogWebFilter implements WebFilter {

    private final ApiRequestLogService apiRequestLogService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        long startNs = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put("requestId", requestId);

        Mono<Authentication> authMono = exchange.getPrincipal()
                .cast(Authentication.class)
                .onErrorResume(e -> Mono.empty());

        Mono<Void> saveLog = authMono
                .flatMap(auth -> apiRequestLogService.save(build(exchange, auth, requestId, startNs)))
                .switchIfEmpty(apiRequestLogService.save(build(exchange, null, requestId, startNs)))
                .onErrorResume(e -> Mono.empty());

        return chain.filter(exchange)
                .then(Mono.defer(() -> authMono
                        .flatMap(auth -> apiRequestLogService.save(build(exchange, auth, requestId, startNs)))
                        .switchIfEmpty(Mono.defer(() -> apiRequestLogService.save(build(exchange, null, requestId, startNs))))
                        .onErrorResume(e -> Mono.empty())
                ));

    }


    private boolean shouldSkip(String path) {
        if (path == null) return false;

        // auth 로그로만 남기기
        if (path.startsWith("/api/v1/auth/")) return true;
        if (path.startsWith("/api/v1/email/")) return true;

        // 너무 잦거나 의미 없는 요청은 활동 로그에서 제외, 필요 시 조정
        if (path.startsWith("/api/v1/health/")) return true;
        if (path.equals("/api/v1/test/ping")) return true;

        return false;
    }

    private ApiRequestLog build(ServerWebExchange ex, Authentication auth, String requestId, long startNs) {
        String method = ex.getRequest().getMethod() != null
                ? ex.getRequest().getMethod().name()
                : "UNKNOWN";

        String path = ex.getRequest().getURI().getPath();
        String queryString = ex.getRequest().getURI().getQuery();

        Integer status = ex.getResponse().getStatusCode() != null ? ex.getResponse().getStatusCode().value() : 200;
        int latencyMs = (int) ((System.nanoTime() - startNs) / 1_000_000);

        String errorCode = (String) ex.getAttributes().get("errorCode");

        String ip = firstNonBlank(
                ex.getRequest().getHeaders().getFirst("X-Forwarded-For"),
                ex.getRequest().getRemoteAddress() != null
                        ? ex.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : null
        );

        String ua = ex.getRequest().getHeaders().getFirst("User-Agent");
        ua = truncate(ua, 512);

        Long userId = null;
        String principal = null;

        if (auth != null && auth.isAuthenticated()) {
            principal = auth.getName();
            Object p = auth.getPrincipal();
            if (p instanceof Long l) {
                userId = l;
            } else if (p != null) {
                try {
                    userId = Long.parseLong(p.toString());
                } catch (Exception ignore) {
                    // principal이 숫자가 아닐 수도 있으니 무시
                }
            }
        }

        return ApiRequestLog.of(
                requestId, method, truncate(path, 512), queryString,
                status, truncate(errorCode, 64), latencyMs,
                userId, truncate(principal, 190), ip, ua
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.split(",")[0].trim();
        return b;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
