package com.qaima.api.AuthController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.user.LoginRequestDto;
import com.qaima.dto.user.LoginResponseDto;
import com.qaima.dto.user.LogoutRequestDto;
import com.qaima.dto.user.SignupRequestDto;
import com.qaima.dto.user.TokenRefreshRequestDto;
import com.qaima.dto.user.TokenRefreshResponseDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

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
                                                     ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.login(requestDto, ip, ua).map(ApiResponse::success);
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<TokenRefreshResponseDto>> refresh(@Valid @RequestBody TokenRefreshRequestDto requestDto,
                                                              ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.refresh(requestDto.getRefreshToken(), ip, ua).map(ApiResponse::success);
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequestDto requestDto,
                                          ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");
        return authService.logout(requestDto.getRefreshToken(), ip, ua)
                .thenReturn(ApiResponse.success(null));
    }
    
    private static String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        if (request.getRemoteAddress() == null) return null;
        return request.getRemoteAddress().getAddress().getHostAddress();
    }
}
