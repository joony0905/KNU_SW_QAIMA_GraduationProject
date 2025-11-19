package com.qaima.api.AuthController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.SignupRequestDto;
import com.qaima.dto.UserResponseDto;
import com.qaima.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.qaima.dto.LoginRequestDto;
import com.qaima.dto.LoginResponseDto;

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
    public ApiResponse<UserResponseDto> signup(@RequestBody SignupRequestDto requestDto) {

        // 비밀번호 암호화, DB 저장
        UserResponseDto responseDto = authService.signup(requestDto);

        return ApiResponse.success(responseDto);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@RequestBody LoginRequestDto requestDto) {

        // 비밀번호 검증
        LoginResponseDto responseDto = authService.login(requestDto);

        return ApiResponse.success(responseDto);
    }
}
