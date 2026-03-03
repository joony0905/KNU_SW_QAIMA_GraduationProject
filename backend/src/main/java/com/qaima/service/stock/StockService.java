package com.qaima.service.stock;

import com.qaima.domain.Exchange;
import com.qaima.domain.Industry;
import com.qaima.domain.Sector;
import com.qaima.domain.Stock;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockMeta;
import com.qaima.external.StockClient;
import com.qaima.repository.ExchangeRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.resolver.IndustryResolver;
import com.qaima.service.resolver.SectorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockClient stockClient;
    private final ExchangeRepository exchangeRepository;
    private final SectorResolver sectorResolver;
    private final IndustryResolver industryResolver;

    /**
     * single-flight 보호용 inflight 캐시
     * - 같은 stockCode에 대한 외부 호출을 1회로 제한
     */
    private final ConcurrentHashMap<String, Mono<Stock>> inflight = new ConcurrentHashMap<>();

    /* ===========================
     * Public APIs
     * =========================== */

    public Mono<StockDto> getStockWithRealtime(Long stockId) {
        return Mono.fromCallable(() ->
                        stockRepository.findById(stockId)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("존재하지 않는 종목 ID: " + stockId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stockClient::fetchStock);
    }

    public Mono<StockDto> getStockWithRealtimeByCode(String rawStockCode) {
        return loadOrCreateStockMono(rawStockCode)
                .flatMap(stockClient::fetchStock);
    }

    public Mono<Stock> getOrCreateStockByCode(String rawStockCode) {
        return loadOrCreateStockMono(rawStockCode);
    }

    /* ===========================
     * Core Logic
     * =========================== */

    private Mono<Stock> loadOrCreateStockMono(String rawStockCode) {
        String key = extractStockCode(rawStockCode);

        Mono<Stock> hit = inflight.get(key);
        if (hit != null) return hit;

        AtomicReference<Mono<Stock>> ref = new AtomicReference<>();

        Mono<Stock> candidate = Mono.defer(() ->
                        Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(key))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(optional -> {
                                    if (optional.isPresent()) return Mono.just(optional.get());

                                    log.info("[loadOrCreateStock] DB 미존재 → 외부 메타 조회 시작: {}", key);

                                    return stockClient.fetchTickerMeta(key)
                                            .flatMap(apiResponse -> {
                                                if (apiResponse == null) {
                                                    log.warn("[loadOrCreateStock] 메타 null → minimal stock 생성: {}", key);
                                                    return createMinimalStock(key);
                                                }
                                                if (!apiResponse.isSuccess() || apiResponse.getData() == null) {
                                                    log.warn("[loadOrCreateStock] 메타 soft-fail → minimal stock 생성: code={}, errors={}",
                                                            key, apiResponse.getErrors());
                                                    return createMinimalStock(key);
                                                }
                                                return createAndSaveStockFromMeta(key, apiResponse.getData());
                                            });
                                })
                )
                .cache();

        ref.set(candidate);

        Mono<Stock> raced = inflight.putIfAbsent(key,
                candidate.doFinally(sig -> inflight.remove(key, ref.get()))
        );

        return raced != null ? raced : inflight.get(key);
    }

    /* ===========================
     * Stock Creation
     * =========================== */

    private Mono<Stock> createAndSaveStockFromMeta(String normalizedCode, StockMeta meta) {

        String exchangeCode = normalizeExchangeCode(meta.getExchangeCode());
        if (exchangeCode == null || exchangeCode.isBlank()) {
            log.warn("[createStock] exchangeCode 없음 → minimal stock: {}", normalizedCode);
            return createMinimalStock(normalizedCode);
        }

        // Exchange (필수)
        Mono<Exchange> exchangeMono =
                Mono.fromCallable(() ->
                                exchangeRepository.findByCode(exchangeCode)
                                        .orElseThrow(() ->
                                                new IllegalStateException("DB Exchange 미존재: " + exchangeCode))
                        )
                        .subscribeOn(Schedulers.boundedElastic());

        // Sector (KIS 중분류, 필수 정책)
        Mono<Sector> sectorMono =
                sectorResolver.resolve(meta.getSectorCode(), meta.getSectorName())
                        .doOnNext(sec ->
                                log.info("[SectorResolver] resolved: scheme={} code={} name={}",
                                        SectorResolver.SCHEME_KRX_BZTP_M,
                                        sec.getCode(), sec.getName()
                                )
                        );

        // Industry (KIS 소분류, Sector 의존)
        Mono<Industry> industryMono =
                sectorMono.flatMap(sec ->
                        industryResolver.resolve(
                                        meta.getIndustryCode(),
                                        meta.getIndustryName(),
                                        sec
                                )
                                .doOnNext(ind ->
                                        log.info("[IndustryResolver] resolved: scheme={} code={} name={} sector={}",
                                                IndustryResolver.SCHEME_KRX_BZTP_S,
                                                ind.getCode(), ind.getName(),
                                                sec.getCode()
                                        )
                                )
                );

        // 조합 후 Stock 생성/조회
        return Mono.zip(exchangeMono, industryMono)
                .flatMap(tuple -> {
                    Exchange exchange = tuple.getT1();
                    Industry industry = tuple.getT2();

                    return Mono.fromCallable(() ->
                                    stockRepository.findByExchangeAndStockCode(exchange, normalizedCode)
                            )
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(opt -> opt
                                    .map(existing -> {
                                        log.info("[createStock] existing stock reused: {}", normalizedCode);
                                        return Mono.just(existing);
                                    })
                                    .orElseGet(() ->
                                            Mono.fromCallable(() -> {
                                                        Stock stock = new Stock();
                                                        stock.setStockCode(normalizedCode);   // ✅ KIS pdno 안 씀
                                                        stock.setCompanyName(meta.getCompanyName());
                                                        stock.setAssetType("EQUITY");
                                                        stock.setCurrency(
                                                                meta.getCurrency() != null ? meta.getCurrency() : "KRW"
                                                        );
                                                        stock.setExchange(exchange);
                                                        stock.setIndustry(industry);          // ✅ FK 명시
                                                        stock.setIsin(null);

                                                        log.info(
                                                                "[createStock] new stock saved: code={}, exchange={}, sector={}, industry={}",
                                                                normalizedCode,
                                                                exchange.getCode(),
                                                                industry.getSector().getCode(),
                                                                industry.getCode()
                                                        );

                                                        return stockRepository.save(stock);
                                                    })
                                                    .subscribeOn(Schedulers.boundedElastic())
                                    )
                            );
                });
    }

    /**
     * 메타 정보가 없거나 불완전할 때 생성하는 최소 Stock
     */
    private Mono<Stock> createMinimalStock(String normalizedCode) {
        return Mono.fromCallable(() -> {
                    Exchange krx = exchangeRepository.findByCode("KRX")
                            .orElseThrow(() -> new IllegalStateException("KRX Exchange 미존재"));

                    Optional<Stock> existing =
                            stockRepository.findByExchangeAndStockCode(krx, normalizedCode);

                    if (existing.isPresent()) {
                        return existing.get();
                    }

                    Stock stock = new Stock();
                    stock.setStockCode(normalizedCode);
                    stock.setCompanyName(normalizedCode);
                    stock.setAssetType("EQUITY");
                    stock.setCurrency("KRW");
                    stock.setExchange(krx);
                    stock.setIndustry(null);
                    stock.setIsin(null);
                    return stockRepository.save(stock);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /* ===========================
     * Utils
     * =========================== */

    private String extractStockCode(String symbol) {
        if (symbol == null) return null;
        int dotIdx = symbol.indexOf('.');
        return (dotIdx > 0) ? symbol.substring(0, dotIdx) : symbol.trim();
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;

        String normalized = exchangeCode.trim().toUpperCase();
        if (normalized.startsWith("KRX ")) {
            return "KRX";
        }

        String condensed = normalized.replace(" ", "");

        return switch (condensed) {
            case "XKRX", "KRX", "KRXSM" -> "KRX";
            case "XKOS" -> "KOSDAQ";
            case "XKON" -> "KONEX";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> normalized;
        };
    }
}