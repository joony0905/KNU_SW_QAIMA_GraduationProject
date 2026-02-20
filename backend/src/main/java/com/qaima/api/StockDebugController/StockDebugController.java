package com.qaima.api.StockDebugController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.StockMeta;
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
            @RequestParam(name = "stockCode") String stockCode
    ) {
        return stockApiClient.fetchTickerMeta(resolveStockCode(stockCode));
    }

    private String resolveStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        return stockCode;
    }
}
