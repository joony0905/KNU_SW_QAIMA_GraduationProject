// backend/src/main/java/com/qaima/api/feature2/Feature2AnalyzeController.java
package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.feature2.Feature2AnalyzeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/feature2")
public class Feature2AnalyzeController {

    private final Feature2AnalyzeService feature2AnalyzeService;

    @PostMapping("/analyze")
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(@RequestBody(required = false) Feature2AnalyzeRequestDto req) {
        System.out.println(">>> FEAT2 ANALYZE REQUEST ENTERED <<<");
        return feature2AnalyzeService.analyze(req)
                .map(res -> {
                    ApiResponse<Feature2AnalyzeResponseDto> api = ApiResponse.success(res);

                    // Feature2 warnings를 ApiResponse.meta.warnings로 "그대로" 노출
                    if (api.getMeta() != null && res != null && res.getMeta() != null && res.getMeta().getWarnings() != null) {
                        api.getMeta().setWarnings(res.getMeta().getWarnings());
                    }
                    return api;
                })
                .onErrorResume(ex -> {
                    // Controller도 throw 금지(원칙): 응답은 success로 유지하되 warnings로 알림
                    log.error("[Feature2] unexpected error in controller. cause={}", ex.getMessage(), ex);

                    Feature2MetaDto meta = Feature2MetaDto.empty();
                    meta.addWarning(Feat2WarningCode.LLM_EXPLAIN_FAILED); // 임시: MVP enum 내에서 가장 “내부 실패”에 가까운 코드
                    // 엄밀하게 할시 FEAT2_INTERNAL_ERROR 같은 코드를 MVP enum에 추가

                    Feature2AnalyzeResponseDto fallback = Feature2AnalyzeResponseDto.builder()
                            .metrics(Feature2MetricsDto.empty())
                            .explain(null)
                            .meta(meta)
                            .build();

                    ApiResponse<Feature2AnalyzeResponseDto> api = ApiResponse.success(fallback);
                    api.getMeta().setWarnings(meta.getWarnings());
                    return Mono.just(api);
                });
    }
}