package com.qaima.service.bondyield;

import com.qaima.common.Blocking;
import com.qaima.domain.BondYield;
import com.qaima.dto.fred.FredObservationsResponse;
import com.qaima.external.FredBondYieldClient;
import com.qaima.repository.BondYieldRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class FredBondYieldSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int LATEST_LOOKBACK_MONTHS = 6;

    private final BondYieldRepository bondYieldRepository;
    private final FredBondYieldClient fredBondYieldClient;

    public Mono<List<BondYield>> syncLatestAll() {
        return Flux.fromArray(FredBondYieldInstrument.values())
                .concatMap(this::syncLatest)
                .collectList();
    }

    public Mono<BondYield> syncLatest(FredBondYieldInstrument instrument) {
        LocalDate to = LocalDate.now(SEOUL);
        LocalDate from = to.minusMonths(LATEST_LOOKBACK_MONTHS).withDayOfMonth(1);
        return fredBondYieldClient.fetchMonthlyTreasurySeries(instrument.seriesId(), from, to)
                .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows)))
                .flatMap(rows -> rows.stream()
                        .max(Comparator.comparing(BondYield::getYieldDate))
                        .map(Mono::just)
                        .orElseGet(() -> findLatest(instrument, to)))
                .switchIfEmpty(findLatest(instrument, to));
    }

    public Mono<List<BondYield>> backfillMonthly(LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return Flux.fromArray(FredBondYieldInstrument.values())
                .concatMap(instrument -> backfillMonthly(instrument, start, end))
                .flatMapIterable(rows -> rows)
                .sort(Comparator
                        .comparing(BondYield::getInstrumentCode)
                        .thenComparing(BondYield::getYieldDate))
                .collectList();
    }

    public Mono<List<BondYield>> backfillMonthly(FredBondYieldInstrument instrument, LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return fredBondYieldClient.fetchMonthlyTreasurySeries(instrument.seriesId(), start, end)
                .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows)));
    }

    public Mono<BondYield> ensureMonthlySynced(FredBondYieldInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        LocalDate syncDate = LocalDate.now(SEOUL);

        return findLatest(instrument, targetDate)
                .flatMap(existing -> isSyncedOn(existing, syncDate)
                        ? Mono.just(existing)
                        : syncLatest(instrument).then(findLatest(instrument, targetDate)).switchIfEmpty(Mono.just(existing)))
                .switchIfEmpty(syncLatest(instrument).then(findLatest(instrument, targetDate)));
    }

    public Mono<BondYield> findLatest(FredBondYieldInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        return Blocking.call(() -> bondYieldRepository
                        .findTopByInstrumentCodeAndCycleAndYieldDateLessThanEqualOrderByYieldDateDesc(
                                instrument.instrumentCode(),
                                FredBondYieldInstrument.CYCLE_MONTHLY,
                                targetDate
                        )
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    public Mono<List<BondYield>> findLatestRows(FredBondYieldInstrument instrument, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return Blocking.call(() -> bondYieldRepository.findByInstrumentCodeAndCycleOrderByYieldDateDesc(
                instrument.instrumentCode(),
                FredBondYieldInstrument.CYCLE_MONTHLY,
                PageRequest.of(0, safeLimit)
        ));
    }

    private List<BondYield> upsertRows(
            FredBondYieldInstrument instrument,
            List<FredObservationsResponse.Observation> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<BondYield> saved = new ArrayList<>();
        for (FredObservationsResponse.Observation row : rows) {
            if (row == null || row.getDate() == null || row.getValue() == null || ".".equals(row.getValue())) {
                continue;
            }

            LocalDate date = LocalDate.parse(row.getDate());
            String rawTime = date.toString();
            BondYield entity = bondYieldRepository
                    .findBySourceAndInstrumentCodeAndCycleAndRawTime(
                            FredBondYieldInstrument.SOURCE_FRED,
                            instrument.instrumentCode(),
                            FredBondYieldInstrument.CYCLE_MONTHLY,
                            rawTime
                    )
                    .orElseGet(BondYield::new);

            entity.setYieldDate(date);
            entity.setRawTime(rawTime);
            entity.setCycle(FredBondYieldInstrument.CYCLE_MONTHLY);
            entity.setCountryCode(FredBondYieldInstrument.COUNTRY_US);
            entity.setInstrumentCode(instrument.instrumentCode());
            entity.setInstrumentName(instrument.instrumentName());
            entity.setMaturityMonths(instrument.maturityMonths());
            entity.setYieldValue(parseYieldValue(row.getValue()));
            entity.setUnitName("%");
            entity.setStatCode(instrument.seriesId());
            entity.setStatName(FredBondYieldInstrument.STAT_NAME);
            entity.setItemCode(instrument.seriesId());
            entity.setItemName(instrument.instrumentName());
            entity.setSource(FredBondYieldInstrument.SOURCE_FRED);

            saved.add(bondYieldRepository.save(entity));
        }

        log.info("[FredBondYieldSync] upsert complete. instrument={}, rows={}",
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

    private BigDecimal parseYieldValue(String value) {
        String normalized = value == null ? "" : value.trim().replace(",", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("FRED bond yield value is required");
        }
        return new BigDecimal(normalized);
    }
}
