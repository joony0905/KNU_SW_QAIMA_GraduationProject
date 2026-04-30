package com.qaima.api.BondYieldAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.BondYield;
import com.qaima.service.bondyield.BondYieldInstrument;
import com.qaima.service.bondyield.BondYieldSyncService;
import com.qaima.service.bondyield.FredBondYieldInstrument;
import com.qaima.service.bondyield.FredBondYieldSyncService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@RequestMapping("/api/v1/admin/bond-yield")
public class BondYieldAdminController {

    private final BondYieldSyncService bondYieldSyncService;
    private final FredBondYieldSyncService fredBondYieldSyncService;

    // 최신 국고채 수익률을 적재한다. instrumentCode 생략 시 KR3Y/KR10Y 모두 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/sync/latest"
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/sync/latest?instrumentCode=KR10Y"
    @PostMapping("/sync/latest")
    public Mono<ApiResponse<List<BondYieldRow>>> syncLatest(
            @RequestParam(required = false) String instrumentCode
    ) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return bondYieldSyncService.syncLatestAll()
                    .map(rows -> rows.stream().map(BondYieldRow::from).toList())
                    .map(ApiResponse::success);
        }

        BondYieldInstrument instrument = BondYieldInstrument.fromCode(instrumentCode);
        return bondYieldSyncService.syncLatest(instrument)
                .map(row -> List.of(BondYieldRow.from(row)))
                .map(ApiResponse::success);
    }

    // 지정 기간의 국고채 수익률을 과거 적재한다. instrumentCode 생략 시 KR3Y/KR10Y 모두 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/backfill?from=2006-01-01&to=2026-04-30"
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/backfill?instrumentCode=KR3Y&from=2006-01-01&to=2026-04-30"
    @PostMapping("/backfill")
    public Mono<ApiResponse<BondYieldBackfillResult>> backfill(
            @RequestParam(required = false) String instrumentCode,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return bondYieldSyncService.backfillDaily(from, to)
                    .map(rows -> BondYieldBackfillResult.from(from, to, rows))
                    .map(ApiResponse::success);
        }

        BondYieldInstrument instrument = BondYieldInstrument.fromCode(instrumentCode);
        return bondYieldSyncService.backfillDaily(instrument, from, to)
                .map(rows -> BondYieldBackfillResult.from(from, to, rows))
                .map(ApiResponse::success);
    }

    // 최신 미국채 수익률을 FRED에서 적재한다. instrumentCode 생략 시 US2Y/US5Y/US10Y 모두 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/fred/sync/latest"
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/fred/sync/latest?instrumentCode=US10Y"
    @PostMapping("/fred/sync/latest")
    public Mono<ApiResponse<List<BondYieldRow>>> syncLatestFred(
            @RequestParam(required = false) String instrumentCode
    ) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return fredBondYieldSyncService.syncLatestAll()
                    .map(rows -> rows.stream().map(BondYieldRow::from).toList())
                    .map(ApiResponse::success);
        }

        FredBondYieldInstrument instrument = FredBondYieldInstrument.fromCode(instrumentCode);
        return fredBondYieldSyncService.syncLatest(instrument)
                .map(row -> List.of(BondYieldRow.from(row)))
                .map(ApiResponse::success);
    }

    // 지정 기간의 미국채 월간 수익률을 FRED에서 과거 적재한다. instrumentCode 생략 시 US2Y/US5Y/US10Y 모두 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/fred/backfill?from=2020-05-11&to=2025-05-11"
    // curl -X POST "http://localhost:8080/api/v1/admin/bond-yield/fred/backfill?instrumentCode=US2Y&from=2020-05-11&to=2025-05-11"
    @PostMapping("/fred/backfill")
    public Mono<ApiResponse<BondYieldBackfillResult>> backfillFred(
            @RequestParam(required = false) String instrumentCode,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return fredBondYieldSyncService.backfillMonthly(from, to)
                    .map(rows -> BondYieldBackfillResult.from(from, to, rows))
                    .map(ApiResponse::success);
        }

        FredBondYieldInstrument instrument = FredBondYieldInstrument.fromCode(instrumentCode);
        return fredBondYieldSyncService.backfillMonthly(instrument, from, to)
                .map(rows -> BondYieldBackfillResult.from(from, to, rows))
                .map(ApiResponse::success);
    }

    @GetMapping("/fred/latest")
    public Mono<ApiResponse<BondYieldRow>> latestFred(
            @RequestParam(defaultValue = "US10Y") String instrumentCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "true") boolean sync
    ) {
        FredBondYieldInstrument instrument = FredBondYieldInstrument.fromCode(instrumentCode);
        Mono<BondYield> result = sync
                ? fredBondYieldSyncService.ensureMonthlySynced(instrument, asOfDate)
                : fredBondYieldSyncService.findLatest(instrument, asOfDate);
        return result
                .map(BondYieldRow::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.success(null)));
    }

    @GetMapping("/fred/series")
    public Mono<ApiResponse<List<BondYieldRow>>> seriesFred(
            @RequestParam(defaultValue = "US10Y") String instrumentCode,
            @RequestParam(defaultValue = "120") int limit
    ) {
        FredBondYieldInstrument instrument = FredBondYieldInstrument.fromCode(instrumentCode);
        return fredBondYieldSyncService.findLatestRows(instrument, limit)
                .map(rows -> rows.stream().map(BondYieldRow::from).toList())
                .map(ApiResponse::success);
    }

    @GetMapping("/latest")
    public Mono<ApiResponse<BondYieldRow>> latest(
            @RequestParam(defaultValue = "KR10Y") String instrumentCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "true") boolean sync
    ) {
        BondYieldInstrument instrument = BondYieldInstrument.fromCode(instrumentCode);
        Mono<BondYield> result = sync
                ? bondYieldSyncService.ensureDailySynced(instrument, asOfDate)
                : bondYieldSyncService.findLatest(instrument, asOfDate);
        return result
                .map(BondYieldRow::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.success(null)));
    }

    @GetMapping("/series")
    public Mono<ApiResponse<List<BondYieldRow>>> series(
            @RequestParam(defaultValue = "KR10Y") String instrumentCode,
            @RequestParam(defaultValue = "365") int limit
    ) {
        BondYieldInstrument instrument = BondYieldInstrument.fromCode(instrumentCode);
        return bondYieldSyncService.findLatestRows(instrument, limit)
                .map(rows -> rows.stream().map(BondYieldRow::from).toList())
                .map(ApiResponse::success);
    }

    public record BondYieldRow(
            LocalDate yieldDate,
            String rawTime,
            String cycle,
            String countryCode,
            String instrumentCode,
            String instrumentName,
            Integer maturityMonths,
            String statCode,
            String itemCode,
            String itemName,
            String unit,
            String source,
            BigDecimal value
    ) {
        static BondYieldRow from(BondYield row) {
            if (row == null) {
                return null;
            }
            return new BondYieldRow(
                    row.getYieldDate(),
                    row.getRawTime(),
                    row.getCycle(),
                    row.getCountryCode(),
                    row.getInstrumentCode(),
                    row.getInstrumentName(),
                    row.getMaturityMonths(),
                    row.getStatCode(),
                    row.getItemCode(),
                    row.getItemName(),
                    row.getUnitName(),
                    row.getSource(),
                    row.getYieldValue()
            );
        }
    }

    public record BondYieldBackfillResult(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int savedCount,
            LocalDate firstYieldDate,
            LocalDate lastYieldDate,
            Map<String, Integer> savedCountByInstrument
    ) {
        static BondYieldBackfillResult from(LocalDate requestedFrom, LocalDate requestedTo, List<BondYield> rows) {
            List<BondYield> safeRows = rows == null ? List.of() : rows;
            LocalDate first = safeRows.stream()
                    .map(BondYield::getYieldDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate last = safeRows.stream()
                    .map(BondYield::getYieldDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            Map<String, Integer> byInstrument = safeRows.stream()
                    .collect(Collectors.groupingBy(
                            BondYield::getInstrumentCode,
                            Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                    ));
            return new BondYieldBackfillResult(
                    requestedFrom,
                    requestedTo,
                    safeRows.size(),
                    first,
                    last,
                    byInstrument
            );
        }
    }
}
