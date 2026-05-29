package com.qaima.api.MarketSnapshotAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.stock.MarketSnapshotBackfillResult;
import com.qaima.service.stock.MarketSnapshotBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/market-snapshots")
@Tag(name = "Admin - Market Data", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class MarketSnapshotAdminController {

    private final MarketSnapshotBackfillService backfillService;

    @PostMapping("/backfill")
    @Operation(summary = "Backfill market snapshot")
    public Mono<ApiResponse<MarketSnapshotBackfillResult>> backfillOne(
            @RequestParam String stockCode,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return backfillService.backfillOne(stockCode, exchange, asOfDate, force)
                .map(ApiResponse::success);
    }

    @PostMapping("/backfill/missing")
    @Operation(summary = "Backfill missing market snapshots")
    public Mono<ApiResponse<List<MarketSnapshotBackfillResult>>> backfillMissing(
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long delayMs,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return backfillService.backfillMissing(exchange, limit, delayMs, asOfDate)
                .map(ApiResponse::success);
    }
}
