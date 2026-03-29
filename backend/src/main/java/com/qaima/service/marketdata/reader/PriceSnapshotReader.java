package com.qaima.service.marketdata.reader;

import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.service.marketdata.model.PriceSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSnapshotReader {

    private static final Duration TTL = Duration.ofSeconds(120);

    private final PriceOhlcvRepository priceOhlcvRepository;
    private final ReactiveRedisTemplate<String, PriceSnapshot> redisTemplate;

    private final ConcurrentHashMap<String, Mono<PriceSnapshot>> inflight = new ConcurrentHashMap<>();

    public Mono<PriceSnapshot> getSnapshot(String stockCode, Freq freq) {
        String key = buildKey(stockCode, freq);

        return redisTemplate.opsForValue()
                .get(key)
                .onErrorResume(ex -> {
                    log.warn("[PriceSnapshot] cache GET failed. fallback to DB. key={}", key, ex);
                    return Mono.empty();
                })
                .doOnNext(v -> log.debug("[PriceSnapshot] cache HIT key={}", key))
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.debug("[PriceSnapshot] cache MISS key={}", key);

                            return inflight.computeIfAbsent(key, k ->
                                    Mono.defer(() -> {
                                                log.debug("[PriceSnapshot] DB fetch start key={}", k);

                                                return Mono.fromCallable(() -> fetchLatestTwo(stockCode, freq))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .map(this::toSnapshot)
                                                        .flatMap(snapshot -> {
                                                            if (snapshot == null) {
                                                                log.debug("[PriceSnapshot] snapshot null (skip cache) key={}", k);
                                                                return Mono.empty();
                                                            }

                                                            return redisTemplate.opsForValue()
                                                                    .set(k, snapshot, TTL)
                                                                    .doOnNext(saved -> log.debug(
                                                                            "[PriceSnapshot] cache SET key={}, saved={}",
                                                                            k, saved
                                                                    ))
                                                                    .onErrorResume(ex -> {
                                                                        log.warn("[PriceSnapshot] cache SET failed. continue without cache. key={}", k, ex);
                                                                        return Mono.just(false);
                                                                    })
                                                                    .thenReturn(snapshot);
                                                        });
                                            })
                                            .cache()
                                            .doFinally(sig -> {
                                                inflight.remove(k);
                                                log.debug("[PriceSnapshot] inflight cleared key={}", k);
                                            })
                            );
                        })
                );
    }

    private String buildKey(String stockCode, Freq freq) {
        return "price:snapshot:" + stockCode + ":" + freq.name();
    }

    private List<PriceOhlcv> fetchLatestTwo(String stockCode, Freq freq) {
        return priceOhlcvRepository.findBefore(
                stockCode,
                freq,
                OffsetDateTime.now(),
                PageRequest.of(0, 2)
        );
    }

    private PriceSnapshot toSnapshot(List<PriceOhlcv> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        BigDecimal latest = list.get(0).getClose();
        BigDecimal prev = (list.size() >= 2) ? list.get(1).getClose() : null;

        BigDecimal changeRate = null;
        if (latest != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
            changeRate = latest.subtract(prev)
                    .divide(prev, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return PriceSnapshot.builder()
                .price(latest)
                .prevPrice(prev)
                .changeRate(changeRate)
                .build();
    }
}