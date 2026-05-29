package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.StockCodeMappingDto;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockResponseDto;
import com.qaima.service.stock.MarketSnapshotService;
import com.qaima.service.stock.StockMappingService;
import com.qaima.service.stock.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/stocks")
@Tag(name = "Stock")
@SecurityRequirements
public class StockController {

    private final StockService stockService;
    private final StockMappingService stockMappingService;
    private final MarketSnapshotService marketSnapshotService;

    @GetMapping("/{stockId}")
    @Operation(summary = "Get stock by id")
    public Mono<ApiResponse<StockResponseDto>> getStock(@PathVariable Long stockId) {
        return stockService.getStockWithRealtime(stockId)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    /**
     * code 기반 조회
     * - DB 없으면 생성
     * - 내부적으로 ticker-meta + inquire-price 수행
     */
    @GetMapping("/code/{stockCode}")
    @Operation(summary = "Get stock by code")
    public Mono<ApiResponse<StockResponseDto>> getOrCreateStockByCode(
            @PathVariable String stockCode
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    @GetMapping("/code/{stockCode}/market-snapshot")
    @Operation(summary = "Get latest market snapshot")
    public Mono<ApiResponse<MarketSnapshotDto>> getLatestMarketSnapshot(
            @PathVariable String stockCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return stockService.getOrCreateStockByCode(stockCode)
                .flatMap(stock -> marketSnapshotService.getLatestDto(stock, asOfDate))
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.success(null)));
    }

    @GetMapping("/normalize")
    @Operation(summary = "Normalize stock code")
    public Mono<ApiResponse<StockCodeMappingDto>> normalizeStockCode(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.normalizeStockCodeByName(name, exchange, symbol)
                .map(ApiResponse::success);
    }

    @GetMapping("/normalize/candidates")
    @Operation(summary = "List stock mapping candidates")
    public Mono<ApiResponse<List<StockCodeMappingDto>>> normalizeCandidates(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.listMappingsByName(name, exchange, symbol)
                .map(ApiResponse::success);
    }

    @GetMapping("/search")
    @Operation(summary = "Search stocks", description = "Searches stock mappings by ticker, company name, and registered aliases.")
    public Mono<ApiResponse<List<StockCodeMappingDto>>> searchStocks(
            @RequestParam(name = "q", required = false) String query
    ) {
        return stockMappingService.searchStockMappings(query)
                .map(ApiResponse::success);
    }

    private StockResponseDto toResponse(StockDto s) {
        return new StockResponseDto(
                s.getStockId(),
                s.getStockCode(),
                s.getCompanyName(),
                s.getPrice(),
                s.getChangeRate(),
                s.getExchangeCode(),
                s.getCurrency()
        );
    }
}
