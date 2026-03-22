package com.qaima.service.baserate;

import com.qaima.common.Blocking;
import com.qaima.domain.BaseRate;
import com.qaima.dto.bok.BokKeyStatisticListResponse;
import com.qaima.dto.bok.BokStatisticSearchResponse;
import com.qaima.external.BokBaseRateClient;
import com.qaima.repository.BaseRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaseRateSyncService {

    public static final String DEFAULT_STAT_CODE = "722Y001";
    public static final String DEFAULT_STAT_NAME =
            "1.3.1. \uD55C\uAD6D\uC740\uD589 \uAE30\uC900\uAE08\uB9AC \uBC0F \uC5EC\uC218\uC2E0\uAE08\uB9AC";
    public static final String DEFAULT_ITEM_CODE = "0101000";
    public static final String DEFAULT_ITEM_NAME =
            "\uD55C\uAD6D\uC740\uD589 \uAE30\uC900\uAE08\uB9AC";
    public static final String DEFAULT_CYCLE = "D";
    public static final String DEFAULT_SOURCE = "BOK_ECOS";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BaseRateRepository baseRateRepository;
    private final BokBaseRateClient bokBaseRateClient;

    public Mono<BaseRate> ensureDailySynced(LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        LocalDate syncDate = LocalDate.now(SEOUL);

        return findLatest(targetDate)
                .flatMap(existing -> refreshIfNeeded(existing, targetDate, syncDate))
                .switchIfEmpty(refreshIfNeeded(null, targetDate, syncDate));
    }

    public Mono<BaseRate> syncLatest() {
        return bokBaseRateClient.fetchLatestBaseRate()
                .flatMap(row -> Blocking.call(() -> upsertLatestRow(row)));
    }

    public Mono<List<BaseRate>> backfillDaily(LocalDate from, LocalDate to) {
        LocalDate start = resolveAsOfDate(from);
        LocalDate end = resolveAsOfDate(to);

        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return bokBaseRateClient.fetchDailyBaseRateSeries(start, end)
                .flatMap(rows -> Blocking.call(() -> upsertDailyRows(rows)));
    }

    public Mono<BaseRate> findLatest(LocalDate asOfDate) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);

        return Blocking.call(() -> baseRateRepository
                        .findTopByStatCodeAndItemCodeAndCycleAndBaseDateLessThanEqualOrderByBaseDateDesc(
                                DEFAULT_STAT_CODE,
                                DEFAULT_ITEM_CODE,
                                DEFAULT_CYCLE,
                                targetDate
                        )
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    private Mono<BaseRate> refreshIfNeeded(BaseRate existing, LocalDate targetDate, LocalDate syncDate) {
        if (isSyncedOn(existing, syncDate)) {
            return Mono.just(existing);
        }

        return syncLatest()
                .then(findLatest(targetDate))
                .switchIfEmpty(Mono.justOrEmpty(existing));
    }

    private BaseRate upsertLatestRow(BokKeyStatisticListResponse.Row row) {
        String rawTime = requireText(row.getCycle(), "cycle");
        BaseRate entity = baseRateRepository
                .findByStatCodeAndItemCodeAndCycleAndRawTime(
                        DEFAULT_STAT_CODE,
                        DEFAULT_ITEM_CODE,
                        DEFAULT_CYCLE,
                        rawTime
                )
                .orElseGet(BaseRate::new);

        entity.setBaseDate(parseBaseDate(rawTime, DEFAULT_CYCLE));
        entity.setRawTime(rawTime);
        entity.setCycle(DEFAULT_CYCLE);
        entity.setRateValue(parseRateValue(row.getDataValue()));
        entity.setUnitName(requireText(row.getUnitName(), "unitName"));
        entity.setStatCode(DEFAULT_STAT_CODE);
        entity.setStatName(DEFAULT_STAT_NAME);
        entity.setItemCode(DEFAULT_ITEM_CODE);
        entity.setItemName(DEFAULT_ITEM_NAME);
        entity.setSource(DEFAULT_SOURCE);

        BaseRate saved = baseRateRepository.save(entity);
        log.info("[BaseRateSync] latest upsert complete. rawTime={}, value={}",
                saved.getRawTime(), saved.getRateValue());
        return saved;
    }

    private List<BaseRate> upsertDailyRows(List<BokStatisticSearchResponse.Row> rows) {
        List<BaseRate> saved = new ArrayList<>();

        for (BokStatisticSearchResponse.Row row : rows) {
            String statCode = requireText(row.getStatCode(), "statCode");
            String itemCode = requireText(row.getItemCode1(), "itemCode1");
            String rawTime = requireText(row.getTime(), "time");

            BaseRate entity = baseRateRepository
                    .findByStatCodeAndItemCodeAndCycleAndRawTime(
                            statCode,
                            itemCode,
                            DEFAULT_CYCLE,
                            rawTime
                    )
                    .orElseGet(BaseRate::new);

            entity.setBaseDate(parseBaseDate(rawTime, DEFAULT_CYCLE));
            entity.setRawTime(rawTime);
            entity.setCycle(DEFAULT_CYCLE);
            entity.setRateValue(parseRateValue(row.getDataValue()));
            entity.setUnitName(requireText(row.getUnitName(), "unitName"));
            entity.setStatCode(statCode);
            entity.setStatName(requireText(row.getStatName(), "statName"));
            entity.setItemCode(itemCode);
            entity.setItemName(requireText(row.getItemName1(), "itemName1"));
            entity.setSource(DEFAULT_SOURCE);

            saved.add(baseRateRepository.save(entity));
        }

        log.info("[BaseRateSync] daily backfill complete. rows={}", saved.size());
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
            default -> throw new IllegalArgumentException("Unsupported base rate cycle: " + cycle);
        };
    }

    private BigDecimal parseRateValue(String value) {
        String normalized = requireText(value, "dataValue").replace(",", "");
        return new BigDecimal(normalized);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("base rate " + fieldName + " is required");
        }
        return value.trim();
    }
}
