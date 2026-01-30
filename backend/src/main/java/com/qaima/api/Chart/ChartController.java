package com.qaima.api.Chart;

import com.qaima.common.ApiResponse;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.dto.CandleSeriesResponse;
import com.qaima.mapper.CandleMapper;
import com.qaima.service.CandleLoadResult;
import com.qaima.service.ChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/candles")
    public Mono<ApiResponse<CandleSeriesResponse>> getCandles(
            @RequestParam String stockCode,
            @RequestParam Freq freq,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to
    ) {
        return chartService.getCandles(stockCode, freq, from, to) // Mono<CandleLoadResult>
                .map(result -> toApiResponse(stockCode, freq, result));
    }

    private ApiResponse<CandleSeriesResponse> toApiResponse(
            String stockCode,
            Freq freq,
            CandleLoadResult result
    ) {
        CandleSeriesResponse data = CandleSeriesResponse.builder()
                .stockCode(stockCode)
                .freq(freq.name())
                .timezone("UTC")
                .source(result.getSource().name())
                .data(CandleMapper.toSeries(result.getCandles()))
                .build();

        if (result.getCandles().isEmpty() || result.getSource() == CandleSource.EMPTY) {
            return ApiResponse.successWithWarning(data, "NO_DATA");
        }

        // 폴백시 warning
        if (result.getSource() == CandleSource.MARKETSTACK) {
             return ApiResponse.successWithWarning(data, "CHART_FALLBACK_TO_GLOBAL");
        }

        return ApiResponse.success(data);
    }
}
