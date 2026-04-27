package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.featone.FeatOneAnalyzeRequestDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.service.credit.CreditService;
import com.qaima.service.featone.FeatOneResult;
import com.qaima.service.featone.FeatOneService;
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
public class FeatOneController {

    private final FeatOneService featOneService;
    private final CreditService creditService;

    @PostMapping("/analyze")
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
                request.getLlmVendor()
        );

        return creditService.useFeature1(userId, referenceId)
                .then(analysis
                        .map(this::toApiResponse)
                        .onErrorResume(ex -> creditService
                                .refundFeature1(userId, referenceId, "FEATURE1_ANALYZE_FAILED")
                                .thenReturn(feature1ErrorResponse(ex))))
                .onErrorResume(ErrorException.class, ex -> {
                    if (ex.getErrorCode() == ErrorCode.INSUFFICIENT_CREDIT) {
                        return Mono.error(ex);
                    }
                    return Mono.just(feature1ErrorResponse(ex));
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
                "FEATURE1_ANALYZE_FAILED",
                "Feature1 analysis failed: " + safeMessage(ex.getMessage())
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
