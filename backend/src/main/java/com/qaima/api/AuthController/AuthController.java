package com.qaima.api.AuthController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.LoginRequestDto;
import com.qaima.dto.LoginResponseDto;
import com.qaima.dto.SignupRequestDto;
import com.qaima.dto.UserResponseDto;
import com.qaima.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public Mono<ApiResponse<UserResponseDto>> signup(@Valid @RequestBody SignupRequestDto requestDto) {
        return authService.signup(requestDto)
                .map(ApiResponse::success);
    }

    /**
     * 로그인 API
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto requestDto,
                                                     HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return authService.login(requestDto, ip)
                .map(ApiResponse::success);
    }
}
