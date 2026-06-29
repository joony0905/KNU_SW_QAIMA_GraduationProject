package com.qaima.api.StockAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.stock.UsStockMasterSyncService;
import com.qaima.service.stock.UsStockMasterSyncService.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stocks/us")
@Tag(name = "Admin - Sync", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class UsStockMasterAdminController {

    private final UsStockMasterSyncService usStockMasterSyncService;

    @PostMapping("/sec/sync")
    @Operation(summary = "Sync US stock master from SEC")
    public Mono<ApiResponse<SyncResult>> syncSecNasdaqNyse() {
        return usStockMasterSyncService.syncNasdaqNyseFromSec()
                .map(ApiResponse::success);
    }
}
