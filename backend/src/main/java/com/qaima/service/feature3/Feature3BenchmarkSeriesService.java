package com.qaima.service.feature3;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.dto.feature3.Feature3BenchmarkSeriesResponseDto;
import com.qaima.external.IndustryIndexFetcher;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import com.qaima.repository.IndustryIndexRepository;
import com.qaima.service.candle.CandleTimePolicy;
import com.qaima.service.tradingcalendar.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class Feature3BenchmarkSeriesService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_BENCHMARK_CODE = "00001";
    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_KIS_BACKFILLED = "KIS_BACKFILLED";
    private static final String SOURCE_DB_INSUFFICIENT = "DB_INSUFFICIENT";
    private static final String SOURCE_DB_STALE = "DB_STALE";
    private static final String SOURCE_UNAVAILABLE = "UNAVAILABLE";
    private static final String KRX_MARKET = "KRX";
    private static final Set<String> KIS_BACKFILL_SUPPORTED_CODES = Set.of("00001", "11001");

    private final IndustryIndexRepository industryIndexRepository;
    private final IndustryIndexOhlcvRepository industryIndexOhlcvRepository;
    private final IndustryIndexFetcher industryIndexFetcher;
    private final TradingCalendarService tradingCalendarService;

    public Mono<Feature3BenchmarkSeriesResponseDto> getBenchmarkSeries(
            String benchmarkCode,
            int lookbackTradingDays,
            int fetchCalendarDays
    ) {
        String code = normalizeBenchmarkCode(benchmarkCode);
        int lookback = Math.max(lookbackTradingDays, 1);
        int fetchDays = Math.max(fetchCalendarDays, lookback);
        BenchmarkWindow window = benchmarkWindow(code, lookback, fetchDays);

        return Mono.fromCallable(() -> industryIndexRepository.findByCode(code))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalIndex -> optionalIndex
                        .map(index -> loadFromDbOrBackfill(index, window))
                        .orElseGet(() -> Mono.just(unavailable(
                                code,
                                null,
                                window.expectedTradingDays(),
                                warning(
                                        "BENCHMARK_INDEX_MISSING",
                                        "Benchmark index master row was not found in industry_index.",
                                        "벤치마크 지수 정보를 찾지 못했습니다.",
                                        "WARN",
                                        code
                                )
                        ))));
    }

    private Mono<Feature3BenchmarkSeriesResponseDto> loadFromDbOrBackfill(
            IndustryIndex index,
            BenchmarkWindow window
    ) {
        int expected = window.expectedTradingDays();
        return readRecentRows(index, expected)
                .flatMap(initialRows -> {
                    boolean sufficient = initialRows.size() >= expected;
                    boolean stale = isStale(initialRows, window);
                    if (sufficient && !stale) {
                        return Mono.just(toResponse(index, SOURCE_DB, true, expected, initialRows, List.of()));
                    }

                    List<Feature3BenchmarkSeriesResponseDto.Warning> warnings = new ArrayList<>();
                    if (!sufficient) {
                        warnings.add(warning(
                                "BENCHMARK_PRICE_HISTORY_INSUFFICIENT",
                                "Benchmark DB rows are fewer than the requested lookback trading days.",
                                "벤치마크 가격 데이터가 분석 기간보다 부족합니다.",
                                "WARN",
                                index.getCode()
                        ));
                    }
                    if (stale) {
                        warnings.add(warning(
                                "BENCHMARK_PRICE_STALE",
                                "Benchmark DB rows do not reach the latest KRX trading day.",
                                "벤치마크 가격 데이터가 최신 거래일까지 갱신되지 않아 외부 데이터를 조회합니다.",
                                "WARN",
                                index.getCode()
                        ));
                    }

                    if (!supportsKisBackfill(index.getCode())) {
                        warnings.add(warning(
                                "BENCHMARK_BACKFILL_UNSUPPORTED",
                                "Benchmark backfill is not supported by the domestic KIS index fetcher.",
                                "해당 벤치마크는 국내 KIS 지수 수집기로 보강할 수 없어 DB 데이터만 사용합니다.",
                                "INFO",
                                index.getCode()
                        ));
                        return Mono.just(insufficientResponse(index, expected, initialRows, warnings));
                    }

                    return backfill(index, window, warnings)
                            .then(readRecentRows(index, expected))
                            .map(rowsAfterBackfill -> {
                                boolean backfilledSufficient = rowsAfterBackfill.size() >= expected;
                                boolean backfilledStale = isStale(rowsAfterBackfill, window);
                                if (backfilledSufficient && !backfilledStale) {
                                    return toResponse(index, SOURCE_KIS_BACKFILLED, true, expected, rowsAfterBackfill, warnings);
                                }
                                return unavailableAfterBackfillResponse(index, expected, rowsAfterBackfill, warnings, backfilledStale);
                            });
                });
    }

    private Mono<List<IndustryIndexOhlcv>> readRecentRows(IndustryIndex index, int expected) {
        return Mono.fromCallable(() ->
                        industryIndexOhlcvRepository.findRecent(
                                index.getIndexId(),
                                Freq.ONE_D,
                                PageRequest.of(0, expected, Sort.by(Sort.Direction.DESC, "id.ts"))
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::sanitizeRows);
    }

    private Mono<Void> backfill(
            IndustryIndex index,
            BenchmarkWindow window,
            List<Feature3BenchmarkSeriesResponseDto.Warning> warnings
    ) {
        int expected = window.expectedTradingDays();
        LocalDate to = window.to();
        LocalDate from = window.from();

        log.info("[Feature3Benchmark] backfill start. benchmarkCode={}, from={}, to={}, expected={}",
                index.getCode(), from, to, expected);

        return industryIndexFetcher.fetch(index.getCode(), Freq.ONE_D, from, to)
                .flatMap(fetched -> {
                    if (fetched == null || fetched.isEmpty()) {
                        warnings.add(warning(
                                "BENCHMARK_BACKFILL_EMPTY",
                                "KIS benchmark backfill returned no rows.",
                                "벤치마크 지수 보강 조회 결과가 비어 있습니다.",
                                "WARN",
                                index.getCode()
                        ));
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> entities = fetched.stream()
                            .map(bar -> {
                                try {
                                    return bar.toEntity(index, Freq.ONE_D);
                                } catch (Exception ex) {
                                    log.warn("[Feature3Benchmark] fetched row dropped. benchmarkCode={}, cause={}",
                                            index.getCode(), ex.getMessage());
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .filter(row -> row.getId() != null && row.getId().getTs() != null)
                            .filter(row -> row.getClose() != null && row.getClose().signum() > 0)
                            .toList();

                    if (entities.isEmpty()) {
                        warnings.add(warning(
                                "BENCHMARK_BACKFILL_EMPTY",
                                "KIS benchmark backfill rows could not be mapped to usable OHLCV rows.",
                                "벤치마크 지수 보강 데이터를 사용할 수 없습니다.",
                                "WARN",
                                index.getCode()
                        ));
                        return Mono.empty();
                    }

                    return Mono.fromCallable(() -> industryIndexOhlcvRepository.saveAll(entities))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnNext(saved -> log.info(
                                    "[Feature3Benchmark] backfill saved. benchmarkCode={}, rows={}",
                                    index.getCode(),
                                    saved == null ? 0 : saved.size()
                            ))
                            .then();
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature3Benchmark] backfill failed. benchmarkCode={}, cause={}",
                            index.getCode(), ex.getMessage(), ex);
                    warnings.add(warning(
                            "BENCHMARK_BACKFILL_FAILED",
                            "KIS benchmark backfill failed.",
                            "벤치마크 지수 보강에 실패했습니다.",
                            "WARN",
                            index.getCode()
                    ));
                    return Mono.empty();
                });
    }

    private List<IndustryIndexOhlcv> sanitizeRows(List<IndustryIndexOhlcv> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<LocalDate, IndustryIndexOhlcv> deduped = new java.util.LinkedHashMap<>();
        rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null && row.getId().getTs() != null)
                .filter(row -> row.getClose() != null && row.getClose().signum() > 0)
                .sorted(Comparator.comparing(row -> row.getId().getTs()))
                .forEach(row -> deduped.put(
                        CandleTimePolicy.tradingDate(row.getId().getTs(), CandleTimePolicy.DEFAULT_TRADING_ZONE),
                        row
                ));

        return deduped.values().stream()
                .sorted(Comparator.comparing(row -> row.getId().getTs()))
                .toList();
    }

    private Feature3BenchmarkSeriesResponseDto insufficientResponse(
            IndustryIndex index,
            int expected,
            List<IndustryIndexOhlcv> rows,
            List<Feature3BenchmarkSeriesResponseDto.Warning> warnings
    ) {
        String source = rows == null || rows.isEmpty() ? SOURCE_UNAVAILABLE : SOURCE_DB_INSUFFICIENT;
        return toResponse(index, source, false, expected, rows == null ? List.of() : rows, warnings);
    }

    private Feature3BenchmarkSeriesResponseDto unavailableAfterBackfillResponse(
            IndustryIndex index,
            int expected,
            List<IndustryIndexOhlcv> rows,
            List<Feature3BenchmarkSeriesResponseDto.Warning> warnings,
            boolean stale
    ) {
        if (rows == null || rows.isEmpty()) {
            return toResponse(index, SOURCE_UNAVAILABLE, false, expected, List.of(), warnings);
        }
        String source = stale ? SOURCE_DB_STALE : SOURCE_DB_INSUFFICIENT;
        return toResponse(index, source, false, expected, rows, warnings);
    }

    private Feature3BenchmarkSeriesResponseDto unavailable(
            String benchmarkCode,
            String benchmarkName,
            int expected,
            Feature3BenchmarkSeriesResponseDto.Warning warning
    ) {
        return new Feature3BenchmarkSeriesResponseDto(
                benchmarkCode,
                benchmarkName,
                SOURCE_UNAVAILABLE,
                false,
                expected,
                0,
                1.0,
                List.of(),
                List.of(warning)
        );
    }

    private Feature3BenchmarkSeriesResponseDto toResponse(
            IndustryIndex index,
            String source,
            boolean available,
            int expected,
            List<IndustryIndexOhlcv> rows,
            List<Feature3BenchmarkSeriesResponseDto.Warning> warnings
    ) {
        List<IndustryIndexOhlcv> sanitized = sanitizeRows(rows);
        int availableCount = sanitized.size();
        double missingRate = round(Math.max(0.0, 1.0 - ((double) availableCount / Math.max(expected, 1))));
        List<Feature3BenchmarkSeriesResponseDto.PricePoint> points = sanitized.stream()
                .map(row -> new Feature3BenchmarkSeriesResponseDto.PricePoint(
                        CandleTimePolicy.canonicalTs(
                                row.getId().getTs(),
                                Freq.ONE_D,
                                CandleTimePolicy.DEFAULT_TRADING_ZONE
                        ).toString(),
                        row.getClose().doubleValue()
                ))
                .toList();

        return new Feature3BenchmarkSeriesResponseDto(
                index.getCode(),
                index.getName(),
                source,
                available,
                expected,
                availableCount,
                missingRate,
                points,
                warnings == null ? List.of() : warnings
        );
    }

    private boolean supportsKisBackfill(String benchmarkCode) {
        return KIS_BACKFILL_SUPPORTED_CODES.contains(normalizeBenchmarkCode(benchmarkCode));
    }

    private boolean isStale(List<IndustryIndexOhlcv> rows, BenchmarkWindow window) {
        if (!window.freshnessRequired() || rows == null || rows.isEmpty()) {
            return false;
        }
        LocalDate latestDbDate = rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null && row.getId().getTs() != null)
                .map(row -> CandleTimePolicy.tradingDate(row.getId().getTs(), CandleTimePolicy.DEFAULT_TRADING_ZONE))
                .max(Comparator.naturalOrder())
                .orElse(null);
        return latestDbDate == null || latestDbDate.isBefore(window.to());
    }

    private BenchmarkWindow benchmarkWindow(String benchmarkCode, int lookback, int fetchDays) {
        String normalized = normalizeBenchmarkCode(benchmarkCode);
        if (!supportsKisBackfill(normalized)) {
            LocalDate to = LocalDate.now(KST);
            LocalDate from = to.minusDays(fetchDays);
            return new BenchmarkWindow(from, to, lookback, false);
        }

        LocalDate latestTradingDay = tradingCalendarService.latestTradingDay(ZonedDateTime.now(KST), KRX_MARKET);
        LocalDate from = latestTradingDay.minusDays(fetchDays);
        int expected = Math.max(1, Math.min(lookback, countTradingDays(from, latestTradingDay)));
        return new BenchmarkWindow(from, latestTradingDay, expected, true);
    }

    private int countTradingDays(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)) {
            return 0;
        }
        int count = 0;
        LocalDate cursor = fromInclusive;
        while (!cursor.isAfter(toInclusive)) {
            if (tradingCalendarService.isTradingDay(cursor, KRX_MARKET)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private String normalizeBenchmarkCode(String benchmarkCode) {
        if (benchmarkCode == null || benchmarkCode.isBlank()) {
            return DEFAULT_BENCHMARK_CODE;
        }
        return benchmarkCode.trim().toUpperCase(Locale.ROOT);
    }

    private Feature3BenchmarkSeriesResponseDto.Warning warning(
            String code,
            String message,
            String userMessage,
            String severity,
            String target
    ) {
        return new Feature3BenchmarkSeriesResponseDto.Warning(code, message, userMessage, severity, target);
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private record BenchmarkWindow(
            LocalDate from,
            LocalDate to,
            int expectedTradingDays,
            boolean freshnessRequired
    ) {
    }
}
