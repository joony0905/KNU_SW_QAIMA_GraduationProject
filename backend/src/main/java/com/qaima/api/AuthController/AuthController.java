package com.qaima.api.AuthController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.user.FindIdRequestDto;
import com.qaima.dto.user.FindIdResponseDto;
import com.qaima.dto.user.LoginRequestDto;
import com.qaima.dto.user.LoginResponseDto;
import com.qaima.dto.user.SignupRequestDto;
import com.qaima.dto.user.TokenRefreshResponseDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.security.AuthCookieProperties;
import com.qaima.security.RefreshTokenCookieService;
import com.qaima.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieProperties authCookieProperties;
    private final RefreshTokenCookieService refreshTokenCookieService;

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
                    refreshTokenCookieService.writeRefreshTokenCookie(response, result.refreshToken());
                    return ApiResponse.success(result.response());
                });
    }

    @PostMapping("/find-id")
    public Mono<ApiResponse<FindIdResponseDto>> findId(@Valid @RequestBody FindIdRequestDto requestDto) {
        return authService.findLoginId(requestDto)
                .map(ApiResponse::success);
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<TokenRefreshResponseDto>> refresh(ServerHttpRequest request,
                                                              ServerHttpResponse response) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.refresh(resolveRefreshToken(request), ip, ua)
                .map(result -> {
                    refreshTokenCookieService.writeRefreshTokenCookie(response, result.refreshToken());
                    return ApiResponse.success(result.response());
                });
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(ServerHttpRequest request,
                                          ServerHttpResponse response) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.logout(resolveRefreshToken(request), ip, ua)
                .doOnSuccess(ignored -> refreshTokenCookieService.expireRefreshTokenCookie(response))
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
}
