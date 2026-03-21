package com.qaima.service.candle;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.PriceOhlcvId;
import com.qaima.domain.Stock;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
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
import org.springframework.data.domain.PageRequest;
import java.util.Comparator;

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
                        priceOhlcvRepository.findRange(
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


    public Mono<CandleLoadResult> loadBefore(
            Stock stock,
            Freq freq,
            OffsetDateTime to,
            int limit
    ) {
        String stockCode = stock.getStockCode();

        // to, limit 분기 check
        if (to == null || limit <= 0) {
            return Mono.just(new CandleLoadResult(List.of(), CandleSource.EMPTY));
        }

        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findBefore(
                                stockCode,
                                freq,
                                to,
                                PageRequest.of(0, limit)
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dbCandles -> {

                    // DB 히트
                    if (dbCandles != null && !dbCandles.isEmpty()) {
                        System.out.println("Candle DB히트");
                        List<PriceOhlcv> asc = dbCandles.stream()
                                .sorted(Comparator.comparing(p -> p.getId().getTs()))
                                .toList();

                        return Mono.just(new CandleLoadResult(asc, CandleSource.DB));
                    }

                    // DB 미스 → 외부 fetch(lookback range) → 저장 → 다시 DB slice
                    OffsetDateTime from = computeLookbackFrom(freq, to, limit);

                    return stockClient.fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, result.getCandles())
                                            .then(
                                                    Mono.fromCallable(() ->
                                                                    priceOhlcvRepository.findBefore(
                                                                            stockCode,
                                                                            freq,
                                                                            to,
                                                                            PageRequest.of(0, limit)
                                                                    )
                                                            )
                                                            .subscribeOn(Schedulers.boundedElastic())
                                                            .map(list -> {
                                                                if (list == null || list.isEmpty()) {
                                                                    return new CandleLoadResult(List.of(), result.getSource());
                                                                }

                                                                List<PriceOhlcv> asc = list.stream()
                                                                        .sorted(Comparator.comparing(p -> p.getId().getTs()))
                                                                        .toList();

                                                                return new CandleLoadResult(asc, result.getSource());
                                                            })
                                            )
                            )
                            .onErrorResume(err ->
                                    Mono.just(new CandleLoadResult(List.of(), CandleSource.EMPTY))
                            );
                });
    }

    /**
     * 외부 API(KIS/Marketstack)는 from/to 둘 다 필요한 경우가 많아서,
     * before 로딩은 to 기준으로 넉넉한 lookback 구간을 잡아 range fetch 후 DB에서 slice 한다.
     */
    private OffsetDateTime computeLookbackFrom(Freq freq, OffsetDateTime to, int limit) {
        // limit=5라도 외부 API가 "limit"을 직접 지원하지 않으면
        // 충분한 윈도우를 잡아야 5개를 안정적으로 확보할 수 있다.
        int n = Math.max(limit * 200, 200);

        return switch (freq) {
            case ONE_MIN      -> to.minusMinutes(n);          // 1분봉
            case FIVE_MIN     -> to.minusMinutes(5L * n);     // 5분봉
            case FIFTEEN_MIN  -> to.minusMinutes(15L * n);    // 15분봉
            case ONE_H        -> to.minusHours(n);             // 1시간봉
            case ONE_D        -> to.minusDays(n);              // 일봉
            case ONE_W        -> to.minusWeeks(n);             // 주봉
            case ONE_M        -> to.minusMonths(n);            // 월봉
        };
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
