package com.qaima.api.feat3;

import com.qaima.common.ApiResponse;
import com.qaima.dto.feature3.Feature3PriceSeriesResponseDto;
import com.qaima.service.feature3.Feature3PriceSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/feature3/market-data")
@RequiredArgsConstructor
public class Feature3MarketDataController {

    private final Feature3PriceSeriesService feature3PriceSeriesService;

    @GetMapping("/price-series")
    public Mono<ApiResponse<Feature3PriceSeriesResponseDto>> getPriceSeries(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "ADJUSTED_CLOSE") String requestedPriceBasis,
            @RequestParam(defaultValue = "252") Integer lookbackTradingDays,
            @RequestParam(defaultValue = "370") Integer fetchCalendarDays
    ) {
        return feature3PriceSeriesService.getPriceSeries(
                        stockCode,
                        requestedPriceBasis,
                        lookbackTradingDays == null ? 252 : lookbackTradingDays,
                        fetchCalendarDays == null ? 370 : fetchCalendarDays
                )
                .map(data -> {
                    java.util.List<String> warnings = data.warnings().stream()
                            .map(Feature3PriceSeriesResponseDto.Warning::code)
                            .toList();
                    if (warnings.isEmpty()) {
                        return ApiResponse.success(data);
                    }
                    return ApiResponse.successWithWarnings(data, warnings);
                });
    }
}
