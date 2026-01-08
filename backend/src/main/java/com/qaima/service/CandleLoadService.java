package com.qaima.service;

import com.qaima.domain.*;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.PriceOhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleLoadService {

    private final PriceOhlcvRepository priceOhlcvRepository;
    private final KrStockClient krStockClient;
    private final GlobalStockClient globalStockClient;

    private static final Duration KIS_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MARKETSTACK_TIMEOUT = Duration.ofSeconds(4);

    public Mono<CandleLoadResult> load(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String stockCode = stock.getStockCode();
        String marketDivCode = toKisMarketDivCode(stock.getExchange());

        // DB 조회
        return Mono.fromCallable(() ->
                        priceOhlcvRepository
                                .findByStockCodeAndFreqAndTsBetween(
                                        stockCode, freq, from, to
                                )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dbCandles -> {
                    if (!dbCandles.isEmpty()) {
                        return Mono.just(
                                new CandleLoadResult(dbCandles, CandleSource.DB)
                        );
                    }

                    // KIS 호출
                    return krStockClient
                            .fetchCandles(stockCode, marketDivCode, freq, from, to)
                            .timeout(KIS_TIMEOUT)
                            .map(dto -> save(stock, dto))
                            .map(list ->
                                    new CandleLoadResult(list, CandleSource.KIS)
                            )
                            // KIS 실패 → Marketstack
                            .onErrorResume(kisErr -> {
                                log.warn("[CANDLE] KIS failed → fallback to Marketstack: {}",
                                        kisErr.getMessage());

                                return globalStockClient
                                        .fetchCandles(stockCode, freq, from, to)
                                        .timeout(MARKETSTACK_TIMEOUT)
                                        .map(dto -> save(stock, dto))
                                        .map(list ->
                                                new CandleLoadResult(list, CandleSource.MARKETSTACK)
                                        )
                                        // 전부 실패 → EMPTY
                                        .onErrorResume(globalErr -> {
                                            log.error("[CANDLE] Marketstack failed: {}",
                                                    globalErr.getMessage(), globalErr);
                                            return Mono.just(
                                                    new CandleLoadResult(List.of(), CandleSource.EMPTY)
                                            );
                                        });
                            });
                });
    }

    /* ========================= */

    private List<PriceOhlcv> save(Stock stock, List<PriceOhlcvDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return List.of();
        }

        return Mono.fromCallable(() -> {
                    List<PriceOhlcv> entities =
                            dtoList.stream()
                                    .map(dto -> toEntity(stock, dto))
                                    .collect(Collectors.toList());
                    return priceOhlcvRepository.saveAll(entities);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .block(); // 내부에서만 block (외부로 전파 X)
    }

    private PriceOhlcv toEntity(Stock stock, PriceOhlcvDto dto) {
        PriceOhlcvId id = new PriceOhlcvId(
                stock.getStockId(),
                dto.getTs(),
                dto.getFreq()
        );

        PriceOhlcv e = new PriceOhlcv();
        e.setId(id);
        e.setStock(stock);
        e.setOpen(dto.getOpen());
        e.setHigh(dto.getHigh());
        e.setLow(dto.getLow());
        e.setClose(dto.getClose());
        e.setVolume(dto.getVolume());
        return e;
    }

    private String toKisMarketDivCode(Exchange exchange) {
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }
}
