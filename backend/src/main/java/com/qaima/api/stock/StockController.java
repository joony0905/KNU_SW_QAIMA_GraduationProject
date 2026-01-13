package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.StockMeta;
import com.qaima.dto.StockDto;
import com.qaima.external.StockClient;
import com.qaima.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
//React에서 호출하는 Rest

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockClient stockClient;

    @GetMapping("/{stockId}")
    public Mono<StockDto> getStock(@PathVariable Long stockId) {
        return stockService.getStockWithRealtime(stockId);
    }

    @GetMapping("/code/{stockCode}")
    public Mono<StockDto> getStockByCode(@PathVariable String stockCode) {
        return stockService.getStockWithRealtimeByCode(stockCode);
    }


    @GetMapping("/debug/ticker-meta")
    public Mono<ApiResponse<StockMeta>> getTickerMeta(
            @RequestParam String symbol
    ) {
        return stockClient.fetchTickerMeta(symbol);
    }
}
