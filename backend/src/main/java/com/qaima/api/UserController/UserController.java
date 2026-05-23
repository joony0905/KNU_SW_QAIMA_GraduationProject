package com.qaima.api.UserController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.user.SocialProfileCompleteRequestDto;
import com.qaima.dto.user.UserProfileUpdateRequestDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.dto.user.UserRiskProfileDto;
import com.qaima.dto.user.UserRiskProfileUpdateRequestDto;
import com.qaima.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Mono<ApiResponse<UserResponseDto>> me(Authentication authentication) {
        return userService.getProfile(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PatchMapping("/me")
    public Mono<ApiResponse<UserResponseDto>> updateMe(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequestDto requestDto
    ) {
        return userService.updateProfile(currentUserId(authentication), requestDto)
                .map(ApiResponse::success);
    }

    @PatchMapping("/me/social-profile")
    public Mono<ApiResponse<UserResponseDto>> completeSocialProfile(
            Authentication authentication,
            @Valid @RequestBody SocialProfileCompleteRequestDto requestDto
    ) {
        return userService.completeSocialProfile(currentUserId(authentication), requestDto)
                .map(ApiResponse::success);
    }

    @GetMapping("/me/risk-profile")
    public Mono<ApiResponse<UserRiskProfileDto>> getRiskProfile(Authentication authentication) {
        return userService.getRiskProfile(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PatchMapping("/me/risk-profile")
    public Mono<ApiResponse<UserRiskProfileDto>> updateRiskProfile(
            Authentication authentication,
            @Valid @RequestBody UserRiskProfileUpdateRequestDto requestDto
    ) {
        return userService.updateRiskProfile(currentUserId(authentication), requestDto)
                .map(ApiResponse::success);
    }

    private static Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return userId;
    }
}
