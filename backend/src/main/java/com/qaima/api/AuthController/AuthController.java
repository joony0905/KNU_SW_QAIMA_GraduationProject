package com.qaima.api.AuthController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.user.LoginRequestDto;
import com.qaima.dto.user.LoginResponseDto;
import com.qaima.dto.user.SignupRequestDto;
import com.qaima.dto.user.TokenRefreshResponseDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.security.AuthCookieProperties;
import com.qaima.security.JwtProperties;
import com.qaima.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieProperties authCookieProperties;
    private final JwtProperties jwtProperties;

    /**
     * 회원가입 API
     * POST /api/v1/auth/signup
     */

    @PostMapping("/signup")
    public Mono<ApiResponse<UserResponseDto>> signup(@Valid @RequestBody SignupRequestDto requestDto,
                                                     ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.signup(requestDto, ip, ua).map(ApiResponse::success);
    }

    /**
     * 로그인 API
     * POST /api/v1/auth/login
     */

    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto requestDto,
                                                     ServerHttpRequest request,
                                                     ServerHttpResponse response) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.login(requestDto, ip, ua)
                .map(result -> {
                    writeRefreshTokenCookie(response, result.refreshToken());
                    return ApiResponse.success(result.response());
                });
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<TokenRefreshResponseDto>> refresh(ServerHttpRequest request,
                                                              ServerHttpResponse response) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.refresh(resolveRefreshToken(request), ip, ua)
                .map(result -> {
                    writeRefreshTokenCookie(response, result.refreshToken());
                    return ApiResponse.success(result.response());
                });
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(ServerHttpRequest request,
                                          ServerHttpResponse response) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.logout(resolveRefreshToken(request), ip, ua)
                .doOnSuccess(ignored -> expireRefreshTokenCookie(response))
                .thenReturn(ApiResponse.success(null));
    }
    
    private static String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        if (request.getRemoteAddress() == null) return null;
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    private String resolveRefreshToken(ServerHttpRequest request) {
        String cookieRefreshToken = request.getCookies().getFirst(authCookieProperties.getRefreshTokenName()) != null
                ? request.getCookies().getFirst(authCookieProperties.getRefreshTokenName()).getValue()
                : null;

        return StringUtils.hasText(cookieRefreshToken) ? cookieRefreshToken : null;
    }

    private void writeRefreshTokenCookie(ServerHttpResponse response, String refreshToken) {
        response.addCookie(buildRefreshTokenCookie(refreshToken, Duration.ofSeconds(
                Math.max(60, jwtProperties.getRefreshTokenValiditySeconds())
        )));
    }

    private void expireRefreshTokenCookie(ServerHttpResponse response) {
        response.addCookie(buildRefreshTokenCookie("", Duration.ZERO));
    }

    private ResponseCookie buildRefreshTokenCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(
                        authCookieProperties.getRefreshTokenName(),
                        value == null ? "" : value
                )
                .httpOnly(authCookieProperties.isHttpOnly())
                .secure(authCookieProperties.isSecure())
                .path(authCookieProperties.getPath())
                .maxAge(maxAge)
                .sameSite(authCookieProperties.getSameSite());

        if (StringUtils.hasText(authCookieProperties.getDomain())) {
            builder.domain(authCookieProperties.getDomain());
        }

        return builder.build();
    }
}
