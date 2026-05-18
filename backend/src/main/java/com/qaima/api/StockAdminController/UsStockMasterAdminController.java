package com.qaima.api.StockAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.stock.UsStockMasterSyncService;
import com.qaima.service.stock.UsStockMasterSyncService.SyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stocks/us")
public class UsStockMasterAdminController {

    private final UsStockMasterSyncService usStockMasterSyncService;

    @PostMapping("/sec/sync")
    public Mono<ApiResponse<SyncResult>> syncSecNasdaqNyse() {
        return usStockMasterSyncService.syncNasdaqNyseFromSec()
                .map(ApiResponse::success);
    }
}
