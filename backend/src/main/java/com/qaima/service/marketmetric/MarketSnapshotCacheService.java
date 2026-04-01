package com.qaima.service.marketmetric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.Blocking;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.service.marketmetric.model.SnapshotCacheEntry;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MarketSnapshotCacheService {

    private static final Duration SNAPSHOT_TTL = Duration.ofHours(1);
    private static final Duration SNAPSHOT_LATEST_TTL = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final MarketSnapshotRepository marketSnapshotRepository;

    public MarketSnapshotCacheService(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper,
            MarketSnapshotRepository marketSnapshotRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.redisObjectMapper = redisObjectMapper;
        this.marketSnapshotRepository = marketSnapshotRepository;
    }

    public Mono<SnapshotMetricView> getSnapshot(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.empty();
        }

        String latestKey = latestKey(stock.getStockCode());
        return readKey(latestKey, asOfDate)
                .switchIfEmpty(readKey(snapshotKey(stock.getStockCode()), asOfDate))
                .switchIfEmpty(Blocking.call(() -> findSnapshot(stock, asOfDate))
                        .flatMap(snapshot -> snapshot == null ? Mono.empty() : cacheSnapshot(stock.getStockCode(), snapshot)));
    }

    public Mono<SnapshotMetricView> cacheSnapshot(String stockCode, MarketSnapshot snapshot) {
        if (stockCode == null || snapshot == null) {
            return Mono.empty();
        }

        SnapshotCacheEntry entry = toEntry(snapshot);
        String json;
        try {
            json = redisObjectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            log.warn("[MarketSnapshotCache] serialize failed. stockCode={}", stockCode, e);
            return Mono.just(toView(entry, List.of()));
        }

        return redisTemplate.opsForValue().set(snapshotKey(stockCode), json, SNAPSHOT_TTL)
                .onErrorResume(ex -> {
                    log.warn("[MarketSnapshotCache] snapshot cache SET failed. stockCode={}", stockCode, ex);
                    return Mono.just(false);
                })
                .then(redisTemplate.opsForValue().set(latestKey(stockCode), json, SNAPSHOT_LATEST_TTL)
                        .onErrorResume(ex -> {
                            log.warn("[MarketSnapshotCache] latest cache SET failed. stockCode={}", stockCode, ex);
                            return Mono.just(false);
                        }))
                .thenReturn(toView(entry, List.of()));
    }

    private Mono<SnapshotMetricView> readKey(String key, LocalDate asOfDate) {
        return redisTemplate.opsForValue()
                .get(key)
                .onErrorResume(ex -> {
                    log.warn("[MarketSnapshotCache] cache GET failed. key={}", key, ex);
                    return Mono.empty();
                })
                .flatMap(json -> deserialize(json, asOfDate));
    }

    private Mono<SnapshotMetricView> deserialize(String json, LocalDate asOfDate) {
        try {
            SnapshotCacheEntry entry = redisObjectMapper.readValue(json, SnapshotCacheEntry.class);
            if (asOfDate != null && entry.getAsOfDate() != null && entry.getAsOfDate().isAfter(asOfDate)) {
                return Mono.empty();
            }
            return Mono.just(toView(entry, List.of()));
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private MarketSnapshot findSnapshot(Stock stock, LocalDate asOfDate) {
        return (asOfDate == null
                ? marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock)
                : marketSnapshotRepository.findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate))
                .orElse(null);
    }

    private SnapshotCacheEntry toEntry(MarketSnapshot snapshot) {
        return SnapshotCacheEntry.builder()
                .asOfDate(snapshot.getAsOfDate())
                .sharesOutstanding(snapshot.getSharesOutstanding())
                .floatingShares(snapshot.getFloatRatio() != null && snapshot.getSharesOutstanding() != null
                        ? snapshot.getSharesOutstanding()
                        .multiply(snapshot.getFloatRatio())
                        .movePointLeft(2)
                        : null)
                .treasuryShares(snapshot.getTreasuryRatio() != null && snapshot.getSharesOutstanding() != null
                        ? snapshot.getSharesOutstanding()
                        .multiply(snapshot.getTreasuryRatio())
                        .movePointLeft(2)
                        : null)
                .epsTtm(snapshot.getEpsTtm())
                .bps(snapshot.getBps())
                .sps(snapshot.getSps())
                .roe(snapshot.getRoe())
                .roa(snapshot.getRoa())
                .operatingMargin(snapshot.getOperatingMargin())
                .netMargin(snapshot.getNetMargin())
                .debtRatio(snapshot.getDebtRatio())
                .currentAssets(snapshot.getCurrentAssets())
                .currentLiabilities(snapshot.getCurrentLiabilities())
                .inventory(snapshot.getInventory())
                .interestExpense(snapshot.getInterestExpense())
                .operatingCashFlow(snapshot.getOperatingCashFlow())
                .capex(snapshot.getCapex())
                .warnings(parseWarnings(snapshot.getWarningFlags()))
                .source(snapshot.getSource())
                .build();
    }

    private SnapshotMetricView toView(SnapshotCacheEntry entry, List<String> warnings) {
        return new SnapshotMetricView(
                entry.getAsOfDate(),
                entry.getSharesOutstanding(),
                entry.getFloatingShares(),
                entry.getTreasuryShares(),
                entry.getEpsTtm(),
                entry.getBps(),
                entry.getSps(),
                entry.getRoe(),
                entry.getRoa(),
                entry.getOperatingMargin(),
                entry.getNetMargin(),
                entry.getDebtRatio(),
                entry.getCurrentAssets(),
                entry.getCurrentLiabilities(),
                entry.getInventory(),
                entry.getInterestExpense(),
                entry.getOperatingCashFlow(),
                entry.getCapex(),
                mergeWarnings(entry.getWarnings(), warnings),
                entry.getSource()
        );
    }

    private List<String> parseWarnings(String warningFlags) {
        if (warningFlags == null || warningFlags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(warningFlags.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> mergeWarnings(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(
                        (left == null ? List.<String>of() : left).stream(),
                        (right == null ? List.<String>of() : right).stream()
                )
                .distinct()
                .toList();
    }

    private String latestKey(String stockCode) {
        return "snapshot_latest:" + stockCode;
    }

    private String snapshotKey(String stockCode) {
        return "snapshot:" + stockCode;
    }
}
