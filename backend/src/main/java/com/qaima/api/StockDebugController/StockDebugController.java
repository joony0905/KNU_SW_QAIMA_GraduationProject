package com.qaima.api.StockDebugController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.StockMeta;
import com.qaima.external.StockApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class StockDebugController {

    private final StockApiClient stockApiClient;

    @GetMapping("/api/debug/ticker-meta")
    public Mono<ApiResponse<StockMeta>> debugTicker(
            @RequestParam String symbol
    ) {
        return stockApiClient.fetchTickerMeta(symbol);
    }
}
