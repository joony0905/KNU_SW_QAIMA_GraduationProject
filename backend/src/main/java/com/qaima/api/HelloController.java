package com.qaima.api;

import com.qaima.common.ApiResponse;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.TestExternalClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Health / Debug")
public class HelloController {

    private final TestExternalClient externalClient;
    private final AnalysisApiClient analysisApiClient;

    @GetMapping("/")
    @Operation(summary = "Backend home")
    @SecurityRequirements
    public String home() {
        return "Welcome! QAIMA backend is running";
    }

    @GetMapping("/api/v1/test/ping")
    @Operation(summary = "Health check")
    @SecurityRequirements
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> payload = Map.of(
                "ok", true,
                "service", "QAIMA backend",
                "version", "v1"
        );
        return ApiResponse.success(payload);
    }

    @GetMapping("/api/v1/test/external")
    @Operation(summary = "Debug external API")
    @SecurityRequirement(name = "bearerAuth")
    public Mono<ApiResponse<Map<String, Object>>> testExternal() {
        return externalClient.getPost(1)
                .map(result -> {
                    Map<String, Object> payload = Map.of(
                            "ok", true,
                            "source", "jsonplaceholder.typicode.com/posts/1",
                            "response", result
                    );
                    return ApiResponse.success(payload);
                })
                .onErrorResume(e -> Mono.just(ApiResponse.<Map<String, Object>>internalError(
                        "EXTERNAL_TEST_FAILED",
                        "External test failed: " + safeMessage(e.getMessage())
                )));
    }

    @GetMapping("/api/v1/test/feature1")
    @Operation(summary = "Debug Feature1 API")
    @SecurityRequirement(name = "bearerAuth")
    public Mono<ApiResponse<FeatOneAnalysisResponseDto>> testAnalysis() {
        FeatOneRequestDto req = FeatOneRequestDto.builder()
                .stockCode("AAPL")
                .freq(com.qaima.domain.Freq.ONE_D)
                .ohlcv(java.util.List.of())
                .financials(java.util.List.of())
                .includeExplain(false)
                .build();

        return analysisApiClient.requestStockAnalysis(req)
                .map(ApiResponse::success)
                .onErrorResume(e -> Mono.just(ApiResponse.internalError(
                        "FASTAPI_ERROR",
                        "FastAPI analysis failed: " + safeMessage(e.getMessage())
                )));
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "n/a";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
