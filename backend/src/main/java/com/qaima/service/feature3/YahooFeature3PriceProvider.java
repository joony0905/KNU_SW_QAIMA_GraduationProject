package com.qaima.service.feature3;

import com.qaima.domain.Stock;
import com.qaima.dto.feature3.Feature3PriceSeriesResponseDto;
import com.qaima.external.YahooFinancePriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFeature3PriceProvider {

    private static final double WARN_MISSING_RATE = 0.05;
    private static final double FALLBACK_MISSING_RATE = 0.20;

    private final YahooFinancePriceClient yahooFinancePriceClient;

    public Mono<Result> fetchAdjustedClose(
            Stock stock,
            OffsetDateTime from,
            OffsetDateTime to,
            int expectedTradingDayCount
    ) {
        SymbolCandidates candidates = resolveSymbols(stock);
        if (candidates.symbols().isEmpty()) {
            return Mono.just(Result.fallback(List.of(warning(
                    "YAHOO_SYMBOL_UNRESOLVED",
                    "Yahoo symbol could not be resolved for Feature3 adjusted close.",
                    "수정주가 심볼을 확인하지 못해 원시 종가로 계산합니다.",
                    "WARN",
                    stockCode(stock)
            ))));
        }

        List<Feature3PriceSeriesResponseDto.Warning> baseWarnings = new ArrayList<>(candidates.warnings());
        return Flux.fromIterable(candidates.symbols())
                .concatMap(symbol -> yahooFinancePriceClient.fetchAdjustedClose(symbol, from, to)
                        .map(series -> evaluate(symbol, series, expectedTradingDayCount, stockCode(stock), baseWarnings))
                        .onErrorResume(err -> {
                            log.warn("[YAHOO][FEATURE3] adjusted close fetch failed. stockCode={}, symbol={}, error={}",
                                    stockCode(stock), symbol, err.toString());
                            return Mono.just(Result.fallback(withWarning(baseWarnings, warning(
                                    "YAHOO_ADJ_PROVIDER_UNAVAILABLE",
                                    "Yahoo adjusted close provider request failed.",
                                    "수정주가 제공자 조회에 실패해 원시 종가로 계산합니다.",
                                    "WARN",
                                    stockCode(stock)
                            ))));
                        }))
                .collectList()
                .map(results -> selectResult(results, baseWarnings, stockCode(stock)));
    }

    private Result evaluate(
            String symbol,
            YahooFinancePriceClient.YahooAdjustedPriceSeries series,
            int expectedTradingDayCount,
            String stockCode,
            List<Feature3PriceSeriesResponseDto.Warning> baseWarnings
    ) {
        List<YahooPoint> points = series.points().stream()
                .filter(point -> point != null && point.ts() != null && point.adjustedClose() != null)
                .filter(point -> point.adjustedClose().signum() > 0)
                .sorted(Comparator.comparing(YahooFinancePriceClient.YahooAdjustedPricePoint::ts))
                .map(point -> new YahooPoint(point.ts(), point.adjustedClose()))
                .toList();

        if (points.size() > expectedTradingDayCount) {
            points = points.subList(points.size() - expectedTradingDayCount, points.size());
        }

        int available = points.size();
        if (available == 0) {
            return Result.retryable(withWarning(baseWarnings, warning(
                    "YAHOO_ADJ_EMPTY_RESPONSE",
                    "Yahoo adjusted close response did not include usable adjusted close rows for " + symbol + ".",
                    "수정주가 데이터가 비어 있어 원시 종가로 계산합니다.",
                    "WARN",
                    stockCode
            )));
        }

        double missingRate = Math.max(0.0, 1.0 - ((double) available / Math.max(expectedTradingDayCount, 1)));
        List<Feature3PriceSeriesResponseDto.Warning> warnings = new ArrayList<>(baseWarnings);
        if (missingRate > FALLBACK_MISSING_RATE) {
            warnings.add(warning(
                    "YAHOO_ADJ_HIGH_MISSING_RATE",
                    "Yahoo adjusted close missing rate is above 20%, so Feature3 falls back to raw close.",
                    "수정주가 결측률이 높아 원시 종가로 계산합니다.",
                    "WARN",
                    stockCode
            ));
            return Result.fallback(warnings);
        }
        if (missingRate > WARN_MISSING_RATE) {
            warnings.add(warning(
                    "YAHOO_ADJ_HIGH_MISSING_RATE",
                    "Yahoo adjusted close missing rate is above 5%, but within the acceptable MVP threshold.",
                    "수정주가 일부 결측이 있어 경고와 함께 계산합니다.",
                    "WARN",
                    stockCode
            ));
        }

        return Result.yahoo(symbol, points, missingRate, warnings);
    }

    private Result selectResult(
            List<Result> results,
            List<Feature3PriceSeriesResponseDto.Warning> baseWarnings,
            String stockCode
    ) {
        for (Result result : results) {
            if (result.useYahoo()) {
                return result;
            }
        }
        for (int i = results.size() - 1; i >= 0; i--) {
            Result result = results.get(i);
            if (!result.retryable()) {
                return result;
            }
        }
        List<Feature3PriceSeriesResponseDto.Warning> warnings = results.isEmpty()
                ? baseWarnings
                : results.get(results.size() - 1).warnings();
        return Result.fallback(withWarning(warnings, warning(
                "YAHOO_ADJ_EMPTY_RESPONSE",
                "Yahoo adjusted close response did not include usable adjusted close rows.",
                "수정주가 데이터가 비어 있어 원시 종가로 계산합니다.",
                "WARN",
                stockCode
        )));
    }

    private SymbolCandidates resolveSymbols(Stock stock) {
        String stockCode = stockCode(stock);
        if (stockCode == null || stockCode.isBlank()) {
            return new SymbolCandidates(List.of(), List.of());
        }
        String cleanCode = stockCode.trim();
        if (cleanCode.contains(".")) {
            return new SymbolCandidates(List.of(cleanCode), List.of());
        }
        if (!cleanCode.matches("\\d{6}")) {
            return new SymbolCandidates(List.of(), List.of());
        }

        String market = marketCode(stock);
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "KOSPI", "XKRX" -> new SymbolCandidates(List.of(cleanCode + ".KS"), List.of());
            case "KOSDAQ", "XKOS" -> new SymbolCandidates(List.of(cleanCode + ".KQ"), List.of());
            case "KRX", "" -> new SymbolCandidates(
                    List.of(cleanCode + ".KS", cleanCode + ".KQ"),
                    List.of(warning(
                            "YAHOO_SYMBOL_MARKET_FALLBACK",
                            "Stock market was not specific enough for Yahoo; trying .KS before .KQ.",
                            "시장 구분이 명확하지 않아 Yahoo 심볼 후보를 순차 조회합니다.",
                            "WARN",
                            cleanCode
                    ))
            );
            default -> new SymbolCandidates(List.of(), List.of());
        };
    }

    private String stockCode(Stock stock) {
        return stock == null ? null : stock.getStockCode();
    }

    private String marketCode(Stock stock) {
        try {
            return stock != null && stock.getExchange() != null ? stock.getExchange().getCode() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Feature3PriceSeriesResponseDto.Warning> withWarning(
            List<Feature3PriceSeriesResponseDto.Warning> warnings,
            Feature3PriceSeriesResponseDto.Warning warning
    ) {
        List<Feature3PriceSeriesResponseDto.Warning> copy = new ArrayList<>(warnings);
        copy.add(warning);
        return copy;
    }

    private Feature3PriceSeriesResponseDto.Warning warning(
            String code,
            String message,
            String userMessage,
            String severity,
            String target
    ) {
        return new Feature3PriceSeriesResponseDto.Warning(code, message, userMessage, severity, target);
    }

    private record SymbolCandidates(
            List<String> symbols,
            List<Feature3PriceSeriesResponseDto.Warning> warnings
    ) {
    }

    public record YahooPoint(OffsetDateTime ts, BigDecimal close) {
    }

    public record Result(
            boolean useYahoo,
            boolean retryable,
            String symbol,
            List<YahooPoint> points,
            double missingRate,
            List<Feature3PriceSeriesResponseDto.Warning> warnings
    ) {
        static Result yahoo(
                String symbol,
                List<YahooPoint> points,
                double missingRate,
                List<Feature3PriceSeriesResponseDto.Warning> warnings
        ) {
            return new Result(true, false, symbol, points, missingRate, warnings);
        }

        static Result retryable(List<Feature3PriceSeriesResponseDto.Warning> warnings) {
            return new Result(false, true, null, List.of(), 1.0, warnings);
        }

        static Result fallback(List<Feature3PriceSeriesResponseDto.Warning> warnings) {
            return new Result(false, false, null, List.of(), 1.0, warnings);
        }
    }
}
