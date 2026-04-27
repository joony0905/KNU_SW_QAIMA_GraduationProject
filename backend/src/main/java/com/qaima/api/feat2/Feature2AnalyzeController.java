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
public class Feature2AnalyzeController {

    private final Feature2AnalyzeService feature2AnalyzeService;
    private final CreditService creditService;

    @PostMapping("/analyze")
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(
            Authentication authentication,
            @Valid @RequestBody Feature2AnalyzeRequestDto req
    ) {
        log.info("[Feature2AnalyzeController][request] stockCode={}, freq={}, window={}",
                req != null ? req.getStockCode() : null,
                req != null ? req.getFreq() : null,
                req != null ? req.getWindow() : null);

        Long userId = currentUserId(authentication);
        String referenceId = UUID.randomUUID().toString();

        return creditService.useFeature2(userId, referenceId)
                .then(feature2AnalyzeService.analyze(req)
                        .map(this::wrapWithWarnings)
                        .onErrorResume(ex -> {
                            log.error("[Feature2] analysis failed. cause={}", ex.getMessage(), ex);
                            return creditService.refundFeature2(userId, referenceId, "FEATURE2_ANALYZE_FAILED")
                                    .thenReturn(wrapWithWarnings(fallbackResponse()));
                        }));
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
