package com.qaima.api.BaseRateAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.BaseRate;
import com.qaima.service.baserate.BaseRateSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/base-rate")
public class BaseRateAdminController {

    private final BaseRateSyncService baseRateSyncService;

    @PostMapping("/sync/latest")
    public Mono<ApiResponse<BaseRateSyncResult>> syncLatest() {
        return baseRateSyncService.syncLatest()
                .map(BaseRateSyncResult::from)
                .map(ApiResponse::success);
    }

    @PostMapping("/backfill")
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

    public record BaseRateSyncResult(
            LocalDate baseDate,
            String rawTime,
            String cycle,
            String statCode,
            String itemCode,
            String unit,
            String source,
            java.math.BigDecimal value
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
