package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.qaima.domain.Freq;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YahooFinancePriceClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final WebClient webClient;

    public YahooFinancePriceClient(@Qualifier("defaultWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<YahooAdjustedPriceSeries> fetchAdjustedClose(String symbol, OffsetDateTime from, OffsetDateTime to) {
        if (symbol == null || symbol.isBlank()) {
            return Mono.error(new IllegalArgumentException("Yahoo symbol is required"));
        }
        long period1 = Math.max(0, from.toEpochSecond());
        long period2 = Math.max(period1 + 86_400, to.toEpochSecond());

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("period1", period1)
                        .queryParam("period2", period2)
                        .queryParam("interval", "1d")
                        .queryParam("events", "history")
                        .queryParam("includeAdjustedClose", "true")
                        .build(symbol))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TIMEOUT)
                .map(this::toAdjustedSeries)
                .doOnNext(series -> log.info(
                        "[YAHOO][FEATURE3] adjusted close fetched. symbol={}, rows={}",
                        symbol,
                        series.points().size()
                ));
    }

    public Mono<List<PriceOhlcvDto>> fetchCandles(String symbol, Freq freq, OffsetDateTime from, OffsetDateTime to) {
        if (symbol == null || symbol.isBlank()) {
            return Mono.error(new IllegalArgumentException("Yahoo symbol is required"));
        }
        if (freq != Freq.ONE_D) {
            return Mono.just(List.of());
        }

        long period1 = Math.max(0, from.toEpochSecond());
        long period2 = Math.max(period1 + 86_400, to.toEpochSecond());

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("query1.finance.yahoo.com")
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("period1", period1)
                        .queryParam("period2", period2)
                        .queryParam("interval", "1d")
                        .queryParam("events", "history")
                        .queryParam("includeAdjustedClose", "false")
                        .build(symbol))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(TIMEOUT)
                .map(root -> toOhlcvDtos(root, freq))
                .doOnNext(rows -> log.info(
                        "[YAHOO][CANDLE] OHLCV fetched. symbol={}, freq={}, rows={}",
                        symbol,
                        freq,
                        rows.size()
                ));
    }

    private YahooAdjustedPriceSeries toAdjustedSeries(JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return new YahooAdjustedPriceSeries(List.of());
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode adjclose = first.path("indicators").path("adjclose");
        if (!timestamps.isArray() || !adjclose.isArray() || adjclose.isEmpty()) {
            return new YahooAdjustedPriceSeries(List.of());
        }

        JsonNode adjustedValues = adjclose.get(0).path("adjclose");
        if (!adjustedValues.isArray()) {
            return new YahooAdjustedPriceSeries(List.of());
        }

        int size = Math.min(timestamps.size(), adjustedValues.size());
        List<YahooAdjustedPricePoint> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            JsonNode tsNode = timestamps.get(i);
            JsonNode valueNode = adjustedValues.get(i);
            if (!tsNode.canConvertToLong() || !valueNode.isNumber()) {
                continue;
            }

            BigDecimal close = BigDecimal.valueOf(valueNode.asDouble());
            if (close.signum() <= 0) {
                continue;
            }

            points.add(new YahooAdjustedPricePoint(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(tsNode.asLong()), ZoneOffset.UTC),
                    close
            ));
        }
        return new YahooAdjustedPriceSeries(points);
    }

    private List<PriceOhlcvDto> toOhlcvDtos(JsonNode root, Freq freq) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }

        JsonNode firstQuote = quote.get(0);
        JsonNode open = firstQuote.path("open");
        JsonNode high = firstQuote.path("high");
        JsonNode low = firstQuote.path("low");
        JsonNode close = firstQuote.path("close");
        JsonNode volume = firstQuote.path("volume");
        if (!open.isArray() || !high.isArray() || !low.isArray() || !close.isArray()) {
            return List.of();
        }

        int size = minSize(timestamps, open, high, low, close, volume);
        List<PriceOhlcvDto> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            JsonNode tsNode = timestamps.get(i);
            JsonNode openNode = open.get(i);
            JsonNode highNode = high.get(i);
            JsonNode lowNode = low.get(i);
            JsonNode closeNode = close.get(i);
            JsonNode volumeNode = volume.isArray() ? volume.get(i) : null;

            if (!tsNode.canConvertToLong()
                    || !openNode.isNumber()
                    || !highNode.isNumber()
                    || !lowNode.isNumber()
                    || !closeNode.isNumber()) {
                continue;
            }

            rows.add(PriceOhlcvDto.builder()
                    .ts(OffsetDateTime.ofInstant(Instant.ofEpochSecond(tsNode.asLong()), ZoneOffset.UTC))
                    .freq(freq)
                    .open(BigDecimal.valueOf(openNode.asDouble()))
                    .high(BigDecimal.valueOf(highNode.asDouble()))
                    .low(BigDecimal.valueOf(lowNode.asDouble()))
                    .close(BigDecimal.valueOf(closeNode.asDouble()))
                    .volume(volumeNode != null && volumeNode.canConvertToLong()
                            ? BigDecimal.valueOf(volumeNode.asLong())
                            : BigDecimal.ZERO)
                    .build());
        }
        return rows;
    }

    private int minSize(JsonNode... nodes) {
        int min = Integer.MAX_VALUE;
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                min = Math.min(min, node.size());
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public record YahooAdjustedPriceSeries(List<YahooAdjustedPricePoint> points) {
    }

    public record YahooAdjustedPricePoint(OffsetDateTime ts, BigDecimal adjustedClose) {
    }
}
