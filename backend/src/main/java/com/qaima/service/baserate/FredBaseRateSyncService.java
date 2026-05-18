package com.qaima.service.baserate;

import com.qaima.common.Blocking;
import com.qaima.domain.BaseRate;
import com.qaima.dto.fred.FredObservationsResponse;
import com.qaima.external.FredBaseRateClient;
import com.qaima.repository.BaseRateRepository;
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
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class FredBaseRateSyncService {

    public static final String FED_FUNDS_STAT_CODE = "DFF";
    public static final String FED_FUNDS_STAT_NAME = "Effective Federal Funds Rate";
    public static final String FED_FUNDS_ITEM_CODE = "DFF";
    public static final String FED_FUNDS_ITEM_NAME = "미국 정책금리";
    public static final String DEFAULT_CYCLE = "D";
    public static final String DEFAULT_SOURCE = "FRED";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int LATEST_LOOKBACK_DAYS = 120;

    private final BaseRateRepository baseRateRepository;
    private final FredBaseRateClient fredBaseRateClient;

    public Mono<BaseRate> syncLatest() {
        LocalDate to = LocalDate.now(SEOUL);
        LocalDate from = to.minusDays(LATEST_LOOKBACK_DAYS);
        return fredBaseRateClient.fetchSeriesObservations(FED_FUNDS_STAT_CODE, from, to)
                .flatMap(rows -> Blocking.call(() -> upsertRows(rows)))
                .flatMap(rows -> rows.stream()
                        .max(Comparator.comparing(BaseRate::getBaseDate))
                        .map(Mono::just)
                        .orElseGet(() -> findLatest(to)))
                .switchIfEmpty(findLatest(to));
    }

    public Mono<List<BaseRate>> backfill(LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return fredBaseRateClient.fetchSeriesObservations(FED_FUNDS_STAT_CODE, start, end)
                .flatMap(rows -> Blocking.call(() -> upsertRows(rows)));
    }

    public Mono<BaseRate> ensureSynced(LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        LocalDate syncDate = LocalDate.now(SEOUL);

        return findLatest(targetDate)
                .flatMap(existing -> isSyncedOn(existing, syncDate)
                        ? Mono.just(existing)
                        : syncLatest().then(findLatest(targetDate)).switchIfEmpty(Mono.just(existing)))
                .switchIfEmpty(syncLatest().then(findLatest(targetDate)));
    }

    public Mono<BaseRate> findLatest(LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        return Blocking.call(() -> baseRateRepository
                        .findTopByStatCodeAndItemCodeAndCycleAndBaseDateLessThanEqualOrderByBaseDateDesc(
                                FED_FUNDS_STAT_CODE,
                                FED_FUNDS_ITEM_CODE,
                                DEFAULT_CYCLE,
                                targetDate
                        )
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    public Mono<List<BaseRate>> findLatestRows(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return Blocking.call(() -> baseRateRepository.findByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
                FED_FUNDS_STAT_CODE,
                FED_FUNDS_ITEM_CODE,
                DEFAULT_CYCLE,
                PageRequest.of(0, safeLimit)
        ));
    }

    private List<BaseRate> upsertRows(List<FredObservationsResponse.Observation> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<BaseRate> saved = new ArrayList<>();
        for (FredObservationsResponse.Observation row : rows) {
            if (row == null || row.getDate() == null || row.getValue() == null || ".".equals(row.getValue())) {
                continue;
            }

            LocalDate date = LocalDate.parse(row.getDate());
            String rawTime = date.toString();
            BaseRate entity = baseRateRepository
                    .findByStatCodeAndItemCodeAndCycleAndRawTime(
                            FED_FUNDS_STAT_CODE,
                            FED_FUNDS_ITEM_CODE,
                            DEFAULT_CYCLE,
                            rawTime
                    )
                    .orElseGet(BaseRate::new);

            entity.setBaseDate(date);
            entity.setRawTime(rawTime);
            entity.setCycle(DEFAULT_CYCLE);
            entity.setRateValue(parseRateValue(row.getValue()));
            entity.setUnitName("%");
            entity.setStatCode(FED_FUNDS_STAT_CODE);
            entity.setStatName(FED_FUNDS_STAT_NAME);
            entity.setItemCode(FED_FUNDS_ITEM_CODE);
            entity.setItemName(FED_FUNDS_ITEM_NAME);
            entity.setSource(DEFAULT_SOURCE);

            saved.add(baseRateRepository.save(entity));
        }

        log.info("[FredBaseRateSync] upsert complete. rows={}", saved.size());
        return saved;
    }

    private boolean isSyncedOn(BaseRate rate, LocalDate syncDate) {
        if (rate == null || rate.getUpdatedAt() == null || syncDate == null) {
            return false;
        }
        return rate.getUpdatedAt().atZone(SEOUL).toLocalDate().equals(syncDate);
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        return asOfDate == null ? LocalDate.now(SEOUL) : asOfDate;
    }

    private BigDecimal parseRateValue(String value) {
        String normalized = value == null ? "" : value.trim().replace(",", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("FRED base rate value is required");
        }
        return new BigDecimal(normalized);
    }

    @SuppressWarnings("unused")
    private LocalDate parseBaseDate(String rawTime, String cycle) {
        return switch (cycle) {
            case "D" -> LocalDate.parse(rawTime);
            case "M" -> YearMonth.parse(rawTime.substring(0, 7)).atDay(1);
            default -> throw new IllegalArgumentException("Unsupported FRED base rate cycle: " + cycle);
        };
    }
}
