package com.qaima.service;

import com.qaima.service.CandleLoadResult;
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

        // DB 조회
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

                    return stockClient
                            .fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, result.getCandles())
                                            .map(list -> {
                                                CandleSource source = list.isEmpty()
                                                        ? CandleSource.EMPTY
                                                        : result.getSource();
                                                return new CandleLoadResult(list, source);
                                            })
                            )
                            .onErrorResume(err -> {
                                log.error("[CANDLE] KIS/Marketstack 모두 실패: {}", err.getMessage(), err);
                                return Mono.just(new CandleLoadResult(List.of(), CandleSource.EMPTY));
                            });
                });
    }

    /* ========================= */

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
        // 외부 응답 dto에 freq가 없을 수 있으니, 요청 freq를 fallback으로 사용
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

        //e.setFreq(resolvedFreq);

        e.setOpen(dto.getOpen());
        e.setHigh(dto.getHigh());
        e.setLow(dto.getLow());
        e.setClose(dto.getClose());
        e.setVolume(dto.getVolume());
        return e;
    }
}
