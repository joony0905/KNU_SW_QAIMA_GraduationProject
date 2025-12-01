package com.qaima.api.stock;

import com.qaima.dto.MarketStackTickersResponse;
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

    @GetMapping("/debug/ticker-meta")
    public Mono<MarketStackTickersResponse.TickerData> getTickerMeta(
            @RequestParam String symbol
    ) {
        return stockClient.fetchTickerMeta(symbol);
    }
}

