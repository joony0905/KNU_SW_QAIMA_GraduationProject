package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockResponseDto;
import com.qaima.external.StockClient;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockService stockService;

    @GetMapping("/{stockId}")
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
    public Mono<ApiResponse<StockResponseDto>> getOrCreateStockByCode(
            @PathVariable String stockCode
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode)
                .map(this::toResponse)
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