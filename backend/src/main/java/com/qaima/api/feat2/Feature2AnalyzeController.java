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
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(
            @RequestBody(required = false) Feature2AnalyzeRequestDto req
    ) {
        log.info("[Feature2] analyze request received");

        return feature2AnalyzeService.analyze(req)
                .map(this::wrapWithWarnings)
                .onErrorResume(ex -> {
                    log.error("[Feature2] unexpected error in controller. cause={}", ex.getMessage(), ex);

                    Feature2MetaDto meta = Feature2MetaDto.empty();
                    meta.addWarning(Feat2WarningCode.FEAT2_INTERNAL_ERROR);

                    Feature2AnalyzeResponseDto fallback = Feature2AnalyzeResponseDto.builder()
                            .metrics(Feature2MetricsDto.empty())
                            .explain(null)
                            .meta(meta)
                            .build();

                    return Mono.just(wrapWithWarnings(fallback));
                });
    }

    private ApiResponse<Feature2AnalyzeResponseDto> wrapWithWarnings(Feature2AnalyzeResponseDto res) {
        ApiResponse<Feature2AnalyzeResponseDto> api = ApiResponse.success(res);
        if (res != null && res.getMeta() != null && res.getMeta().getWarnings() != null) {
            api.getMeta().setWarnings(res.getMeta().getWarnings());
        }
        return api;
    }
}