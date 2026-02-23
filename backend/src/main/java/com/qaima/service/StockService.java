package com.qaima.service;

import com.qaima.domain.Stock;
import com.qaima.dto.StockMeta;
import com.qaima.dto.StockDto;
import com.qaima.external.StockClient;
import com.qaima.repository.ExchangeRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockClient stockClient;
    private final ExchangeRepository exchangeRepository;

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

    /**
     * DB에 종목이 없으면 외부 API(KIS/Marketstack)에서 메타 조회 후
     * ApiResponse 성공일 때만 Stock 엔티티를 생성·저장한 뒤 반환
     */
    private Mono<Stock> loadOrCreateStockMono(String rawStockCode) {
        String normalizedCode = extractStockCode(rawStockCode); // "005930.XKRX" → "005930"

        return Mono.fromCallable(() ->
                        stockRepository.findByStockCodeWithExchange(normalizedCode)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        return Mono.just(optional.get());
                    }

                    log.info("[loadOrCreateStock] DB 미존재 → 외부 메타 조회 시작: {}", normalizedCode);

                    return stockClient.fetchTickerMeta(normalizedCode) // Mono<ApiResponse<StockMeta>>
                            .flatMap(apiResponse -> {
                                if (apiResponse == null || !apiResponse.isSuccess()) {
                                    log.warn("[loadOrCreateStock] 외부 메타 조회 실패 → DB 저장 안 함: {}",
                                            apiResponse != null ? apiResponse.getErrors() : "null");
                                    return Mono.error(new IllegalStateException(
                                            "티커 메타 조회 실패: " + normalizedCode
                                    ));
                                }

                                StockMeta meta = apiResponse.getData();
                                if (meta == null) {
                                    return Mono.error(new IllegalStateException(
                                            "메타 데이터 없음: " + normalizedCode
                                    ));
                                }

                                return createAndSaveStockFromMeta(normalizedCode, meta);
                            });
                })
                .onErrorResume(ex -> {
                    String msg = ex.getMessage();
                    // 유니크 인덱스 에러일 경우 한 번 더 조회해서 리턴
                    // 동시 요청이 생길 경우 안전 방지
                    if (msg != null && msg.contains("UK_EXCHANGE_STOCK_CODE_INDEX")) {
                        log.warn("[loadOrCreateStock] 유니크 충돌 감지 → 재조회 시도: {}", normalizedCode);
                        return Mono.fromCallable(() ->
                                        stockRepository.findByStockCodeWithExchange(normalizedCode)
                                )
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(opt -> opt
                                        .map(Mono::just)
                                        .orElseGet(() -> Mono.error(
                                                new IllegalStateException("유니크 충돌 이후에도 종목을 찾을 수 없음: " + normalizedCode)
                                        )));
                    }
                    return Mono.error(ex);
                });
    }

    private Mono<Stock> createAndSaveStockFromMeta(
            String normalizedCode,
            StockMeta meta
    ) {
        String exchangeCode = normalizeExchangeCode(meta.getExchangeCode());
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return Mono.error(new IllegalStateException("메타 응답에 exchangeCode가 없음"));
        }

        return Mono.fromCallable(() ->
                        exchangeRepository.findByCode(exchangeCode)
                                .orElseThrow(() -> new IllegalStateException(
                                        "DB Exchange 미존재: " + exchangeCode
                                ))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exchange -> {
                    return Mono.fromCallable(() -> stockRepository.findByExchangeAndStockCode(exchange, normalizedCode))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(existing -> existing
                                    .map(Mono::just)
                                    .orElseGet(() -> {
                                        Stock stock = new Stock();
                                        stock.setStockCode(normalizedCode);
                                        stock.setCompanyName(meta.getCompanyName());
                                        stock.setAssetType("EQUITY");
                                        stock.setCurrency(meta.getCountryCode() != null ? meta.getCountryCode() : "USD");
                                        stock.setExchange(exchange);
                                        stock.setIsin(null);

                                        return Mono.fromCallable(() -> stockRepository.save(stock))
                                                .subscribeOn(Schedulers.boundedElastic());
                                    }));
                });
    }

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
