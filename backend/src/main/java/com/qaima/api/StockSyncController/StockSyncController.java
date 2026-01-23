package com.qaima.api.StockSyncController;

import com.qaima.common.ApiResponse;
import com.qaima.external.StockApiClient;
import com.qaima.service.StockSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


/**
 * (★실행 버튼★)
 * Marketstack API에서 Ticker를 받아와 DB에 동기화(Upsert)
 * POST /api/v1/sync/tickers
 */

@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
public class StockSyncController {

    private final StockApiClient stockApiClient;
    private final StockSyncService stockSyncService;

    @PostMapping("/tickers")
    public Mono<ApiResponse<String>> syncTickers() {
        return stockApiClient.fetchTickers()
                .switchIfEmpty(Mono.error(new IllegalStateException("API에서 Ticker를 가져오지 못했습니다.")))
                .flatMap(response -> {
                    if (response.getData() == null || response.getData().isEmpty()) {
                        return Mono.error(new IllegalStateException("API에서 Ticker를 가져오지 못했습니다."));
                    }
                    return stockSyncService.syncMarketStackTickers(response.getData())
                            .thenReturn(ApiResponse.success(response.getData().size() + "개의 Ticker 동기화 완료"));
                });

    }
}
