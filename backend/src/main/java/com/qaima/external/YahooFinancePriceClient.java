package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
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

    public record YahooAdjustedPriceSeries(List<YahooAdjustedPricePoint> points) {
    }

    public record YahooAdjustedPricePoint(OffsetDateTime ts, BigDecimal adjustedClose) {
    }
}
