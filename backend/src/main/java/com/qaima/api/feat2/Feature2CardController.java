package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.domain.Freq;
import com.qaima.dto.feature2.Feature2BaseRateSeriesPointDto;
import com.qaima.dto.feature2.Feature2InvestorFlowDto;
import com.qaima.dto.feature2.Feature2MacroRatesDto;
import com.qaima.dto.feature2.Feature2MacroRatesSeriesDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.feature2.Feature2RelatedStockCardDto;
import com.qaima.dto.feature2.Feature2ShortSellingSeriesPointDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.service.feature2.Feature2CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feature2/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feature2")
@SecurityRequirements
public class Feature2CardController {

    private final Feature2CardService feature2CardService;

    @GetMapping("/base-rate")
    @Operation(summary = "Get base rate card")
    public Mono<ApiResponse<Feature2MetricsDto.BaseRateMetrics>> getBaseRate() {
        return feature2CardService.loadBaseRate()
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] base-rate failed. cause={}", ex.getMessage(), ex));
    }

    @GetMapping("/base-rate-series")
    @Operation(summary = "Get base rate series card")
    public Mono<ApiResponse<List<Feature2BaseRateSeriesPointDto>>> getBaseRateSeries(
            @RequestParam(defaultValue = "365") Integer limit
    ) {
        int safeLimit = limit == null ? 365 : limit;
        return feature2CardService.loadBaseRateSeries(safeLimit)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] base-rate-series failed. cause={}", ex.getMessage(), ex));
    }

    @GetMapping("/macro-rates")
    @Operation(summary = "Get macro rates card")
    public Mono<ApiResponse<Feature2MacroRatesDto>> getMacroRates() {
        return feature2CardService.loadMacroRates()
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] macro-rates failed. cause={}", ex.getMessage(), ex));
    }

    @GetMapping("/macro-rates-series")
    @Operation(summary = "Get macro rates series card")
    public Mono<ApiResponse<Feature2MacroRatesSeriesDto>> getMacroRatesSeries(
            @RequestParam(defaultValue = "120") Integer limit
    ) {
        int safeLimit = limit == null ? 120 : limit;
        return feature2CardService.loadMacroRatesSeries(safeLimit)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] macro-rates-series failed. cause={}", ex.getMessage(), ex));
    }

    @GetMapping("/industry-index")
    @Operation(summary = "Get industry index card")
    public Mono<ApiResponse<IndustryIndexBlockDto>> getIndustryIndex(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "ONE_D") Freq freq,
            @RequestParam(defaultValue = "120") Integer window
    ) {
        return feature2CardService.loadIndustryIndex(stockCode, freq, window)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] industry-index failed. stockCode={}, cause={}",
                        stockCode, ex.getMessage(), ex));
    }

    @GetMapping("/short-selling")
    @Operation(summary = "Get short selling card")
    public Mono<ApiResponse<Feature2MetricsDto.ShortSellingMetrics>> getShortSelling(
            @RequestParam String stockCode
    ) {
        return feature2CardService.loadShortSelling(stockCode)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] short-selling failed. stockCode={}, cause={}",
                        stockCode, ex.getMessage(), ex));
    }

    @GetMapping("/short-selling-series")
    @Operation(summary = "Get short selling series card")
    public Mono<ApiResponse<List<Feature2ShortSellingSeriesPointDto>>> getShortSellingSeries(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "60") Integer limit
    ) {
        int safeLimit = limit == null ? 60 : limit;
        return feature2CardService.loadShortSellingSeries(stockCode, safeLimit)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] short-selling-series failed. stockCode={}, cause={}",
                        stockCode, ex.getMessage(), ex));
    }

    @GetMapping("/related-stocks")
    @Operation(summary = "Get related stocks card")
    public Mono<ApiResponse<List<Feature2RelatedStockCardDto>>> getRelatedStocks(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "30") Integer limit
    ) {
        int safeLimit = limit == null ? 30 : limit;
        return feature2CardService.loadRelatedStocks(stockCode, safeLimit)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] related-stocks failed. stockCode={}, cause={}",
                        stockCode, ex.getMessage(), ex));
    }

    @GetMapping("/investor-flow")
    @Operation(summary = "Get investor flow card")
    public Mono<ApiResponse<Feature2InvestorFlowDto>> getInvestorFlow(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "60") Integer limit
    ) {
        int safeLimit = limit == null ? 60 : limit;
        return feature2CardService.loadInvestorFlow(stockCode, safeLimit)
                .map(result -> ApiResponse.successWithWarnings(
                        result.data(),
                        result.meta() == null || result.meta().getWarnings() == null
                                ? List.of()
                                : result.meta().getWarnings()
                ))
                .doOnError(ex -> log.error("[Feature2CardController] investor-flow failed. stockCode={}, cause={}",
                        stockCode, ex.getMessage(), ex));
    }
}
