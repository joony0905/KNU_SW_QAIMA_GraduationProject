package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackCandlesResponse;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.dto.StockDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.qaima.domain.Freq;
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

    public GlobalStockClient(
            @Qualifier("marketstackWebClient") WebClient webClient
    ) {
        this.webClient = webClient;
    }

    public Mono<StockDto> fetchStock(Stock stock) {
        String symbol = stock.getStockCode();

        // 1) 6자리 숫자면 국내 종목 → 기본은 XKRX 추후 확장해야함..
        if (symbol != null && symbol.matches("^[0-9]{6}$")) {
            symbol = symbol + ".XKRX";

        }
        final String symbolStr = symbol;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + symbolStr)
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class)
                .map(t -> mapToStockDto(stock, t));
    }

    private StockDto mapToStockDto(Stock s, MarketStackTickersResponse.TickerData t) {

        BigDecimal price = (t.getPrice() != null ? t.getPrice() : null);
        BigDecimal change = (t.getChangeRate() != null ? t.getChangeRate() : null);

        Long industryId = (s.getIndustry() != null)
                ? s.getIndustry().getIndustryId()
                : null;

        return StockDto.builder()
                .stockId(s.getStockId())
                .stockCode(s.getStockCode())
                .isin(s.getIsin())
                .companyName(s.getCompanyName())

                .exchangeId(s.getExchange().getExchangeId())
                .exchangeCode(s.getExchange().getCode())

                .assetType(s.getAssetType())
                .currency(s.getCurrency())
                .industryId(industryId)

                .price(price)
                .changeRate(change)

                .listedAt(s.getListedAt())
                .delistedAt(s.getDelistedAt())
                .build();
    }


    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        if (symbol != null && symbol.matches("^[0-9]{6}$")) {
            symbol = symbol + ".XKRX";

        }
        final String symbolStr = symbol;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + symbolStr)
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class);
    }

    public Mono<MarketStackTickersResponse> fetchTickers() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers")
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.class);
    }

    /**
     * Marketstack (Global) 캔들/EOD 조회
     * - KIS 실패 시 폴백용
     */
    public Mono<List<PriceOhlcvDto>> fetchCandles(
            String symbol,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        if (symbol != null && symbol.matches("^[0-9]{6}$")) {
            symbol = symbol + ".XKRX";

        }
        final String symbolStr = symbol;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/eod")
                        .queryParam("access_key", accessKey)
                        .queryParam("symbols", symbolStr)
                        .queryParam("date_from", from.toLocalDate().toString())
                        .queryParam("date_to", to.toLocalDate().toString())
                        .queryParam("limit", 5000)
                        .build()
                )
                .retrieve()
                .bodyToMono(MarketStackCandlesResponse.class)
                .map(resp -> mapToPriceOhlcvDtoList(resp, freq));
    }

    private List<PriceOhlcvDto> mapToPriceOhlcvDtoList(MarketStackCandlesResponse resp, Freq freq) {

        if (resp == null || resp.getData() == null) {
            return List.of();
        }

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
        if (ts.isEmpty()) {
            return Optional.empty();
        }

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
        if (data.getEpochSeconds() != null) {
            return Optional.of(OffsetDateTime.ofInstant(Instant.ofEpochSecond(data.getEpochSeconds()), ZoneOffset.UTC));
        }

        if (data.getDate() == null || data.getDate().isBlank()) {
            return Optional.empty();
        }

        String raw = data.getDate();

        try {
            return Optional.of(OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DateTimeParseException ignored) {
            // 보조 포맷으로 한 번 더 파싱 시도
        }

        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                    .toFormatter();
            return Optional.of(OffsetDateTime.parse(raw, formatter));
        } catch (DateTimeParseException ignored) {
            // 날짜만 있는 포맷으로 한 번 더 파싱 시도
        }

        try {
            LocalDate date = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return Optional.of(date.atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

}
