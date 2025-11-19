package com.qaima.api;

import com.qaima.external.TestExternalClient;
import com.qaima.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 테스트용 컨트롤러
 * - 서버 동작 및 공통 응답 포맷, 외부api 연동 테스트
 */
@RestController
public class HelloController {
    private final TestExternalClient externalClient;
    public HelloController(TestExternalClient externalClient) {
        this.externalClient = externalClient;
    }

    @GetMapping("/")
    public String home() {
        return "Welcome! QAIMA backend is running";
    }

    /**
     * GET /api/v1/test/ping
     * 서버 헬스체크용 엔드포인트
     */
    @GetMapping("/api/v1/test/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> payload = Map.of(
                "ok", true,
                "service", "QAIMA backend",
                "version", "v1"
        );

        return ApiResponse.success(payload);
    }

    /**
     * GET /api/v1/test/external
     * 외부 API 통신 테스트용 엔드포인트
     */
    @GetMapping("/api/v1/test/external")
    public ApiResponse<Map<String, Object>> testExternal() {
        // JSONPlaceholder로 post 1번 글 호출 테스트
        String result = externalClient.getPost(1);

        Map<String, Object> payload = Map.of(
                "ok", true,
                "source", "jsonplaceholder.typicode.com/posts/1",
                "response", result
        );

        return ApiResponse.success(payload);
    }
}
