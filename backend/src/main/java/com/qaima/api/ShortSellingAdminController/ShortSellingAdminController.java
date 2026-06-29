package com.qaima.api.ShortSellingAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.shortselling.FinraShortSellingSyncService;
import com.qaima.service.shortselling.FinraShortSellingSyncService.FinraShortSellingBackfillResult;
import com.qaima.service.shortselling.FinraShortSellingSyncService.FinraShortSellingDailySyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/short-selling/finra")
@Tag(name = "Admin - Short Selling", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class ShortSellingAdminController {

    private final FinraShortSellingSyncService finraShortSellingSyncService;

    @PostMapping("/sync")
    @Operation(summary = "Sync FINRA short selling data")
    public Mono<ApiResponse<FinraShortSellingDailySyncResult>> syncDaily(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return finraShortSellingSyncService.syncDaily(date)
                .map(ApiResponse::success);
    }

    @PostMapping("/backfill")
    @Operation(summary = "Backfill FINRA short selling data")
    public Mono<ApiResponse<FinraShortSellingBackfillResult>> backfill(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return finraShortSellingSyncService.backfill(from, to)
                .map(ApiResponse::success);
    }
}
