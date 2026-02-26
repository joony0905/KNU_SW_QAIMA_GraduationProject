package com.qaima.external;

import com.qaima.domain.Freq;
import com.qaima.dto.mkstack.MarketStackCandlesResponse;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GlobalStockClient {

    private final WebClient webClient;

    @Value("${marketstack.access-key}")
    private String accessKey;

    public GlobalStockClient(@Qualifier("marketstackWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Marketstack 전용: mkstackCode를 그대로 받는다.
     * 예) 005930.XKRX
     */
    public Mono<StockDto> fetchStockByMkstackCode(String mkstackCode) {
        return fetchTickerMetaByMkstackCode(mkstackCode)
                .map(ticker -> StockDto.builder()
                        .stockId(null)
                        .stockCode(mkstackCode)
                        .companyName(ticker.getName())
                        .price(ticker.getPrice())
                        .changeRate(ticker.getChangeRate())
                        .build()
                );
    }

    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMetaByMkstackCode(String mkstackCode) {
        final String symbol = mkstackCode;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers")
                        .pathSegment(symbol) // 인코딩 안정
                        .queryParam("access_key", accessKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.class)
                .flatMap(resp -> {
                    if (resp == null || resp.getData() == null || resp.getData().isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.just(resp.getData().get(0));
                });
    }

    public Mono<MarketStackTickersResponse> fetchTickers() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers")
                        .queryParam("access_key", accessKey)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.class);
    }

    public Mono<List<PriceOhlcvDto>> fetchCandlesByMkstackCode(
            String mkstackCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/eod")
                        .queryParam("access_key", accessKey)
                        .queryParam("symbols", mkstackCode)
                        .queryParam("date_from", from.toLocalDate().toString())
                        .queryParam("date_to", to.toLocalDate().toString())
                        .queryParam("limit", 5000)
                        .build()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(MarketStackCandlesResponse.class)
                .map(resp -> mapToPriceOhlcvDtoList(resp, freq));
    }

    private List<PriceOhlcvDto> mapToPriceOhlcvDtoList(MarketStackCandlesResponse resp, Freq freq) {
        if (resp == null || resp.getData() == null) return List.of();

        int rawCount = resp.getData().size();
        List<PriceOhlcvDto> candles = resp.getData().stream()
                .map(d -> toPriceOhlcvDto(d, freq))
                .flatMap(Optional::stream)
                .toList();

        if (rawCount > 0 && candles.isEmpty()) {
            log.warn("Marketstack candles dropped entirely. freq={}, rawCount={}", freq, rawCount);
        }
        return candles;
    }

    private Optional<PriceOhlcvDto> toPriceOhlcvDto(MarketStackCandlesResponse.CandleData data, Freq freq) {
        Optional<OffsetDateTime> ts = parseMarketstackDate(data);
        if (ts.isEmpty()) return Optional.empty();

        if (data.getOpen() == null || data.getHigh() == null || data.getLow() == null || data.getClose() == null) {
            return Optional.empty();
        }

        return Optional.of(PriceOhlcvDto.builder()
                .ts(ts.get())
                .freq(freq)
                .open(data.getOpen())
                .high(data.getHigh())
                .low(data.getLow())
                .close(data.getClose())
                .volume(data.getVolume() != null ? BigDecimal.valueOf(data.getVolume()) : BigDecimal.ZERO)
                .build());
    }

    private Optional<OffsetDateTime> parseMarketstackDate(MarketStackCandlesResponse.CandleData data) {
        if (data == null) return Optional.empty();

        if (data.getEpochSeconds() != null) {
            return Optional.of(OffsetDateTime.ofInstant(Instant.ofEpochSecond(data.getEpochSeconds()), ZoneOffset.UTC));
        }

        if (data.getDate() == null || data.getDate().isBlank()) return Optional.empty();
        String raw = data.getDate();

        try {
            return Optional.of(OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DateTimeParseException ignored) {}

        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                    .toFormatter();
            return Optional.of(OffsetDateTime.parse(raw, formatter));
        } catch (DateTimeParseException ignored) {}

        try {
            LocalDate date = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return Optional.of(date.atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}