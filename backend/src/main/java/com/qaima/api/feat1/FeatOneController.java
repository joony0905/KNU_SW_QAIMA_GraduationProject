package com.qaima.api.feat1;

import com.qaima.common.ApiResponse;
import com.qaima.domain.Freq;
import com.qaima.dto.FeatOneResponseDataDto;
import com.qaima.service.FeatOneService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

/**
 * 기능1 – 심층 종목 분석
 * 예시:
 * GET /api/v1/feature1/stock?code=005930&freq=DAY&from=2025-01-01T00:00:00+09:00&to=2025-02-01T00:00:00+09:00
 */

@RestController
@RequestMapping("/api/v1/feature1/stock")
@RequiredArgsConstructor
public class FeatOneController {

    private final FeatOneService featOneService;

    @GetMapping
    public Mono<ApiResponse<FeatOneResponseDataDto>> getFeatOne(
            @RequestParam String stockCode,
            @RequestParam Freq freq,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return featOneService.getFeatOneData(stockCode, freq, from, to)
                .map(ApiResponse::success)
                .onErrorResume(ex -> {
                    // log.error("FeatOne failed", ex);
                    return Mono.just(ApiResponse.error("INTERNAL_ERROR", ex.getMessage()));
                });
    }
}
