package com.qaima.api.ShortSellingAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.shortselling.KrxShortSellingSyncService;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingBackfillResult;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingDailySyncResult;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingProbeResult;
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
@RequestMapping("/api/v1/admin/short-selling/krx")
@Tag(name = "Admin - Short Selling", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class KrxShortSellingAdminController {

    private final KrxShortSellingSyncService krxShortSellingSyncService;

    @PostMapping("/sync")
    @Operation(summary = "Sync KRX short selling data")
    public Mono<ApiResponse<KrxShortSellingDailySyncResult>> syncDaily(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return krxShortSellingSyncService.syncDaily(date)
                .map(ApiResponse::success);
    }

    @PostMapping("/probe")
    @Operation(summary = "Probe KRX short selling endpoint")
    public Mono<ApiResponse<KrxShortSellingProbeResult>> probeDaily(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return krxShortSellingSyncService.probeDaily(date)
                .map(ApiResponse::success);
    }

    @PostMapping("/backfill")
    @Operation(summary = "Backfill KRX short selling data")
    public Mono<ApiResponse<KrxShortSellingBackfillResult>> backfill(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return krxShortSellingSyncService.backfill(from, to)
                .map(ApiResponse::success);
    }
}
