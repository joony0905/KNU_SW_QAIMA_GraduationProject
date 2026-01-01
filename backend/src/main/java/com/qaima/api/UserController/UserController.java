package com.qaima.api.UserController;

import com.qaima.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")

public class UserController {

    @GetMapping("/jwt_test")
    public ApiResponse<MeResponse> jwt_test(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("UNKNOWN");

        return ApiResponse.success(new MeResponse(userId, role));
    }

    public record MeResponse(Long userId, String role) {}
}
