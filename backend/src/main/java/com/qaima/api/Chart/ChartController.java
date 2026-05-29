package com.qaima.api.Chart;

import com.qaima.common.ApiResponse;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.dto.candle.CandleSeriesResponse;
import com.qaima.service.candle.CandleTimePolicy;
import com.qaima.service.candle.CandleLoadResult;
import com.qaima.service.chart.ChartService;
import com.qaima.mapper.CandleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
@Tag(name = "Chart")
@SecurityRequirements
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/candles")
    @Operation(summary = "Get candle chart data", description = "Returns candle series data for a stock and frequency within the requested time range.")
    public Mono<ApiResponse<CandleSeriesResponse>> getCandles(
            @RequestParam String stockCode,
            @RequestParam Freq freq,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) Integer limit
    ) {
        Mono<CandleLoadResult> resultMono =
                (limit != null)
                        ? chartService.getCandlesBefore(stockCode, freq, to, limit)
                        : chartService.getCandles(stockCode, freq, from, to);

        return resultMono.map(result -> toApiResponse(stockCode, freq, result));
    }

    private ApiResponse<CandleSeriesResponse> toApiResponse(
            String stockCode,
            Freq freq,
            CandleLoadResult result
    ) {
        CandleSeriesResponse data = CandleSeriesResponse.builder()
                .stockCode(stockCode)
                .freq(freq.name())
                .timezone(CandleTimePolicy.timezoneLabel(CandleTimePolicy.DEFAULT_TRADING_ZONE))
                .source(result.getSource().name())
                .data(CandleMapper.toSeries(result.getCandles()))
                .build();

        if (result.getCandles().isEmpty() || result.getSource() == CandleSource.EMPTY) {
            return ApiResponse.successWithWarning(data, "NO_DATA");
        }

        if (result.getSource() == CandleSource.MARKETSTACK) {
            return ApiResponse.successWithWarning(data, "CHART_FALLBACK_TO_GLOBAL");
        }

        return ApiResponse.success(data);
    }
}
