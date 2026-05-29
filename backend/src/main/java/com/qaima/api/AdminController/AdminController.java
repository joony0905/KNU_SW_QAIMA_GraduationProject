package com.qaima.api.AdminController;

import com.qaima.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Sync", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    @GetMapping("/ping")
    @Operation(summary = "Check admin API")
    public ApiResponse<String> ping() {
        return ApiResponse.success("admin ok");
    }
}
