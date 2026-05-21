package com.qaima.service.feature3;

import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.dto.feature3.Feature3PriceSeriesResponseDto;
import com.qaima.service.candle.CandleLoadResult;
import com.qaima.service.candle.CandleLoadService;
import com.qaima.service.stock.StockService;
import com.qaima.service.tradingcalendar.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class Feature3PriceSeriesService {

    private static final String ADJUSTED_CLOSE = "ADJUSTED_CLOSE";
    private static final String CLOSE = "CLOSE";
    private static final String RAW_CLOSE = "RAW_CLOSE";
    private static final String YAHOO_ADJ_CLOSE = "YAHOO_ADJ_CLOSE";
    private static final String KRX_MARKET = "KRX";

    private final StockService stockService;
    private final CandleLoadService candleLoadService;
    private final TradingCalendarService tradingCalendarService;
    private final YahooFeature3PriceProvider yahooFeature3PriceProvider;

    public Mono<Feature3PriceSeriesResponseDto> getPriceSeries(
            String stockCode,
            String requestedPriceBasis,
            int lookbackTradingDays,
            int fetchCalendarDays
    ) {
        String requested = normalizePriceBasis(requestedPriceBasis);
        int lookback = Math.max(lookbackTradingDays, 1);
        int fetchDays = Math.max(fetchCalendarDays, lookback);
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = to.minusDays(fetchDays);

        return stockService.getOrCreateStockByCode(stockCode)
                .flatMap(stock -> {
                    String market = marketCode(stock);
                    LocalDate latestTradingDay = tradingCalendarService.latestTradingDay(ZonedDateTime.now(ZoneOffset.UTC), market);
                    int expectedTradingDays = Math.max(1, Math.min(lookback, countTradingDays(from.toLocalDate(), latestTradingDay, market)));
                    if (!ADJUSTED_CLOSE.equals(requested)) {
                        return loadRawClose(stock, requested, expectedTradingDays, from, to, List.of(), false);
                    }
                    return yahooFeature3PriceProvider.fetchAdjustedClose(stock, from, to, expectedTradingDays)
                            .flatMap(yahooResult -> {
                                if (yahooResult.useYahoo()) {
                                    return Mono.just(toYahooResponse(
                                            stock.getStockCode(),
                                            stock.getCompanyName(),
                                            requested,
                                            expectedTradingDays,
                                            yahooResult
                                    ));
                                }
                                return loadRawClose(
                                        stock,
                                        requested,
                                        expectedTradingDays,
                                        from,
                                        to,
                                        yahooResult.warnings(),
                                        true
                                );
                            });
                });
    }

    private Mono<Feature3PriceSeriesResponseDto> loadRawClose(
            com.qaima.domain.Stock stock,
            String requestedPriceBasis,
            int expectedTradingDayCount,
            OffsetDateTime from,
            OffsetDateTime to,
            List<Feature3PriceSeriesResponseDto.Warning> upstreamWarnings,
            boolean adjustedFallback
    ) {
        return candleLoadService.loadForFeature3(stock, Freq.ONE_D, from, to, expectedTradingDayCount)
                .map(result -> toRawResponse(
                        stock.getStockCode(),
                        stock.getCompanyName(),
                        requestedPriceBasis,
                        expectedTradingDayCount,
                        result,
                        upstreamWarnings,
                        adjustedFallback
                ));
    }

    private Feature3PriceSeriesResponseDto toRawResponse(
            String stockCode,
            String companyName,
            String requestedPriceBasis,
            int expectedTradingDayCount,
            CandleLoadResult result,
            List<Feature3PriceSeriesResponseDto.Warning> upstreamWarnings,
            boolean adjustedFallback
    ) {
        List<Feature3PriceSeriesResponseDto.PricePoint> points = result.getCandles().stream()
                .filter(Objects::nonNull)
                .filter(price -> price.getId() != null && price.getId().getTs() != null)
                .filter(price -> price.getClose() != null)
                .filter(price -> price.getClose().signum() > 0)
                .sorted(Comparator.comparing(price -> price.getId().getTs()))
                .map(price -> new Feature3PriceSeriesResponseDto.PricePoint(
                        price.getId().getTs().toString(),
                        price.getClose().doubleValue()
                ))
                .toList();
        if (points.size() > expectedTradingDayCount) {
            points = points.subList(points.size() - expectedTradingDayCount, points.size());
        }

        int available = points.size();
        double missingRate = Math.max(0.0, 1.0 - ((double) available / Math.max(expectedTradingDayCount, 1)));
        List<Feature3PriceSeriesResponseDto.Warning> warnings = buildRawWarnings(
                stockCode,
                requestedPriceBasis,
                result.getSource(),
                available,
                expectedTradingDayCount,
                upstreamWarnings,
                adjustedFallback
        );

        return new Feature3PriceSeriesResponseDto(
                stockCode,
                companyName,
                requestedPriceBasis,
                RAW_CLOSE,
                adjustedFallback ? "KIS" : rawFeature3Source(result.getSource()),
                cacheStatus(result.getSource()),
                expectedTradingDayCount,
                available,
                round(missingRate),
                adjustedFallback,
                points,
                warnings
        );
    }

    private Feature3PriceSeriesResponseDto toYahooResponse(
            String stockCode,
            String companyName,
            String requestedPriceBasis,
            int expectedTradingDayCount,
            YahooFeature3PriceProvider.Result result
    ) {
        List<Feature3PriceSeriesResponseDto.PricePoint> points = result.points().stream()
                .map(point -> new Feature3PriceSeriesResponseDto.PricePoint(
                        point.ts().toString(),
                        point.close().doubleValue()
                ))
                .toList();

        return new Feature3PriceSeriesResponseDto(
                stockCode,
                companyName,
                requestedPriceBasis,
                YAHOO_ADJ_CLOSE,
                "YAHOO",
                "MISS",
                expectedTradingDayCount,
                points.size(),
                round(result.missingRate()),
                false,
                points,
                result.warnings()
        );
    }

    private List<Feature3PriceSeriesResponseDto.Warning> buildRawWarnings(
            String stockCode,
            String requestedPriceBasis,
            CandleSource source,
            int available,
            int expected,
            List<Feature3PriceSeriesResponseDto.Warning> upstreamWarnings,
            boolean adjustedFallback
    ) {
        java.util.ArrayList<Feature3PriceSeriesResponseDto.Warning> warnings = new java.util.ArrayList<>();
        if (upstreamWarnings != null) {
            warnings.addAll(upstreamWarnings);
        }

        if (adjustedFallback) {
            warnings.add(new Feature3PriceSeriesResponseDto.Warning(
                    "ADJUSTED_CLOSE_FALLBACK_TO_RAW",
                    "Yahoo adjusted close was unavailable or below the quality threshold, so Feature3 uses raw close for this stock.",
                    "수정주가를 사용할 수 없어 원시 종가 기준으로 계산합니다.",
                    "WARN",
                    stockCode
            ));
            warnings.add(new Feature3PriceSeriesResponseDto.Warning(
                    "RAW_CLOSE_USED_FOR_RISK_ENGINE",
                    "Feature3 risk engine is using KIS raw close for this stock.",
                    "원시 종가 기준으로 리스크 계산을 수행합니다.",
                    "INFO",
                    stockCode
            ));
        }

        if (source == CandleSource.EMPTY || available == 0) {
            warnings.add(new Feature3PriceSeriesResponseDto.Warning(
                    "PRICE_SERIES_UNAVAILABLE",
                    "No usable price_ohlcv rows were found for Feature3 analysis.",
                    "가격 시계열을 가져오지 못해 해당 종목의 리스크 계산이 제한됩니다.",
                    "WARN",
                    stockCode
            ));
        } else if (available < expected) {
            warnings.add(new Feature3PriceSeriesResponseDto.Warning(
                    "INSUFFICIENT_PRICE_HISTORY",
                    "price_ohlcv rows are fewer than the requested Feature3 lookback trading days.",
                    "분석 기간보다 가격 데이터가 부족해 결측 경고가 표시될 수 있습니다.",
                    "WARN",
                    stockCode
            ));
        }

        return warnings;
    }

    private String normalizePriceBasis(String requestedPriceBasis) {
        if (CLOSE.equalsIgnoreCase(String.valueOf(requestedPriceBasis))) {
            return CLOSE;
        }
        return ADJUSTED_CLOSE;
    }

    private int countTradingDays(LocalDate fromInclusive, LocalDate toInclusive, String market) {
        if (fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)) {
            return 0;
        }
        int count = 0;
        LocalDate cursor = fromInclusive;
        while (!cursor.isAfter(toInclusive)) {
            if (tradingCalendarService.isTradingDay(cursor, market)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private String marketCode(com.qaima.domain.Stock stock) {
        try {
            if (stock != null && stock.getExchange() != null && stock.getExchange().getCode() != null) {
                return stock.getExchange().getCode();
            }
        } catch (Exception ignored) {
            return KRX_MARKET;
        }
        return KRX_MARKET;
    }

    private String rawFeature3Source(CandleSource source) {
        if (source == CandleSource.EMPTY || source == null) {
            return "UNAVAILABLE";
        }
        return source.name();
    }

    private String cacheStatus(CandleSource source) {
        if (source == CandleSource.DB) {
            return "HIT";
        }
        if (source == CandleSource.EMPTY || source == null) {
            return "MISS";
        }
        return "MISS";
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
