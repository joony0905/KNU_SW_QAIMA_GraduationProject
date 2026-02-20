package com.qaima.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .authorizeExchange(ex -> ex

                        // 공개
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/api/v1/email/**").permitAll()
                        .pathMatchers("/api/v1/meta/**").permitAll()
                        .pathMatchers("/api/v1/dictionary/**").permitAll()
                        .pathMatchers("/api/v1/health/**").permitAll()
                        .pathMatchers("/api/v1/test/ping").permitAll()

                        // 관리자
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 나머지
                        .anyExchange().authenticated()
                )

                // 401/403에서 errorCode 주입 + JSON 응답
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex2) -> {
                            exchange.getAttributes().put("errorCode", "UNAUTHORIZED");
                            return writeJson(exchange, 401, "UNAUTHORIZED", "인증이 필요합니다.");
                        })
                        .accessDeniedHandler((exchange, ex2) -> {
                            exchange.getAttributes().put("errorCode", "FORBIDDEN");
                            return writeJson(exchange, 403, "FORBIDDEN", "권한이 없습니다.");
                        })
                )

                .build();
    }

    private Mono<Void> writeJson(org.springframework.web.server.ServerWebExchange exchange,
                                 int status, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(code, message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] bytes = ("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
