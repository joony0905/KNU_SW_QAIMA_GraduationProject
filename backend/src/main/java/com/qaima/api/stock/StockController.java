package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.StockCodeMappingDto;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockResponseDto;
import com.qaima.service.stock.StockMappingService;
import com.qaima.service.stock.StockService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockService stockService;
    private final StockMappingService stockMappingService;

    @GetMapping("/id/{stockId}")
    public Mono<ApiResponse<StockResponseDto>> getStockById(@PathVariable Long stockId) {
        return stockService.getStockWithRealtime(stockId)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    @GetMapping("/{stockCode}")
    public Mono<ApiResponse<StockResponseDto>> getStockByCode(
            @PathVariable String stockCode
    ) {
        return stockService.getStockWithRealtimeByCode(stockCode)
                .map(this::toResponse)
                .map(ApiResponse::success);
    }

    // legacy path compatibility
    @GetMapping("/code/{stockCode}")
    public Mono<ApiResponse<StockResponseDto>> getStockByCodeLegacy(
            @PathVariable String stockCode
    ) {
        return getStockByCode(stockCode);
    }

    @GetMapping("/normalize")
    public Mono<ApiResponse<StockCodeMappingDto>> normalizeStockCode(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.normalizeStockCodeByName(name, exchange, symbol)
                .map(ApiResponse::success);
    }

    @GetMapping("/normalize/candidates")
    public Mono<ApiResponse<List<StockCodeMappingDto>>> normalizeCandidates(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "symbol", required = false) String symbol
    ) {
        return stockMappingService.listMappingsByName(name, exchange, symbol)
                .map(ApiResponse::success);
    }

    @GetMapping("/search")
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
