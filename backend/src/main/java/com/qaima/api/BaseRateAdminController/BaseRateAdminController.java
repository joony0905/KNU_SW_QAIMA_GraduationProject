package com.qaima.api.BaseRateAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.BaseRate;
import com.qaima.service.baserate.BaseRateSyncService;
import com.qaima.service.baserate.FredBaseRateSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/base-rate")
@Tag(name = "Admin - Macro", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class BaseRateAdminController {

    private final BaseRateSyncService baseRateSyncService;
    private final FredBaseRateSyncService fredBaseRateSyncService;

    // 최신 한국은행 기준금리를 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/base-rate/sync/latest"
    @PostMapping("/sync/latest")
    @Operation(summary = "Sync latest Korean base rate")
    public Mono<ApiResponse<BaseRateSyncResult>> syncLatest() {
        return baseRateSyncService.syncLatest()
                .map(BaseRateSyncResult::from)
                .map(ApiResponse::success);
    }

    // 지정 기간의 한국은행 기준금리를 과거 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/base-rate/backfill?from=2020-01-01&to=2026-04-30"
    @PostMapping("/backfill")
    @Operation(summary = "Backfill Korean base rates")
    public Mono<ApiResponse<BaseRateBackfillResult>> backfill(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return baseRateSyncService.backfillDaily(from, to)
                .map(rows -> BaseRateBackfillResult.from(from, to, rows))
                .map(ApiResponse::success);
    }

    // 최신 미국 정책금리(FEDFUNDS)를 FRED에서 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/base-rate/fred/sync/latest"
    @PostMapping("/fred/sync/latest")
    @Operation(summary = "Sync latest FRED base rate")
    public Mono<ApiResponse<BaseRateSyncResult>> syncLatestFred() {
        return fredBaseRateSyncService.syncLatest()
                .map(BaseRateSyncResult::from)
                .map(ApiResponse::success);
    }

    // 지정 기간의 미국 정책금리(FEDFUNDS)를 FRED에서 과거 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/base-rate/fred/backfill?from=2020-05-11&to=2025-05-11"
    @PostMapping("/fred/backfill")
    @Operation(summary = "Backfill FRED base rates")
    public Mono<ApiResponse<BaseRateBackfillResult>> backfillFred(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return fredBaseRateSyncService.backfill(from, to)
                .map(rows -> BaseRateBackfillResult.from(from, to, rows))
                .map(ApiResponse::success);
    }

    @GetMapping("/fred/latest")
    @Operation(summary = "Get latest FRED base rate")
    public Mono<ApiResponse<BaseRateSyncResult>> latestFred(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "true") boolean sync
    ) {
        Mono<BaseRate> result = sync
                ? fredBaseRateSyncService.ensureSynced(asOfDate)
                : fredBaseRateSyncService.findLatest(asOfDate);
        return result
                .map(BaseRateSyncResult::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.success(null)));
    }

    @GetMapping("/fred/series")
    @Operation(summary = "List FRED base rate series")
    public Mono<ApiResponse<List<BaseRateSyncResult>>> seriesFred(
            @RequestParam(defaultValue = "120") int limit
    ) {
        return fredBaseRateSyncService.findLatestRows(limit)
                .map(rows -> rows.stream().map(BaseRateSyncResult::from).toList())
                .map(ApiResponse::success);
    }

    public record BaseRateSyncResult(
            LocalDate baseDate,
            String rawTime,
            String cycle,
            String statCode,
            String itemCode,
            String unit,
            String source,
            BigDecimal value
    ) {
        static BaseRateSyncResult from(BaseRate baseRate) {
            return new BaseRateSyncResult(
                    baseRate.getBaseDate(),
                    baseRate.getRawTime(),
                    baseRate.getCycle(),
                    baseRate.getStatCode(),
                    baseRate.getItemCode(),
                    baseRate.getUnitName(),
                    baseRate.getSource(),
                    baseRate.getRateValue()
            );
        }
    }

    public record BaseRateBackfillResult(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int savedCount,
            LocalDate firstBaseDate,
            LocalDate lastBaseDate
    ) {
        static BaseRateBackfillResult from(LocalDate requestedFrom, LocalDate requestedTo, List<BaseRate> rows) {
            LocalDate first = rows == null || rows.isEmpty() ? null : rows.get(0).getBaseDate();
            LocalDate last = rows == null || rows.isEmpty() ? null : rows.get(rows.size() - 1).getBaseDate();
            return new BaseRateBackfillResult(
                    requestedFrom,
                    requestedTo,
                    rows == null ? 0 : rows.size(),
                    first,
                    last
            );
        }
    }
}
