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
    public ApiResponse<UserResponseDto> signup(@Valid @RequestBody SignupRequestDto requestDto) {
        UserResponseDto responseDto = authService.signup(requestDto);
        return ApiResponse.success(responseDto);
    }

    /**
     * 로그인 API
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto,
                                               HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        LoginResponseDto responseDto = authService.login(requestDto, ip);
        return ApiResponse.success(responseDto);
    }
}
