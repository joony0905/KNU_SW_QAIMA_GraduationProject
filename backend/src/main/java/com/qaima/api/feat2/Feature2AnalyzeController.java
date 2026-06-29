package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.credit.CreditService;
import com.qaima.service.feature2.Feature2AnalyzeService;
import com.qaima.service.report.AnalysisReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/feature2")
@Tag(name = "Feature2")
@SecurityRequirement(name = "bearerAuth")
public class Feature2AnalyzeController {

    private final Feature2AnalyzeService feature2AnalyzeService;
    private final CreditService creditService;
    private final AnalysisReportService analysisReportService;

    @PostMapping("/analyze")
    @Operation(summary = "Run Feature2 analysis", description = "Runs Feature2 analysis for a stock using market, macro, peer, and short-selling context.")
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(
            Authentication authentication,
            @Valid @RequestBody Feature2AnalyzeRequestDto req
    ) {
        log.info("[Feature2AnalyzeController][request] stockCode={}, freq={}, window={}, llmVendor={}",
                req != null ? req.getStockCode() : null,
                req != null ? req.getFreq() : null,
                req != null ? req.getWindow() : null,
                req != null ? req.getLlmVendor() : null);

        Long userId = currentUserId(authentication);
        String referenceId = UUID.randomUUID().toString();

        return creditService.useFeature2(userId, referenceId)
                .then(feature2AnalyzeService.analyze(req)
                        .flatMap(res -> attachReportId(userId, req, res, wrapWithWarnings(res)))
                        .onErrorResume(ex -> {
                            log.error("[Feature2] analysis failed. cause={}", ex.getMessage(), ex);
                            return creditService.refundFeature2(userId, referenceId, ErrorCode.FEATURE2_ANALYZE_FAILED.code())
                                    .thenReturn(wrapWithWarnings(fallbackResponse()));
                        }));
    }

    private Mono<ApiResponse<Feature2AnalyzeResponseDto>> attachReportId(
            Long userId,
            Feature2AnalyzeRequestDto request,
            Feature2AnalyzeResponseDto data,
            ApiResponse<Feature2AnalyzeResponseDto> response
    ) {
        if (data == null || response == null || response.getMeta() == null) {
            return Mono.just(response);
        }
        return analysisReportService.createFeature2(userId, request, data)
                .map(reportId -> {
                    response.getMeta().setReportId(reportId);
                    return response;
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature2] report snapshot save failed. userId={}, cause={}", userId, ex.getMessage(), ex);
                    response.getMeta().addWarning("REPORT_SAVE_FAILED");
                    return Mono.just(response);
                });
    }

    private ApiResponse<Feature2AnalyzeResponseDto> wrapWithWarnings(Feature2AnalyzeResponseDto res) {
        List<String> warnings = (res == null || res.getWarnings() == null) ? List.of() : res.getWarnings();
        return ApiResponse.successWithWarnings(res, warnings);
    }

    private Feature2AnalyzeResponseDto fallbackResponse() {
        return Feature2AnalyzeResponseDto.builder()
                .metrics(Feature2MetricsDto.empty())
                .explain(null)
                .warnings(List.of(Feat2WarningCode.FEAT2_INTERNAL_ERROR.name()))
                .build();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }
}
