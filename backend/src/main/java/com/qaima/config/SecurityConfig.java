package com.qaima.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.security.JwtAuthFilter;
import com.qaima.security.OAuth2LoginFailureHandler;
import com.qaima.security.OAuth2LoginSuccessHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Value("${qaima.cors.allowed-origin-patterns:http://localhost:5173,http://localhost:3000,https://localhost:5173,https://localhost:3000}")
    private List<String> allowedOriginPatterns;

    @PostConstruct
    void validateCorsConfiguration() {
        if (allowedOriginPatterns != null && allowedOriginPatterns.contains("*")) {
            throw new IllegalStateException("qaima.cors.allowed-origin-patterns must not contain '*' when credentials are enabled");
        }
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(oAuth2LoginSuccessHandler)
                        .authenticationFailureHandler(oAuth2LoginFailureHandler)
                )
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(ex -> ex
                        // 공개
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/oauth2/**").permitAll()
                        .pathMatchers("/login/oauth2/**").permitAll()
                        .pathMatchers("/api/v1/email/**").permitAll()
                        .pathMatchers("/api/v1/meta/**").permitAll()
                        .pathMatchers("/api/v1/dictionary/**").permitAll()
                        .pathMatchers("/api/v1/health/**").permitAll()
                        .pathMatchers("/api/v1/test/ping").permitAll()
                        .pathMatchers("/api/v1/charts/**").permitAll()
                        .pathMatchers("/api/v1/stocks/**").permitAll()
                        .pathMatchers("/api/v1/featured-stocks/**").permitAll()
                        .pathMatchers("/api/v1/feature3/market-data/**").permitAll()
                        .pathMatchers("/api/v1/feature2/peercluster/data").permitAll()
                        .pathMatchers("/api/v1/feature2/news/**").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/base-rate").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/base-rate-series").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/macro-rates").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/macro-rates-series").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/industry-index").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/short-selling").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/short-selling-series").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/related-stocks").permitAll()
                        .pathMatchers("/api/v1/feature2/cards/investor-flow").permitAll()

                        // 관리자
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 나머지
                        .anyExchange().authenticated()
                )

                // 401/403에서 errorCode 주입 + JSON 응답
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex2) -> {
                            exchange.getAttributes().put("errorCode", ErrorCode.UNAUTHORIZED.code());
                            return writeJson(exchange, ErrorCode.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((exchange, ex2) -> {
                            exchange.getAttributes().put("errorCode", ErrorCode.FORBIDDEN.code());
                            return writeJson(exchange, ErrorCode.FORBIDDEN);
                        })
                )

                .build();
    }

    //CORS 설정 (프론트 dev 서버 포트에 맞춰 허용)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 쿠키/인증 쓰면 true 필요 (지금은 토큰 방식이어도 켜놔도 무방)
        config.setAllowCredentials(true);

        config.setAllowedOriginPatterns(allowedOriginPatterns);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private Mono<Void> writeJson(org.springframework.web.server.ServerWebExchange exchange,
                                 ErrorCode errorCode) {
        var response = exchange.getResponse();
        response.setStatusCode(errorCode.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String message = localizedErrorMessage(exchange, errorCode);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(errorCode.code(), message));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] bytes = ("{\"meta\":{\"status\":\"failure\"},\"data\":null,\"errors\":[{\"code\":\""
                    + errorCode.code() + "\",\"message\":\"" + message + "\"}]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    private static String localizedErrorMessage(org.springframework.web.server.ServerWebExchange exchange,
                                                ErrorCode errorCode) {
        String language = exchange.getRequest().getHeaders().getFirst("Accept-Language");
        if (language != null && language.toLowerCase().startsWith("en")) {
            return com.qaima.common.GlobalExceptionHandler.englishDefaultMessage(errorCode);
        }
        return errorCode.defaultMessage();
    }
}
