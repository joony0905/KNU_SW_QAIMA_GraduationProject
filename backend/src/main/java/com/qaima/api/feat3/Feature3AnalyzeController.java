package com.qaima.api.feat3;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.feature3.PortfolioAnalyzeRequestDto;
import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import com.qaima.external.AnalysisApiClient;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/feature3")
public class Feature3AnalyzeController {

    private final AnalysisApiClient analysisApiClient;

    @PostMapping("/analysis")
    public Mono<ApiResponse<PortfolioAnalyzeResponseDto>> analyze(
            Authentication authentication,
            @Valid @RequestBody PortfolioAnalyzeRequestDto req
    ) {
        currentUserId(authentication);
        log.info("[Feature3AnalyzeController][request] holdings={}, options={}, riskGamma={}",
                req.holdings() != null ? req.holdings().size() : 0,
                req.options(),
                req.riskGamma());

        return analysisApiClient.requestPortfolioAnalysis(req)
                .map(ApiResponse::success);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }
}
