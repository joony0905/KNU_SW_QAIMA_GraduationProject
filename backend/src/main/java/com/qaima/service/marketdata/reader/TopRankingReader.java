package com.qaima.service.marketdata.reader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.Blocking;
import com.qaima.domain.Stock;
import com.qaima.dto.featuredstock.FeaturedStockDto;
import com.qaima.dto.featuredstock.FeaturedStockTopic;
import com.qaima.external.KrStockClient;
import com.qaima.repository.StockRepository;
import com.qaima.service.tradingcalendar.TradingCalendarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TopRankingReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_CACHE_START = LocalTime.of(8, 30);
    private static final LocalTime MARKET_CACHE_END = LocalTime.of(18, 0);
    private static final LocalTime PRE_OPEN_CACHE_CUTOFF = LocalTime.of(8, 25);
    private static final Duration MARKET_TTL = Duration.ofSeconds(10);
    private static final DateTimeFormatter CACHE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final TypeReference<List<FeaturedStockDto>> FEATURED_STOCK_LIST_TYPE = new TypeReference<>() {};
    private static final String KRX_MARKET = "KRX";

    private final KrStockClient krStockClient;
    private final StockRepository stockRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TradingCalendarService tradingCalendarService;

    public TopRankingReader(
            KrStockClient krStockClient,
            StockRepository stockRepository,
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            TradingCalendarService tradingCalendarService
    ) {
        this.krStockClient = krStockClient;
        this.stockRepository = stockRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tradingCalendarService = tradingCalendarService;
    }

    public Mono<List<FeaturedStockDto>> fetch(FeaturedStockTopic topic, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        CachePolicy policy = cachePolicy(topic, safeLimit);
        if (!policy.enabled()) {
            return fetchFresh(topic, safeLimit);
        }

        return redisTemplate.opsForValue()
                .get(policy.key())
                .flatMap(this::deserialize)
                .onErrorResume(ex -> {
                    log.warn("[TopRankingReader] ranking cache read failed. key={}, cause={}",
                            policy.key(), safeMessage(ex));
                    return Mono.empty();
                })
                .switchIfEmpty(fetchFresh(topic, safeLimit)
                        .flatMap(items -> cache(policy, items).thenReturn(items)));
    }

    private Mono<List<FeaturedStockDto>> fetchFresh(FeaturedStockTopic topic, int safeLimit) {
        return krStockClient.fetchFeaturedStocks(topic, safeLimit)
                .flatMap(this::filterSupportedEquities);
    }

    private Mono<Void> cache(CachePolicy policy, List<FeaturedStockDto> items) {
        if (policy.ttl().isZero() || policy.ttl().isNegative()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(items))
                .flatMap(json -> redisTemplate.opsForValue().set(policy.key(), json, policy.ttl()))
                .doOnError(ex -> log.warn("[TopRankingReader] ranking cache write failed. key={}, cause={}",
                        policy.key(), safeMessage(ex)))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    private Mono<List<FeaturedStockDto>> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> objectMapper.readValue(json, FEATURED_STOCK_LIST_TYPE));
    }

    private CachePolicy cachePolicy(FeaturedStockTopic topic, int limit) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalTime time = now.toLocalTime();
        LocalDate today = now.toLocalDate();
        boolean tradingDay = tradingCalendarService.isTradingDay(today, KRX_MARKET);

        if (tradingDay && !time.isBefore(PRE_OPEN_CACHE_CUTOFF) && time.isBefore(MARKET_CACHE_START)) {
            return CachePolicy.disabled();
        }

        LocalDate rankingDate = rankingDate(today, time, tradingDay);
        String key = "ranking-stocks:%s:%s:%d".formatted(rankingDate.format(CACHE_DATE_FORMATTER), topic, limit);

        if (tradingDay && !time.isBefore(MARKET_CACHE_START) && time.isBefore(MARKET_CACHE_END)) {
            return new CachePolicy(true, key, MARKET_TTL);
        }

        LocalDate cutoffDate = nextPreOpenCutoffDate(today, time, tradingDay);
        Duration ttl = Duration.between(now, LocalDateTime.of(cutoffDate, PRE_OPEN_CACHE_CUTOFF));
        return !ttl.isZero() && !ttl.isNegative() ? new CachePolicy(true, key, ttl) : CachePolicy.disabled();
    }

    private LocalDate rankingDate(LocalDate today, LocalTime time, boolean tradingDay) {
        if (tradingDay && !time.isBefore(PRE_OPEN_CACHE_CUTOFF)) {
            return today;
        }
        return tradingCalendarService.previousTradingDay(today, KRX_MARKET);
    }

    private LocalDate nextPreOpenCutoffDate(LocalDate today, LocalTime time, boolean tradingDay) {
        if (tradingDay && time.isBefore(PRE_OPEN_CACHE_CUTOFF)) {
            return today;
        }
        return tradingCalendarService.nextTradingDay(today, KRX_MARKET);
    }

    private Mono<List<FeaturedStockDto>> filterSupportedEquities(List<FeaturedStockDto> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return Mono.just(List.of());
        }

        List<String> stockCodes = rankings.stream()
                .map(FeaturedStockDto::stockCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        return Blocking.call(() -> stockRepository.findByStockCodeIn(stockCodes))
                .map(stocks -> {
                    Map<String, Stock> supported = stocks.stream()
                            .filter(stock -> "EQUITY".equalsIgnoreCase(stock.getAssetType()))
                            .collect(Collectors.toMap(
                                    stock -> stock.getStockCode().toUpperCase(Locale.ROOT),
                                    Function.identity(),
                                    (left, right) -> left
                            ));

                    return rankings.stream()
                            .map(item -> enrichIfSupported(item, supported))
                            .flatMap(List::stream)
                            .toList();
                });
    }

    private List<FeaturedStockDto> enrichIfSupported(
            FeaturedStockDto item,
            Map<String, Stock> supported
    ) {
        if (item.stockCode() == null) {
            return List.of();
        }

        Stock stock = supported.get(item.stockCode().toUpperCase(Locale.ROOT));
        if (stock == null) {
            return List.of();
        }

        String exchangeCode = stock.getExchange() != null ? stock.getExchange().getCode() : item.exchangeCode();
        return List.of(new FeaturedStockDto(
                stock.getStockId(),
                item.stockCode(),
                item.companyName(),
                exchangeCode,
                item.price(),
                item.volume(),
                item.change(),
                item.changeRate()
        ));
    }

    private String safeMessage(Throwable ex) {
        String message = ex == null ? null : ex.getMessage();
        return message == null || message.isBlank() ? "n/a" : message;
    }

    private record CachePolicy(boolean enabled, String key, Duration ttl) {
        static CachePolicy disabled() {
            return new CachePolicy(false, "", Duration.ZERO);
        }
    }
}
