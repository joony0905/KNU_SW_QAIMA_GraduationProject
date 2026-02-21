package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.StockDto;
import com.qaima.dto.StockMeta;
import com.qaima.dto.StockResponseDto;
import com.qaima.external.StockClient;
import com.qaima.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockClient stockClient;

    @GetMapping("/{stockId}")
    public Mono<ApiResponse<StockResponseDto>> getStock(@PathVariable Long stockId) {
        return stockService.getStockWithRealtime(stockId)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    @GetMapping("/code/{stockCode}")
    public Mono<ApiResponse<StockResponseDto>> getStockByCode(
            @PathVariable String stockCode
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    /*
    @GetMapping("/debug/ticker-meta")
    public Mono<ApiResponse<StockMeta>> getTickerMeta(
            @RequestParam String symbol
    ) {
        return stockClient.fetchTickerMeta(symbol)
                .map(ApiResponse::success);
    }
    */

    /**
     * 내부 StockDto → 프론트 계약 DTO response
     */
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