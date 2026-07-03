package com.qaima.service.stock;

import com.qaima.domain.Exchange;
import com.qaima.domain.Industry;
import com.qaima.domain.Sector;
import com.qaima.domain.Stock;
import com.qaima.common.exception.ResourceNotFoundException;
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
     * - 같은 stockCode에 대한 등록 시도를 1회로 제한
     */
    private final ConcurrentHashMap<String, Mono<Stock>> inflight = new ConcurrentHashMap<>();

    /* ===========================
     * Public APIs
     * =========================== */

    public Mono<StockDto> getStockWithRealtime(Long stockId) {
        return Mono.fromCallable(() ->
                        stockRepository.findByIdWithExchangeAndIndustry(stockId)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("존재하지 않는 종목 ID: " + stockId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stockClient::fetchStock);
    }

    /**
     * 조회 전용 경로
     *
     * 정책:
     * - DB에 있으면 기존 엔티티로 fetchStock
     * - DB에 없으면
     *   1) 메타 조회 성공
     *   2) 임시 Stock으로 실시간 조회 성공
     *   3) 그때만 save
     *   4) 저장된 엔티티 기준으로 최종 fetchStock
     *
     * => 최종 조회 실패 시 insert 안 됨
     */
    public Mono<StockDto> getStockWithRealtimeByCode(String rawStockCode) {
        String code = normalizeStockCode(extractStockCode(rawStockCode));

        if (code == null || code.isBlank()) {
            return Mono.error(new IllegalArgumentException("종목코드가 비어있습니다."));
        }

        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchangeAndIndustry(code))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> optional
                        .map(existing -> {
                            log.info("[getStockWithRealtimeByCode] existing stock hit: {}", code);
                            return stockClient.fetchStock(existing)
                                    .onErrorResume(ex -> {
                                        log.warn(
                                                "[getStockWithRealtimeByCode] realtime quote failed; returning static stock info. stockCode={}, cause={}",
                                                code,
                                                ex.toString()
                                        );
                                        return Mono.just(toStaticStockDto(existing));
                                    });
                        })
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("Unknown stockCode: " + code)))
                );
    }

    private StockDto toStaticStockDto(Stock stock) {
        return StockDto.builder()
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName())
                .exchangeId(stock.getExchange() != null ? stock.getExchange().getExchangeId() : null)
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(stock.getIndustry() != null ? stock.getIndustry().getIndustryId() : null)
                .listedAt(stock.getListedAt())
                .delistedAt(stock.getDelistedAt())
                .price(null)
                .changeRate(null)
                .build();
    }

    /**
     * 등록/부트스트랩 경로
     *
     * 정책:
     * - DB에 있으면 그대로 반환
     * - DB에 없으면 메타 조회 성공 + 정합성 확인 후에만 save
     * - minimal fallback 없음
     */
    public Mono<Stock> getOrCreateStockByCode(String rawStockCode) {
        log.info("[StockService][getOrCreateStockByCode] incoming rawStockCode={}", rawStockCode);
        return getStockByCode(rawStockCode);
    }

    private Mono<Stock> getStockByCode(String rawStockCode) {
        String code = normalizeStockCode(extractStockCode(rawStockCode));

        if (code == null || code.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchangeAndIndustry(code))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> optional
                        .map(existing -> {
                            log.info("[getStockByCode] existing stock hit: {}", code);
                            return Mono.just(existing);
                        })
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("Unknown stockCode: " + code))));
    }

    /* ===========================
     * Realtime-first path (NO INSERT on final failure)
     * =========================== */

    private Mono<StockDto> fetchAndCreateOnlyAfterRealtimeSuccess(String normalizedCode) {
        log.info("[realtime-first] DB 미존재 → 외부 메타/시세 조회 시작: {}", normalizedCode);

        return stockClient.fetchTickerMeta(normalizedCode)
                .flatMap(apiResponse -> {
                    if (apiResponse == null) {
                        return Mono.error(new IllegalStateException(
                                "종목 메타 조회 실패(null): " + normalizedCode
                        ));
                    }

                    if (!apiResponse.isSuccess() || apiResponse.getData() == null) {
                        return Mono.error(new IllegalStateException(
                                "종목 메타 조회 실패: " + normalizedCode + ", errors=" + apiResponse.getErrors()
                        ));
                    }

                    StockMeta meta = apiResponse.getData();

                    validateMetaForPersist(normalizedCode, meta);

                    Stock transientStock = buildTransientStockFromMeta(normalizedCode, meta);

                    return stockClient.fetchStock(transientStock)
                            .filter(this::isUsableRealtimeQuote)
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "실시간 조회 실패 또는 유효한 price 없음: " + normalizedCode
                            )))
                            .flatMap(validQuote ->
                                    createAndSaveStockFromMeta(normalizedCode, meta)
                                            .flatMap(stockClient::fetchStock)
                            );
                });
    }

    /**
     * save 전에 실시간 조회 검증용 임시 Stock 생성
     * - DB insert 없음
     * - fetchStock()에 필요한 최소 static 정보만 세팅
     */
    private Stock buildTransientStockFromMeta(String normalizedCode, StockMeta meta) {
        Stock stock = new Stock();
        stock.setStockId(null);
        stock.setStockCode(normalizedCode);
        stock.setCompanyName(
                meta.getCompanyName() != null && !meta.getCompanyName().isBlank()
                        ? meta.getCompanyName()
                        : normalizedCode
        );
        stock.setAssetType("EQUITY");
        stock.setCurrency(
                meta.getCurrency() != null && !meta.getCurrency().isBlank()
                        ? meta.getCurrency()
                        : "KRW"
        );
        stock.setIsin(null);
        stock.setIndustry(null);

        Exchange exchange = new Exchange();
        exchange.setCode(normalizeExchangeCode(meta.getExchangeCode()));
        stock.setExchange(exchange);

        return stock;
    }

    private boolean isUsableRealtimeQuote(StockDto dto) {
        return dto != null && dto.getPrice() != null;
    }

    /* ===========================
     * Core Logic (verified create path only)
     * =========================== */

    private Mono<Stock> loadOrCreateStockMono(String rawStockCode) {
        String key = extractStockCode(rawStockCode);

        if (key == null || key.isBlank()) {
            return Mono.error(new IllegalArgumentException("종목코드가 비어있습니다."));
        }

        Mono<Stock> hit = inflight.get(key);
        if (hit != null) return hit;

        AtomicReference<Mono<Stock>> ref = new AtomicReference<>();

        Mono<Stock> candidate = Mono.defer(() ->
                        Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchangeAndIndustry(key))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(optional -> {
                                    if (optional.isPresent()) {
                                        log.info("[loadOrCreateStock] existing stock hit: {}", key);
                                        return Mono.just(optional.get());
                                    }

                                    log.info("[loadOrCreateStock] DB 미존재 → 외부 메타 조회 시작: {}", key);

                                    return stockClient.fetchTickerMeta(key)
                                            .flatMap(apiResponse -> {
                                                if (apiResponse == null) {
                                                    log.warn("[loadOrCreateStock] 메타 null → 저장 중단: {}", key);
                                                    return Mono.error(new IllegalStateException(
                                                            "종목 메타 조회 실패(null): " + key
                                                    ));
                                                }

                                                if (!apiResponse.isSuccess() || apiResponse.getData() == null) {
                                                    log.warn("[loadOrCreateStock] 메타 soft-fail → 저장 중단: code={}, errors={}",
                                                            key, apiResponse.getErrors());
                                                    return Mono.error(new IllegalStateException(
                                                            "종목 메타 조회 실패: " + key
                                                    ));
                                                }

                                                StockMeta meta = apiResponse.getData();
                                                validateMetaForPersist(key, meta);

                                                return createAndSaveStockFromMeta(key, meta);
                                            });
                                })
                )
                .cache();

        ref.set(candidate);

        Mono<Stock> raced = inflight.putIfAbsent(
                key,
                candidate.doFinally(sig -> inflight.remove(key, ref.get()))
        );

        return raced != null ? raced : inflight.get(key);
    }

    /* ===========================
     * Stock Creation
     * =========================== */

    private Mono<Stock> createAndSaveStockFromMeta(String normalizedCode, StockMeta meta) {
        validateMetaForPersist(normalizedCode, meta);

        String exchangeCode = normalizeExchangeCode(meta.getExchangeCode());

        Mono<Exchange> exchangeMono =
                Mono.fromCallable(() ->
                                exchangeRepository.findByCode(exchangeCode)
                                        .orElseThrow(() ->
                                                new IllegalStateException("DB Exchange 미존재: " + exchangeCode))
                        )
                        .subscribeOn(Schedulers.boundedElastic());

        return exchangeMono.flatMap(exchange -> {
            Mono<Sector> sectorMono =
                    sectorResolver.resolve(exchange, meta.getSectorCode(), meta.getSectorName())
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "Sector resolve 실패: exchange=" + exchange.getCode()
                                            + ", code=" + meta.getSectorCode()
                                            + ", name=" + meta.getSectorName()
                            )))
                            .doOnNext(sec ->
                                    log.info("[SectorResolver] resolved: exchange={} scheme={} code={} name={}",
                                            exchange.getCode(),
                                            SectorResolver.SCHEME_KRX_BZTP_M,
                                            sec.getCode(), sec.getName()
                                    )
                            );

            Mono<Industry> industryMono =
                    sectorMono.flatMap(sec -> {
                        if (meta.getIndustryCode() == null || meta.getIndustryCode().isBlank()) {
                            return Mono.empty();
                        }
                        return industryResolver.resolve(
                                        exchange,
                                        meta.getIndustryCode(),
                                        meta.getIndustryName(),
                                        sec
                                )
                                .switchIfEmpty(Mono.error(new IllegalStateException(
                                        "Industry resolve 실패: exchange=" + exchange.getCode()
                                                + ", sector=" + sec.getCode()
                                                + ", code=" + meta.getIndustryCode()
                                                + ", name=" + meta.getIndustryName()
                                )))
                                .doOnNext(ind ->
                                        log.info("[IndustryResolver] resolved: exchange={} scheme={} code={} name={} sector={}",
                                                exchange.getCode(),
                                                IndustryResolver.SCHEME_KRX_BZTP_S,
                                                ind.getCode(), ind.getName(),
                                                sec.getCode()
                                        )
                                );
                    });

            return sectorMono.flatMap(sector ->
                    industryMono
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(industryOpt -> {
                                Industry industry = industryOpt.orElse(null);

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
                                                                    stock.setStockCode(normalizedCode);
                                                                    stock.setCompanyName(
                                                                            meta.getCompanyName() != null && !meta.getCompanyName().isBlank()
                                                                                    ? meta.getCompanyName()
                                                                                    : normalizedCode
                                                                    );
                                                                    stock.setAssetType("EQUITY");
                                                                    stock.setCurrency(
                                                                            meta.getCurrency() != null && !meta.getCurrency().isBlank()
                                                                                    ? meta.getCurrency()
                                                                                    : "KRW"
                                                                    );
                                                                    stock.setExchange(exchange);
                                                                    stock.setSector(sector);
                                                                    stock.setIndustry(industry);
                                                                    stock.setIsin(null);

                                                                    log.info(
                                                                            "[createStock] new stock saved: code={}, exchange={}, sector={}, industry={}",
                                                                            normalizedCode,
                                                                            exchange.getCode(),
                                                                            sector.getCode(),
                                                                            industry != null ? industry.getCode() : null
                                                                    );

                                                                    return stockRepository.save(stock);
                                                                })
                                                                .subscribeOn(Schedulers.boundedElastic())
                                                )
                                        );
                            })
            );
        });
    }

    /* ===========================
     * Validation
     * =========================== */

    /**
     * 저장 가능한 최소 메타 정합성 검증
     *
     * 현재 정책:
     * - exchangeCode 필수
     * - companyName 권장 (없으면 code fallback 허용)
     * - 국내주식(6자리 숫자)은 sector 코드/이름 필수
     * - industry는 선택이며, sector가 있을 때만 저장 가능
     */
    private void validateMetaForPersist(String normalizedCode, StockMeta meta) {
        if (meta == null) {
            throw new IllegalStateException("StockMeta가 null입니다: " + normalizedCode);
        }

        String exchangeCode = normalizeExchangeCode(meta.getExchangeCode());
        if (exchangeCode == null || exchangeCode.isBlank()) {
            throw new IllegalStateException("exchangeCode 없음. stock 저장 불가: " + normalizedCode);
        }

        boolean isKoreanStock = normalizedCode.matches("^[0-9]{6}$");
        if (isKoreanStock) {
            if (meta.getSectorCode() == null || meta.getSectorCode().isBlank()) {
                throw new IllegalStateException("국내주식 sectorCode 없음. stock 저장 불가: " + normalizedCode);
            }
            if (meta.getSectorName() == null || meta.getSectorName().isBlank()) {
                throw new IllegalStateException("국내주식 sectorName 없음. stock 저장 불가: " + normalizedCode);
            }
            if (meta.getIndustryCode() != null && !meta.getIndustryCode().isBlank()
                    && (meta.getIndustryName() == null || meta.getIndustryName().isBlank())) {
                throw new IllegalStateException("국내주식 industryCode는 있으나 industryName 없음. stock 저장 불가: " + normalizedCode);
            }
        }
    }

    /* ===========================
     * Utils
     * =========================== */

    private String extractStockCode(String symbol) {
        if (symbol == null) return null;
        int dotIdx = symbol.indexOf('.');
        return (dotIdx > 0) ? symbol.substring(0, dotIdx) : symbol.trim();
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null) return null;

        String trimmed = stockCode.trim().toUpperCase();
        if (trimmed.matches("\\d{1,5}")) {
            return String.format("%6s", trimmed).replace(' ', '0');
        }
        return trimmed;
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
