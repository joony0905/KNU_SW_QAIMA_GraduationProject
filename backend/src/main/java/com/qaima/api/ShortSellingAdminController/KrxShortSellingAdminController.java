package com.qaima.api.ShortSellingAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.shortselling.KrxShortSellingSyncService;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingBackfillResult;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingDailySyncResult;
import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingProbeResult;
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
public class KrxShortSellingAdminController {

    private final KrxShortSellingSyncService krxShortSellingSyncService;

    @PostMapping("/sync")
    public Mono<ApiResponse<KrxShortSellingDailySyncResult>> syncDaily(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return krxShortSellingSyncService.syncDaily(date)
                .map(ApiResponse::success);
    }

    @PostMapping("/probe")
    public Mono<ApiResponse<KrxShortSellingProbeResult>> probeDaily(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return krxShortSellingSyncService.probeDaily(date)
                .map(ApiResponse::success);
    }

    @PostMapping("/backfill")
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
