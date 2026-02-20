package com.qaima.api.MetaController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.StockMeta;
import com.qaima.external.StockClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final StockClient stockClient;

    @GetMapping("/tickers")
    public Mono<ApiResponse<StockMeta>> getTickerMeta(
            @RequestParam(name = "stockCode") String stockCode
    ) {
        return stockClient.fetchTickerMeta(resolveStockCode(stockCode));
    }

    private String resolveStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        return stockCode;
    }
}
