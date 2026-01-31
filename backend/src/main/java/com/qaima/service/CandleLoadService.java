package com.qaima.service;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.PriceOhlcvId;
import com.qaima.domain.Stock;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.external.StockClient;
import com.qaima.repository.PriceOhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleLoadService {

    private final PriceOhlcvRepository priceOhlcvRepository;
    private final StockClient stockClient;

    public Mono<CandleLoadResult> load(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String stockCode = stock.getStockCode();

        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findByStockCodeAndFreqAndTsBetween(
                                stockCode, freq, from, to
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dbCandles -> {
                    if (!dbCandles.isEmpty()) {
                        return Mono.just(new CandleLoadResult(dbCandles, CandleSource.DB));
                    }

                    return stockClient.fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, result.getCandles())
                                            .map(list -> {
                                                CandleSource source = list.isEmpty()
                                                        ? CandleSource.EMPTY
                                                        : result.getSource();
                                                return new CandleLoadResult(list, source);
                                            })
                            )
                            .onErrorResume(ErrorException.class, e -> {
                                // decode는 숨기지 말고 터뜨림
                                if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) {
                                    return Mono.error(e);
                                }

                                // http/biz/market_closed는 EMPTY로 폴백(컨트롤러에서 warning 판단)
                                if (e.getErrorCode() == ErrorCode.KIS_HTTP_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_BIZ_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_MARKET_CLOSED) {
                                    log.warn("[CANDLE] fallback to EMPTY. code={}, msg={}",
                                            e.getErrorCode().code(), e.getMessage());
                                    return Mono.just(new CandleLoadResult(List.of(), CandleSource.EMPTY));
                                }

                                return Mono.error(e);
                            })
                            .onErrorResume(err -> {
                                // 나머지 예상치 못한 예외는 일단 EMPTY (운영 정책에 따라 바꿔도 됨)
                                log.error("[CANDLE] unexpected error: {}", err.getMessage(), err);
                                return Mono.just(new CandleLoadResult(List.of(), CandleSource.EMPTY));
                            });
                });
    }

    private Mono<List<PriceOhlcv>> save(Stock stock, Freq freq, List<PriceOhlcvDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(() -> {
                    List<PriceOhlcv> entities = dtoList.stream()
                            .map(dto -> toEntity(stock, freq, dto))
                            .collect(Collectors.toList());

                    return priceOhlcvRepository.saveAll(entities);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private PriceOhlcv toEntity(Stock stock, Freq freq, PriceOhlcvDto dto) {
        Freq resolvedFreq = (dto != null && dto.getFreq() != null) ? dto.getFreq() : freq;
        if (resolvedFreq == null) {
            throw new IllegalStateException("PriceOhlcv freq is null for stock=" + stock.getStockCode());
        }
        if (dto == null) {
            throw new IllegalArgumentException("PriceOhlcvDto is null for stock=" + stock.getStockCode());
        }
        if (dto.getTs() == null) {
            throw new IllegalStateException("PriceOhlcv ts is null for stock=" + stock.getStockCode());
        }

        PriceOhlcvId id = new PriceOhlcvId(
                stock.getStockId(),
                dto.getTs(),
                resolvedFreq
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
}
