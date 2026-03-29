package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.dto.featone.FeatOneAnalyzeRequestDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.service.featone.FeatOneResult;
import com.qaima.service.featone.FeatOneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api/v1/feature1")
@RequiredArgsConstructor
public class FeatOneController {

    private final FeatOneService featOneService;

    @PostMapping("/analyze")
    public Mono<ApiResponse<FeatOneAnalysisResponseDto>> analyze(
            @RequestBody FeatOneAnalyzeRequestDto request
    ) {
        return featOneService.getFeatOneData(
                        request.getStockCode(),
                        request.getFreq(),
                        request.getFrom(),
                        request.getTo(),
                        request.getMarketDivCode(),
                        request.getIncludeExplain()
                )
                .map(this::toApiResponse)
                .onErrorResume(ex -> Mono.just(ApiResponse.internalError(
                        "FEATURE1_ANALYZE_FAILED",
                        "Feature1 분석 처리 실패: " + safeMessage(ex.getMessage())
                )));
    }

    private ApiResponse<FeatOneAnalysisResponseDto> toApiResponse(FeatOneResult result) {
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

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "n/a";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
