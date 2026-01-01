package com.qaima.api.AdminController;

import com.qaima.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")

public class AdminController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("admin ok");
    }
}
