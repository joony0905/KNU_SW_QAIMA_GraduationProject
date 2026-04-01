package com.qaima.service.marketmetric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.Exchange;
import com.qaima.domain.Stock;
import com.qaima.external.KrStockClient;
import com.qaima.service.marketmetric.model.PriceCacheEntry;
import com.qaima.service.marketmetric.model.PriceQuoteResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RealtimePriceService {

    private static final Duration PRICE_TTL = Duration.ofSeconds(20);
    private static final Duration PRICE_STALE_TTL = Duration.ofMinutes(20);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final KrStockClient krStockClient;

    public RealtimePriceService(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper,
            KrStockClient krStockClient
    ) {
        this.redisTemplate = redisTemplate;
        this.redisObjectMapper = redisObjectMapper;
        this.krStockClient = krStockClient;
    }

    public Mono<BigDecimal> getPrice(Stock stock) {
        return getPriceQuote(stock).map(PriceQuoteResult::price).flatMap(Mono::justOrEmpty);
    }

    public Mono<PriceQuoteResult> getPriceQuote(Stock stock) {
        if (stock == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return Mono.just(new PriceQuoteResult(null, List.of("PRICE_FETCH_FAILED"), false));
        }

        String freshKey = freshKey(stock.getStockCode());
        String staleKey = staleKey(stock.getStockCode());
        return redisTemplate.opsForValue().get(freshKey)
                .onErrorResume(ex -> Mono.empty())
                .flatMap(this::deserializePrice)
                .map(price -> new PriceQuoteResult(price, List.of(), false))
                .switchIfEmpty(fetchAndCache(stock, freshKey, staleKey)
                        .onErrorResume(ex -> readStale(staleKey)
                                .map(price -> new PriceQuoteResult(price, List.of("PRICE_STALE_USED"), true))
                                .switchIfEmpty(Mono.just(new PriceQuoteResult(null, List.of("PRICE_FETCH_FAILED"), false)))));
    }

    private Mono<PriceQuoteResult> fetchAndCache(Stock stock, String freshKey, String staleKey) {
        String marketDivCode = toKisMarketDivCode(stock.getExchange());
        if ("B".equals(marketDivCode)) {
            return readStale(staleKey)
                    .map(price -> new PriceQuoteResult(price, List.of("PRICE_STALE_USED"), true))
                    .switchIfEmpty(Mono.just(new PriceQuoteResult(null, List.of("PRICE_FETCH_FAILED"), false)));
        }

        return krStockClient.fetchKisStatRaw(stock.getStockCode(), marketDivCode)
                .map(output -> parsePrice(output.getStck_prpr()))
                .flatMap(price -> {
                    if (price == null) {
                        return readStale(staleKey)
                                .map(stale -> new PriceQuoteResult(stale, List.of("PRICE_STALE_USED"), true))
                                .switchIfEmpty(Mono.just(new PriceQuoteResult(null, List.of("PRICE_FETCH_FAILED"), false)));
                    }
                    PriceCacheEntry entry = PriceCacheEntry.builder()
                            .price(price)
                            .fetchedAt(Instant.now())
                            .build();
                    try {
                        String json = redisObjectMapper.writeValueAsString(entry);
                        return redisTemplate.opsForValue().set(freshKey, json, PRICE_TTL)
                                .onErrorResume(ex -> Mono.just(false))
                                .then(redisTemplate.opsForValue().set(staleKey, json, PRICE_STALE_TTL)
                                        .onErrorResume(ex -> Mono.just(false)))
                                .thenReturn(new PriceQuoteResult(price, List.of(), false));
                    } catch (JsonProcessingException e) {
                        return Mono.just(new PriceQuoteResult(price, List.of(), false));
                    }
                })
                .onErrorResume(ex -> readStale(staleKey)
                        .map(price -> new PriceQuoteResult(price, List.of("PRICE_STALE_USED"), true))
                        .switchIfEmpty(Mono.just(new PriceQuoteResult(null, List.of("PRICE_FETCH_FAILED"), false))));
    }

    private Mono<BigDecimal> deserializePrice(String json) {
        try {
            PriceCacheEntry entry = redisObjectMapper.readValue(json, PriceCacheEntry.class);
            return Mono.justOrEmpty(entry.getPrice());
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    private Mono<BigDecimal> readStale(String key) {
        return redisTemplate.opsForValue().get(key)
                .onErrorResume(ex -> Mono.empty())
                .flatMap(this::deserializePrice);
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("[RealtimePriceService] invalid KIS price={}", raw, e);
            return null;
        }
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null || exchange.getCode() == null) {
            return "B";
        }
        return switch (exchange.getCode().trim().toUpperCase()) {
            case "KRX", "XKRX", "KOSPI" -> "J";
            case "KOSDAQ", "XKOS" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    private String freshKey(String stockCode) {
        return "price:" + stockCode;
    }

    private String staleKey(String stockCode) {
        return "price_stale:" + stockCode;
    }
}
