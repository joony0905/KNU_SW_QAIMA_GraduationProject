package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.dto.FeatOneAnalyzeRequestDto;
import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.service.FeatOneResult;
import com.qaima.service.FeatOneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
                .map(result -> toApiResponse(result));
    }

    private ApiResponse<FeatOneAnalysisResponseDto> toApiResponse(FeatOneResult result) {
        if (result.isChartUnavailable()) {
            return ApiResponse.successWithWarning(
                    result.getData(),
                    "CHART_DATA_UNAVAILABLE"
            );
        }
        return ApiResponse.success(result.getData());
    }
}
