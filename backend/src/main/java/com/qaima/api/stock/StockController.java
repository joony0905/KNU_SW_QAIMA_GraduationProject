package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.StockCodeMappingDto;
import com.qaima.dto.StockDto;
import com.qaima.dto.StockMeta;
import com.qaima.external.StockClient;
import com.qaima.service.StockMappingService;
import com.qaima.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
// 프론트엔드(React)에서 호출하는 REST 컨트롤러

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockMappingService stockMappingService;
    private final StockClient stockClient;

    @GetMapping("/{stockCode}")
    public Mono<StockDto> getStock(
            @PathVariable String stockCode,
            @RequestParam(name = "exchange", required = false) String exchange
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode, exchange);
    }

    @GetMapping("/code/{stockCode}")
    public Mono<StockDto> getStockByCode(
            @PathVariable String stockCode,
            @RequestParam(name = "exchange", required = false) String exchange
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode, exchange);
    }

    @GetMapping("/normalize")
    public Mono<StockCodeMappingDto> normalizeStockCode(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.normalizeStockCodeByName(name, exchange, symbol);
    }

    @GetMapping("/normalize/candidates")
    public Mono<List<StockCodeMappingDto>> normalizeCandidates(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.listMappingsByName(name, exchange, symbol);
    }

    @GetMapping("/search")
    public Mono<List<StockCodeMappingDto>> searchStocks(@RequestParam(name = "q", required = false) String query) {
        return stockMappingService.searchStockMappings(query);
    }

    @GetMapping("/debug/ticker-meta")
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
