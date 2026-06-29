package com.qaima.api.UserController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.user.SocialProfileCompleteRequestDto;
import com.qaima.dto.user.UserProfileUpdateRequestDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.dto.user.UserRiskProfileDto;
import com.qaima.dto.user.UserRiskProfileUpdateRequestDto;
import com.qaima.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get my profile", description = "Returns the authenticated user's profile.")
    public Mono<ApiResponse<UserResponseDto>> me(Authentication authentication) {
        return userService.getProfile(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update my profile")
    public Mono<ApiResponse<UserResponseDto>> updateMe(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequestDto requestDto
    ) {
        return userService.updateProfile(currentUserId(authentication), requestDto)
                .map(ApiResponse::success);
    }

    @PatchMapping("/me/social-profile")
    @Operation(summary = "Complete social profile")
    public Mono<ApiResponse<UserResponseDto>> completeSocialProfile(
            Authentication authentication,
            @Valid @RequestBody SocialProfileCompleteRequestDto requestDto
    ) {
        return userService.completeSocialProfile(currentUserId(authentication), requestDto)
                .map(ApiResponse::success);
    }

    @GetMapping("/me/risk-profile")
    @Operation(summary = "Get my risk profile")
    public Mono<ApiResponse<UserRiskProfileDto>> getRiskProfile(Authentication authentication) {
        return userService.getRiskProfile(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PatchMapping("/me/risk-profile")
    @Operation(summary = "Update my risk profile")
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
