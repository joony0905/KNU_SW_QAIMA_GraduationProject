package com.qaima.service.bondyield;

import com.qaima.common.Blocking;
import com.qaima.domain.BondYield;
import com.qaima.dto.bok.BokStatisticSearchResponse;
import com.qaima.external.BokBondYieldClient;
import com.qaima.repository.BondYieldRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BondYieldSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int LATEST_LOOKBACK_DAYS = 21;

    private final BondYieldRepository bondYieldRepository;
    private final BokBondYieldClient bokBondYieldClient;

    public Mono<List<BondYield>> syncLatestAll() {
        return Flux.fromArray(BondYieldInstrument.values())
                .concatMap(this::syncLatest)
                .collectList();
    }

    public Mono<BondYield> syncLatest(BondYieldInstrument instrument) {
        LocalDate to = LocalDate.now(SEOUL);
        LocalDate from = to.minusDays(LATEST_LOOKBACK_DAYS);
        return bokBondYieldClient.fetchDailyBondYieldSeries(
                        instrument.statCode(),
                        instrument.itemCode(),
                        from,
                        to
                )
                .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows)))
                .flatMap(rows -> rows.stream()
                        .max(Comparator.comparing(BondYield::getYieldDate))
                        .map(Mono::just)
                        .orElseGet(() -> findLatest(instrument, to)))
                .switchIfEmpty(findLatest(instrument, to));
    }

    public Mono<List<BondYield>> backfillDaily(LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return Flux.fromArray(BondYieldInstrument.values())
                .concatMap(instrument -> backfillDaily(instrument, start, end))
                .flatMapIterable(rows -> rows)
                .sort(Comparator
                        .comparing(BondYield::getInstrumentCode)
                        .thenComparing(BondYield::getYieldDate))
                .collectList();
    }

    public Mono<List<BondYield>> backfillDaily(BondYieldInstrument instrument, LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return Flux.fromIterable(splitByYear(start, end))
                .concatMap(range -> bokBondYieldClient.fetchDailyBondYieldSeries(
                                instrument.statCode(),
                                instrument.itemCode(),
                                range.from(),
                                range.to()
                        )
                        .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows))))
                .flatMapIterable(rows -> rows)
                .collectList();
    }

    public Mono<BondYield> ensureDailySynced(BondYieldInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        LocalDate syncDate = LocalDate.now(SEOUL);

        return findLatest(instrument, targetDate)
                .flatMap(existing -> isSyncedOn(existing, syncDate)
                        ? Mono.just(existing)
                        : syncLatest(instrument).then(findLatest(instrument, targetDate)).switchIfEmpty(Mono.just(existing)))
                .switchIfEmpty(syncLatest(instrument).then(findLatest(instrument, targetDate)));
    }

    public Mono<BondYield> findLatest(BondYieldInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        return Blocking.call(() -> bondYieldRepository
                        .findTopByInstrumentCodeAndCycleAndYieldDateLessThanEqualOrderByYieldDateDesc(
                                instrument.instrumentCode(),
                                BondYieldInstrument.CYCLE_DAILY,
                                targetDate
                        )
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    public Mono<List<BondYield>> findLatestRows(BondYieldInstrument instrument, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return Blocking.call(() -> bondYieldRepository.findByInstrumentCodeAndCycleOrderByYieldDateDesc(
                instrument.instrumentCode(),
                BondYieldInstrument.CYCLE_DAILY,
                PageRequest.of(0, safeLimit)
        ));
    }

    private List<BondYield> upsertRows(
            BondYieldInstrument instrument,
            List<BokStatisticSearchResponse.Row> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<BondYield> saved = new ArrayList<>();
        for (BokStatisticSearchResponse.Row row : rows) {
            if (row == null || row.getTime() == null || row.getDataValue() == null) {
                continue;
            }

            String rawTime = requireText(row.getTime(), "time");
            BondYield entity = bondYieldRepository
                    .findBySourceAndInstrumentCodeAndCycleAndRawTime(
                            BondYieldInstrument.SOURCE_BOK_ECOS,
                            instrument.instrumentCode(),
                            BondYieldInstrument.CYCLE_DAILY,
                            rawTime
                    )
                    .orElseGet(BondYield::new);

            entity.setYieldDate(parseBaseDate(rawTime, BondYieldInstrument.CYCLE_DAILY));
            entity.setRawTime(rawTime);
            entity.setCycle(BondYieldInstrument.CYCLE_DAILY);
            entity.setCountryCode(BondYieldInstrument.COUNTRY_KR);
            entity.setInstrumentCode(instrument.instrumentCode());
            entity.setInstrumentName(instrument.instrumentName());
            entity.setMaturityMonths(instrument.maturityMonths());
            entity.setYieldValue(parseYieldValue(row.getDataValue()));
            entity.setUnitName(defaultText(row.getUnitName(), "%"));
            entity.setStatCode(defaultText(row.getStatCode(), instrument.statCode()));
            entity.setStatName(defaultText(row.getStatName(), "시장금리"));
            entity.setItemCode(defaultText(row.getItemCode1(), instrument.itemCode()));
            entity.setItemName(defaultText(row.getItemName1(), instrument.instrumentName()));
            entity.setSource(BondYieldInstrument.SOURCE_BOK_ECOS);

            saved.add(bondYieldRepository.save(entity));
        }

        log.info("[BondYieldSync] upsert complete. instrument={}, rows={}",
                instrument.instrumentCode(), saved.size());
        return saved;
    }

    private boolean isSyncedOn(BondYield bondYield, LocalDate syncDate) {
        if (bondYield == null || bondYield.getUpdatedAt() == null || syncDate == null) {
            return false;
        }
        return bondYield.getUpdatedAt().atZone(SEOUL).toLocalDate().equals(syncDate);
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        return asOfDate == null ? LocalDate.now(SEOUL) : asOfDate;
    }

    private LocalDate parseBaseDate(String rawTime, String cycle) {
        return switch (cycle) {
            case "D" -> LocalDate.of(
                    Integer.parseInt(rawTime.substring(0, 4)),
                    Integer.parseInt(rawTime.substring(4, 6)),
                    Integer.parseInt(rawTime.substring(6, 8))
            );
            case "M" -> YearMonth.of(
                    Integer.parseInt(rawTime.substring(0, 4)),
                    Integer.parseInt(rawTime.substring(4, 6))
            ).atDay(1);
            case "A" -> LocalDate.of(Integer.parseInt(rawTime), 1, 1);
            default -> throw new IllegalArgumentException("Unsupported bond yield cycle: " + cycle);
        };
    }

    private BigDecimal parseYieldValue(String value) {
        String normalized = requireText(value, "dataValue").replace(",", "");
        return new BigDecimal(normalized);
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("bond yield " + fieldName + " is required");
        }
        return value.trim();
    }

    private List<DateRange> splitByYear(LocalDate from, LocalDate to) {
        List<DateRange> ranges = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate endOfYear = LocalDate.of(cursor.getYear(), 12, 31);
            LocalDate chunkEnd = endOfYear.isBefore(to) ? endOfYear : to;
            ranges.add(new DateRange(cursor, chunkEnd));
            cursor = chunkEnd.plusDays(1);
        }
        return ranges;
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
