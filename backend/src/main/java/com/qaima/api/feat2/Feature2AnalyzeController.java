package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.feature2.Feature2AnalyzeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/feature2")
public class Feature2AnalyzeController {

    private final Feature2AnalyzeService feature2AnalyzeService;

    @PostMapping("/analyze")
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(
            @Valid @RequestBody Feature2AnalyzeRequestDto req
    ) {
        log.info("[Feature2AnalyzeController][request] stockCode={}, freq={}, window={}",
                req != null ? req.getStockCode() : null,
                req != null ? req.getFreq() : null,
                req != null ? req.getWindow() : null);

        return feature2AnalyzeService.analyze(req)
                .map(this::wrapWithWarnings)
                .onErrorResume(ex -> {
                    log.error("[Feature2] unexpected error in controller. cause={}", ex.getMessage(), ex);

                    Feature2AnalyzeResponseDto fallback = Feature2AnalyzeResponseDto.builder()
                            .metrics(Feature2MetricsDto.empty())
                            .explain(null)
                            .warnings(List.of(Feat2WarningCode.FEAT2_INTERNAL_ERROR.name()))
                            .build();

                    return Mono.just(wrapWithWarnings(fallback));
                });
    }

    private ApiResponse<Feature2AnalyzeResponseDto> wrapWithWarnings(Feature2AnalyzeResponseDto res) {
        List<String> warnings = (res == null || res.getWarnings() == null) ? List.of() : res.getWarnings();
        return ApiResponse.successWithWarnings(res, warnings);
    }
}
