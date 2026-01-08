package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.domain.Freq;
import com.qaima.dto.FeatOneResponseDataDto;
import com.qaima.service.FeatOneResult;
import com.qaima.service.FeatOneService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/feature1")
@RequiredArgsConstructor
public class FeatOneController {

    private final FeatOneService featOneService;

    @GetMapping
    public Mono<ApiResponse<FeatOneResponseDataDto>> getFeatOne(
            @RequestParam String stockCode,
            @RequestParam Freq freq,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to
    ) {
        return featOneService.getFeatOneData(stockCode, freq, from, to)
                .map(result -> toApiResponse(result));
    }

    private ApiResponse<FeatOneResponseDataDto> toApiResponse(FeatOneResult result) {
        if (result.isChartUnavailable()) {
            return ApiResponse.successWithWarning(
                    result.getData(),
                    "CHART_DATA_UNAVAILABLE"
            );
        }
        return ApiResponse.success(result.getData());
    }
}
