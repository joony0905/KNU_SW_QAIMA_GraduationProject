package com.qaima.api.StockSyncController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.external.StockApiClient;
import com.qaima.service.stock.StockSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class StockSyncController {

    private final StockApiClient StockApiClient;
    private final StockSyncService StockSyncService;

    /**
     * (★실행 버튼★)
     * Marketstack API에서 Ticker를 받아와 DB에 동기화(Upsert)
     * POST /api/v1/sync/tickers
     */
    @PostMapping("/tickers")
    public Mono<ApiResponse<String>> syncTickers() {
        return StockApiClient.fetchTickers()
                .flatMap(response -> {
                    if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                        List<MarketStackTickersResponse.TickerData> tickers = response.getData();

                        return StockSyncService.syncMarketStackTickers(tickers)
                                .thenReturn(ApiResponse.success(tickers.size() + "개의 Ticker 동기화 완료"));
                    }
                    return Mono.just(ApiResponse.error("SYNC_ERROR", "API에서 Ticker를 가져오지 못했습니다."));
                });
    }
}
