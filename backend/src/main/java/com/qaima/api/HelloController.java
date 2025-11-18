package com.qaima.api;

import com.qaima.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 테스트용 컨트롤러
 * - 서버 동작 및 공통 응답 포맷 확인용
 */
@RestController
public class HelloController {

    /**
     * GET /api/v1/ping
     * 서버 헬스체크용 엔드포인트
     */
    @GetMapping("/api/v1/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> payload = Map.of(
                "ok", true,
                "service", "QAIMA backend",
                "version", "v1"
        );

        return ApiResponse.success(payload);
    }
}
