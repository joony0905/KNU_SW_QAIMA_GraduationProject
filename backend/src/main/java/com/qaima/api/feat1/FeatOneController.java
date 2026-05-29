package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.featone.FeatOneAnalyzeRequestDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.service.credit.CreditService;
import com.qaima.service.featone.FeatOneResult;
import com.qaima.service.featone.FeatOneService;
import com.qaima.service.feature3.Feature3OverlayService;
import com.qaima.service.report.AnalysisReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
@RequestMapping("/api/v1/feature1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feature1")
@SecurityRequirement(name = "bearerAuth")
public class FeatOneController {

    private final FeatOneService featOneService;
    private final CreditService creditService;
    private final Feature3OverlayService feature3OverlayService;
    private final AnalysisReportService analysisReportService;

    @PostMapping("/analyze")
    @Operation(summary = "Run Feature1 analysis", description = "Runs Feature1 stock analysis and stores a report snapshot when possible.")
    public Mono<ApiResponse<FeatOneAnalysisResponseDto>> analyze(
            Authentication authentication,
            @RequestBody FeatOneAnalyzeRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        String referenceId = UUID.randomUUID().toString();

        Mono<FeatOneResult> analysis = featOneService.getFeatOneData(
                request.getStockCode(),
                request.getFreq(),
                request.getFrom(),
                request.getTo(),
                request.getMarketDivCode(),
                request.getIncludeExplain(),
                request.getLlmVendor(),
                request.getInvestLevel(),
                request.getLanguageCode()
        );

        return creditService.useFeature1(userId, referenceId)
                .then(analysis
                        .flatMap(result -> feature3OverlayService
                                .cacheFeature1Metrics(
                                        request.getStockCode(),
                                        result.getData() != null ? result.getData().getMetrics() : null
                                )
                                .thenReturn(result))
                        .flatMap(result -> attachReportId(userId, request, result.getData(), toApiResponse(result)))
                        .onErrorResume(ex -> creditService
                                .refundFeature1(userId, referenceId, ErrorCode.FEATURE1_ANALYZE_FAILED.code())
                                .thenReturn(feature1ErrorResponse(ex))));
    }

    private Mono<ApiResponse<FeatOneAnalysisResponseDto>> attachReportId(
            Long userId,
            FeatOneAnalyzeRequestDto request,
            FeatOneAnalysisResponseDto data,
            ApiResponse<FeatOneAnalysisResponseDto> response
    ) {
        if (data == null || response == null || response.getMeta() == null) {
            return Mono.just(response);
        }
        return analysisReportService.createFeature1(userId, request, data)
                .map(reportId -> {
                    response.getMeta().setReportId(reportId);
                    return response;
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature1] report snapshot save failed. userId={}, cause={}", userId, ex.getMessage(), ex);
                    response.getMeta().addWarning("REPORT_SAVE_FAILED");
                    return Mono.just(response);
                });
    }

    private ApiResponse<FeatOneAnalysisResponseDto> toApiResponse(FeatOneResult result) {
        log.info("[FeatOneController] final ApiResponse.data before wrap={}", result != null ? result.getData() : null);
        List<String> warnings = new ArrayList<>();
        if (result.getData() != null && result.getData().getWarnings() != null) {
            warnings.addAll(result.getData().getWarnings());
        }
        if (result.isChartUnavailable()) {
            warnings.add("CHART_DATA_UNAVAILABLE");
        }
        List<String> dedupedWarnings = new ArrayList<>(new LinkedHashSet<>(warnings));
        return ApiResponse.successWithWarnings(result.getData(), dedupedWarnings);
    }

    private ApiResponse<FeatOneAnalysisResponseDto> feature1ErrorResponse(Throwable ex) {
        return ApiResponse.internalError(
                ErrorCode.FEATURE1_ANALYZE_FAILED.code(),
                ErrorCode.FEATURE1_ANALYZE_FAILED.defaultMessage() + ": " + safeMessage(ex.getMessage())
        );
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "n/a";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }
}
