package com.qaima.api.StockSyncController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.external.StockApiClient;
import com.qaima.service.stock.StockSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Admin - Sync", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class StockSyncController {

    private final StockApiClient stockApiClient;
    private final StockSyncService stockSyncService;

    @PostMapping("/tickers")
    @Operation(summary = "Sync market stack tickers")
    public Mono<ApiResponse<String>> syncTickers() {
        return stockApiClient.fetchTickers()
                .flatMap(response -> {
                    if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                        List<MarketStackTickersResponse.TickerData> tickers = response.getData();

                        return stockSyncService.syncMarketStackTickers(tickers)
                                .thenReturn(ApiResponse.success(tickers.size() + "媛쒖쓽 Ticker ?숆린???꾨즺"));
                    }
                    return Mono.just(ApiResponse.<String>error("SYNC_ERROR", "API?먯꽌 Ticker瑜?媛?몄삤吏 紐삵뻽?듬땲??"));
                })
                .onErrorResume(ex -> Mono.just(ApiResponse.<String>error(
                        "SYNC_ERROR",
                        "Ticker ?숆린???ㅽ뙣: " + safeMessage(ex.getMessage())
                )));
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "n/a";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
