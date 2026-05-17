package com.qaima.api.SecAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.issuedshares.SecIssuedSharesSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sec")
public class SecAdminController {

    private final SecIssuedSharesSyncService issuedSharesSyncService;

    @PostMapping("/issued-shares/sync")
    public Mono<ApiResponse<SecIssuedSharesSyncService.BatchResult>> syncIssuedShares(
            @RequestParam(defaultValue = "300") int limit
    ) {
        return issuedSharesSyncService.syncAllMappedStocks(limit)
                .map(ApiResponse::success);
    }

    @PostMapping("/issued-shares/sync/stock")
    public Mono<ApiResponse<SecIssuedSharesSyncService.StockSyncResult>> syncIssuedSharesForStock(
            @RequestParam String stockCode
    ) {
        return issuedSharesSyncService.syncForStockCode(stockCode)
                .map(ApiResponse::success);
    }
}
