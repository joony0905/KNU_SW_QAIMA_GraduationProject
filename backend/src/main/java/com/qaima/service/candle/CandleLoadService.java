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
import com.qaima.service.tradingcalendar.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleLoadService {

    private static final String KRX_MARKET = "KRX";

    private final PriceOhlcvRepository priceOhlcvRepository;
    private final StockClient stockClient;
    private final TradingCalendarService tradingCalendarService;

    public Mono<CandleLoadResult> load(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String stockCode = stock.getStockCode();

        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findRange(
                                stockCode, freq, dbRangeFromForRead(stock, freq, from), to
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dbCandles -> {
                    if (!shouldFetchCandlesFromExternal(stock, dbCandles)) {
                        return Mono.just(new CandleLoadResult(dbCandles, CandleSource.DB));
                    }

                    log.info("[CANDLE] latest candle miss -> external fetch. stockCode={}, freq={}, existingSize={}, requestedToDate={}, latestDbDate={}",
                            stockCode,
                            freq,
                            dbCandles.size(),
                            latestTradingDay(stock),
                            latestLocalDate(stock, dbCandles));

                    return stockClient.fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, dbCandles, result.getCandles())
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

    /**
     * Feature3 전용 가격 시계열 로딩 경로.
     *
     * 차트 화면은 DB에 일부 row만 있어도 즉시 반환할 수 있지만,
     * 포트폴리오 리스크 분석은 결측률/관측치 수가 계산 품질에 직접 영향을 준다.
     * 따라서 price_ohlcv 조회 결과가 요청 lookback을 채우지 못하면 Spring 책임으로
     * 외부 가격 API fallback을 시도하고, FastAPI는 계산만 담당하게 한다.
     */
    public Mono<CandleLoadResult> loadForFeature3(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to,
            int requiredRows
    ) {
        String stockCode = stock.getStockCode();
        int minRequiredRows = Math.max(requiredRows, 1);

        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findRange(stockCode, freq, dbRangeFromForRead(stock, freq, from), to)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dbCandles -> {
                    boolean insufficientRows = dbCandles == null || dbCandles.size() < minRequiredRows;
                    boolean shouldFetch = shouldFetchCandlesFromExternal(stock, dbCandles) || insufficientRows;

                    if (!shouldFetch) {
                        return Mono.just(new CandleLoadResult(dbCandles, CandleSource.DB));
                    }

                    log.info("[CANDLE][FEATURE3] price_ohlcv insufficient/stale -> external fetch. stockCode={}, freq={}, existingSize={}, requiredRows={}, requestedFrom={}, requestedTo={}, latestDbDate={}",
                            stockCode,
                            freq,
                            dbCandles == null ? 0 : dbCandles.size(),
                            minRequiredRows,
                            from,
                            to,
                            latestLocalDate(stock, dbCandles));

                    return stockClient.fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, dbCandles == null ? List.of() : dbCandles, result.getCandles())
                                            .map(list -> {
                                                CandleSource source = list.isEmpty()
                                                        ? CandleSource.EMPTY
                                                        : result.getSource();
                                                return new CandleLoadResult(list, source);
                                            })
                            )
                            .onErrorResume(ErrorException.class, e -> {
                                if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) {
                                    return Mono.error(e);
                                }

                                if (e.getErrorCode() == ErrorCode.KIS_HTTP_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_BIZ_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_MARKET_CLOSED) {
                                    log.warn("[CANDLE][FEATURE3] external fallback failed. code={}, msg={}",
                                            e.getErrorCode().code(), e.getMessage());
                                    return Mono.just(new CandleLoadResult(
                                            dbCandles == null ? List.of() : dbCandles,
                                            dbCandles == null || dbCandles.isEmpty() ? CandleSource.EMPTY : CandleSource.DB
                                    ));
                                }

                                return Mono.error(e);
                            })
                            .onErrorResume(err -> {
                                log.error("[CANDLE][FEATURE3] unexpected external fetch error: {}", err.getMessage(), err);
                                return Mono.just(new CandleLoadResult(
                                        dbCandles == null ? List.of() : dbCandles,
                                        dbCandles == null || dbCandles.isEmpty() ? CandleSource.EMPTY : CandleSource.DB
                                ));
                            });
                });
    }

    private Mono<List<PriceOhlcv>> save(
            Stock stock,
            Freq freq,
            List<PriceOhlcv> existing,
            List<PriceOhlcvDto> dtoList
    ) {
        if (dtoList == null || dtoList.isEmpty()) {
            return Mono.just(existing == null ? List.of() : existing);
        }

        return Mono.fromCallable(() -> {
                    List<PriceOhlcv> entities = dtoList.stream()
                            .map(dto -> toEntity(stock, freq, dto))
                            .collect(Collectors.toList());

                    ZoneId tradingZone = CandleTimePolicy.tradingZone(stock);
                    List<PriceOhlcv> missingOnly = filterMissingCandles(existing, entities, tradingZone);
                    if (missingOnly.isEmpty()) {
                        log.info("[CANDLE] external returned only existing rows. stockCode={}, freq={}, fetchedSize={}",
                                stock.getStockCode(), freq, entities.size());
                        return mergeCandles(existing, entities, tradingZone);
                    }

                    List<PriceOhlcv> saved = priceOhlcvRepository.saveAll(missingOnly);
                    log.info("[CANDLE] persisted missing rows only. stockCode={}, freq={}, existingSize={}, fetchedSize={}, insertedSize={}",
                            stock.getStockCode(), freq, existing == null ? 0 : existing.size(), entities.size(), saved.size());
                    return mergeCandles(existing, saved, tradingZone);
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
                        log.debug("[CandleLoad] DB hit. stockCode={}, freq={}, rows={}", stockCode, freq, dbCandles.size());
                        List<PriceOhlcv> asc = dbCandles.stream()
                                .sorted(Comparator.comparing(p -> p.getId().getTs()))
                                .toList();

                        return Mono.just(new CandleLoadResult(asc, CandleSource.DB));
                    }

                    // DB 미스 → 외부 fetch(lookback range) → 저장 → 다시 DB slice
                    OffsetDateTime from = computeLookbackFrom(freq, to, limit);

                    return stockClient.fetchCandles(stock, freq, from, to)
                            .flatMap(result ->
                                    save(stock, freq, List.of(), result.getCandles())
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

    private boolean shouldFetchCandlesFromExternal(Stock stock, List<PriceOhlcv> existing) {
        if (existing == null || existing.isEmpty()) {
            return true;
        }

        LocalDate latestRequestedDate = latestTradingDay(stock);
        LocalDate latestDbDate = latestLocalDate(stock, existing);
        return latestDbDate == null || latestDbDate.isBefore(latestRequestedDate);
    }

    private LocalDate latestTradingDay(Stock stock) {
        ZoneId tradingZone = CandleTimePolicy.tradingZone(stock);
        return tradingCalendarService.latestTradingDay(ZonedDateTime.now(tradingZone), KRX_MARKET);
    }

    private LocalDate latestLocalDate(Stock stock, List<PriceOhlcv> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        ZoneId tradingZone = CandleTimePolicy.tradingZone(stock);
        return candles.stream()
                .map(candle -> CandleTimePolicy.tradingDate(candle, tradingZone))
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private List<PriceOhlcv> filterMissingCandles(
            List<PriceOhlcv> existing,
            List<PriceOhlcv> fetched,
            ZoneId tradingZone
    ) {
        Set<String> existingKeys = (existing == null ? List.<PriceOhlcv>of() : existing).stream()
                .map(entity -> candleLogicalKey(entity, tradingZone))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        return (fetched == null ? List.<PriceOhlcv>of() : fetched).stream()
                .filter(entity -> {
                    String key = candleLogicalKey(entity, tradingZone);
                    return key != null && !existingKeys.contains(key);
                })
                .toList();
    }

    private List<PriceOhlcv> mergeCandles(List<PriceOhlcv> existing, List<PriceOhlcv> fetched, ZoneId tradingZone) {
        java.util.LinkedHashMap<String, PriceOhlcv> merged = new java.util.LinkedHashMap<>();

        if (existing != null) {
            existing.stream()
                    .filter(entity -> entity != null && entity.getId() != null)
                    .forEach(entity -> merged.put(candleLogicalKey(entity, tradingZone), entity));
        }

        if (fetched != null) {
            fetched.stream()
                    .filter(entity -> entity != null && entity.getId() != null)
                    .forEach(entity -> merged.put(candleLogicalKey(entity, tradingZone), entity));
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(entity -> entity.getId().getTs()))
                .toList();
    }

    private String candleLogicalKey(PriceOhlcv entity, ZoneId tradingZone) {
        if (entity == null || entity.getId() == null) return null;
        PriceOhlcvId id = entity.getId();
        if (id.getStockId() == null || id.getFreq() == null || id.getTs() == null) return null;

        if (id.getFreq() == Freq.ONE_D) {
            LocalDate tradingDay = CandleTimePolicy.tradingDate(id.getTs(), tradingZone);
            return id.getStockId() + "|" + id.getFreq() + "|" + tradingDay;
        }

        return id.getStockId() + "|" + id.getFreq() + "|" + id.getTs().toInstant();
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

    private OffsetDateTime dbRangeFromForRead(Stock stock, Freq freq, OffsetDateTime from) {
        if (from == null || freq != Freq.ONE_D) {
            return from;
        }
        ZoneId tradingZone = CandleTimePolicy.tradingZone(stock);
        if (!CandleTimePolicy.DEFAULT_TRADING_ZONE.equals(tradingZone)) {
            return from;
        }
        return from.minusHours(9);
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
                normalizeTsForFreq(stock, dto.getTs(), resolvedFreq),
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

    private OffsetDateTime normalizeTsForFreq(Stock stock, OffsetDateTime ts, Freq freq) {
        return CandleTimePolicy.canonicalTs(ts, freq, CandleTimePolicy.tradingZone(stock));
    }
}
