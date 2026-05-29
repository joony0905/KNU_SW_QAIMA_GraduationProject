package com.qaima.api.StockDebugController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.StockMeta;
import com.qaima.external.StockApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health / Debug")
@SecurityRequirement(name = "bearerAuth")
public class StockDebugController {

    private final StockApiClient stockApiClient;

    @GetMapping("/api/debug/ticker-meta")
    @Operation(summary = "Debug ticker metadata")
    public Mono<ApiResponse<StockMeta>> debugTicker(
            @RequestParam String symbol
    ) {
        return stockApiClient.fetchTickerMeta(symbol);
    }
}
