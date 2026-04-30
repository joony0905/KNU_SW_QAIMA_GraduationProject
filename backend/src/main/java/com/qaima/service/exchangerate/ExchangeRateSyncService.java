package com.qaima.service.exchangerate;

import com.qaima.common.Blocking;
import com.qaima.domain.ExchangeRate;
import com.qaima.dto.bok.BokStatisticSearchResponse;
import com.qaima.external.BokExchangeRateClient;
import com.qaima.repository.ExchangeRateRepository;
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
public class ExchangeRateSyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int LATEST_LOOKBACK_DAYS = 10;

    private final ExchangeRateRepository exchangeRateRepository;
    private final BokExchangeRateClient bokExchangeRateClient;

    public Mono<ExchangeRate> syncLatest(ExchangeRateInstrument instrument) {
        LocalDate to = LocalDate.now(SEOUL);
        LocalDate from = to.minusDays(LATEST_LOOKBACK_DAYS);
        return bokExchangeRateClient.fetchDailyExchangeRateSeries(
                        instrument.statCode(),
                        instrument.itemCode(),
                        from,
                        to
                )
                .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows)))
                .flatMap(rows -> rows.stream()
                        .max(Comparator.comparing(ExchangeRate::getRateDate))
                        .map(Mono::just)
                        .orElseGet(() -> findLatest(instrument, to)))
                .switchIfEmpty(findLatest(instrument, to));
    }

    public Mono<List<ExchangeRate>> backfillDaily(ExchangeRateInstrument instrument, LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return Flux.fromIterable(splitByYear(start, end))
                .concatMap(range -> bokExchangeRateClient.fetchDailyExchangeRateSeries(
                                instrument.statCode(),
                                instrument.itemCode(),
                                range.from(),
                                range.to()
                        )
                        .flatMap(rows -> Blocking.call(() -> upsertRows(instrument, rows))))
                .flatMapIterable(rows -> rows)
                .sort(Comparator.comparing(ExchangeRate::getRateDate))
                .collectList();
    }

    public Mono<ExchangeRate> ensureDailySynced(ExchangeRateInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        LocalDate syncDate = LocalDate.now(SEOUL);

        return findLatest(instrument, targetDate)
                .flatMap(existing -> isSyncedOn(existing, syncDate)
                        ? Mono.just(existing)
                        : syncLatest(instrument).then(findLatest(instrument, targetDate)).switchIfEmpty(Mono.just(existing)))
                .switchIfEmpty(syncLatest(instrument).then(findLatest(instrument, targetDate)));
    }

    public Mono<ExchangeRate> findLatest(ExchangeRateInstrument instrument, LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        return Blocking.call(() -> exchangeRateRepository
                        .findTopByPairCodeAndCycleAndRateDateLessThanEqualOrderByRateDateDesc(
                                instrument.pairCode(),
                                ExchangeRateInstrument.CYCLE_DAILY,
                                targetDate
                        )
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    public Mono<List<ExchangeRate>> findLatestRows(ExchangeRateInstrument instrument, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return Blocking.call(() -> exchangeRateRepository.findByPairCodeAndCycleOrderByRateDateDesc(
                instrument.pairCode(),
                ExchangeRateInstrument.CYCLE_DAILY,
                PageRequest.of(0, safeLimit)
        ));
    }

    private List<ExchangeRate> upsertRows(
            ExchangeRateInstrument instrument,
            List<BokStatisticSearchResponse.Row> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<ExchangeRate> saved = new ArrayList<>();
        for (BokStatisticSearchResponse.Row row : rows) {
            if (row == null || row.getTime() == null || row.getDataValue() == null) {
                continue;
            }

            String rawTime = requireText(row.getTime(), "time");
            ExchangeRate entity = exchangeRateRepository
                    .findByPairCodeAndCycleAndRawTimeAndSource(
                            instrument.pairCode(),
                            ExchangeRateInstrument.CYCLE_DAILY,
                            rawTime,
                            ExchangeRateInstrument.SOURCE_BOK_ECOS
                    )
                    .orElseGet(ExchangeRate::new);

            entity.setRateDate(parseBaseDate(rawTime, ExchangeRateInstrument.CYCLE_DAILY));
            entity.setRawTime(rawTime);
            entity.setCycle(ExchangeRateInstrument.CYCLE_DAILY);
            entity.setPairCode(instrument.pairCode());
            entity.setBaseCurrency(instrument.baseCurrency());
            entity.setQuoteCurrency(instrument.quoteCurrency());
            entity.setRateValue(parseRateValue(row.getDataValue()));
            entity.setUnitName(defaultText(row.getUnitName(), "원"));
            entity.setStatCode(defaultText(row.getStatCode(), instrument.statCode()));
            entity.setStatName(defaultText(row.getStatName(), "환율"));
            entity.setItemCode(defaultText(row.getItemCode1(), instrument.itemCode()));
            entity.setItemName(defaultText(row.getItemName1(), instrument.pairCode()));
            entity.setSource(ExchangeRateInstrument.SOURCE_BOK_ECOS);

            saved.add(exchangeRateRepository.save(entity));
        }

        log.info("[ExchangeRateSync] upsert complete. pairCode={}, rows={}",
                instrument.pairCode(), saved.size());
        return saved;
    }

    private boolean isSyncedOn(ExchangeRate exchangeRate, LocalDate syncDate) {
        if (exchangeRate == null || exchangeRate.getUpdatedAt() == null || syncDate == null) {
            return false;
        }
        return exchangeRate.getUpdatedAt().atZone(SEOUL).toLocalDate().equals(syncDate);
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
            default -> throw new IllegalArgumentException("Unsupported exchange rate cycle: " + cycle);
        };
    }

    private BigDecimal parseRateValue(String value) {
        return new BigDecimal(requireText(value, "dataValue").replace(",", ""));
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("exchange rate " + fieldName + " is required");
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
