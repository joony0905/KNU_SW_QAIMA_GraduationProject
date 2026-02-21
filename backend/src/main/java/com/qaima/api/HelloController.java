package com.qaima.api;

import com.qaima.common.ApiResponse;
import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.dto.FeatOneRequestDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.TestExternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 테스트용 컨트롤러
 * - 서버 동작, 공통 응답 포맷, 외부 API 연동, FastAPI 연동 테스트
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class HelloController {

    private final TestExternalClient externalClient;
    private final AnalysisApiClient analysisApiClient;

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
     * 외부(예시) API 통신 테스트
     */
    @GetMapping("/api/v1/test/external")
    public ApiResponse<Map<String, Object>> testExternal() {
        String result = externalClient.getPost(1);

        Map<String, Object> payload = Map.of(
                "ok", true,
                "source", "jsonplaceholder.typicode.com/posts/1",
                "response", result
        );
        return ApiResponse.success(payload);
    }

    /**
     * GET /api/v1/test/feature1
     * FastAPI(기능1) 응답 테스트
     */
    @GetMapping("/api/v1/test/feature1")
    public Mono<ApiResponse<FeatOneAnalysisResponseDto>> testAnalysis() {

        // 최소 필드만 채운 더미 요청
        FeatOneRequestDto req = FeatOneRequestDto.builder()
                .stockCode("AAPL")
                .freq(com.qaima.domain.Freq.ONE_D)
                .ohlcv(java.util.List.of())
                .financials(java.util.List.of())
                .includeExplain(false)
                .options(Map.of())
                .build();

        return analysisApiClient.requestStockAnalysis(req)
                .map(ApiResponse::success)               // 성공 시 공통 성공 응답으로 감싸기
                .onErrorResume(e ->                      // 실패 시 공통 실패 응답으로 감싸기
                        Mono.just(ApiResponse.internalError(
                                "FASTAPI_ERROR",
                                "FastAPI 분석 요청 실패: " + e.getMessage()
                        ))
                );
    }
}
