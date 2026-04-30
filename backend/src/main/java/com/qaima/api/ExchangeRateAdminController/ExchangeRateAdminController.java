package com.qaima.api.ExchangeRateAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.ExchangeRate;
import com.qaima.service.exchangerate.ExchangeRateInstrument;
import com.qaima.service.exchangerate.ExchangeRateSyncService;
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
@RequestMapping("/api/v1/admin/exchange-rate")
public class ExchangeRateAdminController {

    private final ExchangeRateSyncService exchangeRateSyncService;

    // 최신 USD/KRW 환율을 적재한다. 현재 지원 pairCode는 USD_KRW다.
    // curl -X POST "http://localhost:8080/api/v1/admin/exchange-rate/sync/latest?pairCode=USD_KRW"
    @PostMapping("/sync/latest")
    public Mono<ApiResponse<ExchangeRateRow>> syncLatest(
            @RequestParam(defaultValue = "USD_KRW") String pairCode
    ) {
        ExchangeRateInstrument instrument = ExchangeRateInstrument.fromPairCode(pairCode);
        return exchangeRateSyncService.syncLatest(instrument)
                .map(ExchangeRateRow::from)
                .map(ApiResponse::success);
    }

    // 지정 기간의 USD/KRW 환율을 과거 적재한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/exchange-rate/backfill?pairCode=USD_KRW&from=2010-01-01&to=2026-04-30"
    @PostMapping("/backfill")
    public Mono<ApiResponse<ExchangeRateBackfillResult>> backfill(
            @RequestParam(defaultValue = "USD_KRW") String pairCode,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        ExchangeRateInstrument instrument = ExchangeRateInstrument.fromPairCode(pairCode);
        return exchangeRateSyncService.backfillDaily(instrument, from, to)
                .map(rows -> ExchangeRateBackfillResult.from(from, to, rows))
                .map(ApiResponse::success);
    }

    @GetMapping("/latest")
    public Mono<ApiResponse<ExchangeRateRow>> latest(
            @RequestParam(defaultValue = "USD_KRW") String pairCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "true") boolean sync
    ) {
        ExchangeRateInstrument instrument = ExchangeRateInstrument.fromPairCode(pairCode);
        Mono<ExchangeRate> result = sync
                ? exchangeRateSyncService.ensureDailySynced(instrument, asOfDate)
                : exchangeRateSyncService.findLatest(instrument, asOfDate);
        return result
                .map(ExchangeRateRow::from)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(ApiResponse.success(null)));
    }

    @GetMapping("/series")
    public Mono<ApiResponse<List<ExchangeRateRow>>> series(
            @RequestParam(defaultValue = "USD_KRW") String pairCode,
            @RequestParam(defaultValue = "365") int limit
    ) {
        ExchangeRateInstrument instrument = ExchangeRateInstrument.fromPairCode(pairCode);
        return exchangeRateSyncService.findLatestRows(instrument, limit)
                .map(rows -> rows.stream().map(ExchangeRateRow::from).toList())
                .map(ApiResponse::success);
    }

    public record ExchangeRateRow(
            LocalDate rateDate,
            String rawTime,
            String cycle,
            String pairCode,
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rateValue,
            String unit,
            String statCode,
            String itemCode,
            String itemName,
            String source
    ) {
        static ExchangeRateRow from(ExchangeRate row) {
            if (row == null) {
                return null;
            }
            return new ExchangeRateRow(
                    row.getRateDate(),
                    row.getRawTime(),
                    row.getCycle(),
                    row.getPairCode(),
                    row.getBaseCurrency(),
                    row.getQuoteCurrency(),
                    row.getRateValue(),
                    row.getUnitName(),
                    row.getStatCode(),
                    row.getItemCode(),
                    row.getItemName(),
                    row.getSource()
            );
        }
    }

    public record ExchangeRateBackfillResult(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int savedCount,
            LocalDate firstRateDate,
            LocalDate lastRateDate
    ) {
        static ExchangeRateBackfillResult from(LocalDate requestedFrom, LocalDate requestedTo, List<ExchangeRate> rows) {
            List<ExchangeRate> safeRows = rows == null ? List.of() : rows;
            LocalDate first = safeRows.stream()
                    .map(ExchangeRate::getRateDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate last = safeRows.stream()
                    .map(ExchangeRate::getRateDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            return new ExchangeRateBackfillResult(
                    requestedFrom,
                    requestedTo,
                    safeRows.size(),
                    first,
                    last
            );
        }
    }
}
