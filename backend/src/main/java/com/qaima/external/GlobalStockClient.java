package com.qaima.external;

import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.mkstack.MarketStackCandlesResponse;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
     * Marketstack ticker 조회 (Fallback 용)
     * - 반환 StockDto.stockCode 는 항상 canonical (005930/AAPL)
     * - Marketstack 호출용 심볼만 suffix 부착 (005930.XKRX)
     */
    public Mono<StockDto> fetchStock(Stock stock) {
        final String canonicalCode = stock.getStockCode();
        final String marketstackSymbol = toMarketstackSymbol(canonicalCode);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + marketstackSymbol)
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class)
                .map(ticker -> mapToStockDto(stock, ticker, canonicalCode));
    }

    /**
     * (내부용) Marketstack ticker meta raw
     * - 여기서 반환하는 TickerData는 marketstack 형태 그대로
     */
    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        final String canonicalCode = stripMarketSuffixIfAny(symbol);
        final String marketstackSymbol = toMarketstackSymbol(canonicalCode);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + marketstackSymbol)
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
     * - 입력 symbol은 canonical(005930/AAPL) 기준으로 받고, 요청시에만 suffix 부착
     */
    public Mono<List<PriceOhlcvDto>> fetchCandles(
            String symbol,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        final String canonicalCode = stripMarketSuffixIfAny(symbol);
        final String marketstackSymbol = toMarketstackSymbol(canonicalCode);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/eod")
                        .queryParam("access_key", accessKey)
                        .queryParam("symbols", marketstackSymbol)
                        .queryParam("date_from", from.toLocalDate().toString())
                        .queryParam("date_to", to.toLocalDate().toString())
                        .queryParam("limit", 5000)
                        .build()
                )
                .retrieve()
                .bodyToMono(MarketStackCandlesResponse.class)
                .map(resp -> mapToPriceOhlcvDtoList(resp, freq));
    }

    /* =========================
       Mapping
    ========================= */

    private StockDto mapToStockDto(Stock stock, MarketStackTickersResponse.TickerData ticker, String canonicalCode) {
        BigDecimal price = ticker != null ? ticker.getPrice() : null;
        BigDecimal changeRate = ticker != null ? ticker.getChangeRate() : null;

        Long industryId = (stock.getIndustry() != null)
                ? stock.getIndustry().getIndustryId()
                : null;

        // exchangeId / exchangeCode는 stock.getExchange()가 null일 수 있으니 방어
        Long exchangeId = (stock.getExchange() != null) ? stock.getExchange().getExchangeId() : null;
        String exchangeCode = (stock.getExchange() != null) ? stock.getExchange().getCode() : null;

        return StockDto.builder()
                .stockId(stock.getStockId())
                .stockCode(canonicalCode) // ★ 절대 marketstackSymbol 넣지 말 것
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName())

                .exchangeId(exchangeId)
                .exchangeCode(exchangeCode)

                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(industryId)

                .price(price)
                .changeRate(changeRate)

                .listedAt(stock.getListedAt())
                .delistedAt(stock.getDelistedAt())
                .build();
    }

    /* =========================
       Symbol helpers
    ========================= */

    /**
     * Marketstack 호출용 심볼로 변환
     * - 6자리 숫자(국내)면 기본 .XKRX 부착 (추후 KOSPI/KOSDAQ 분리 가능)
     * - 그 외는 그대로 (AAPL 등)
     */
    private String toMarketstackSymbol(String canonicalCode) {
        if (canonicalCode == null) return null;
        if (canonicalCode.matches("^[0-9]{6}$")) {
            return canonicalCode + ".XKRX";
        }
        return canonicalCode;
    }

    /**
     * 혹시 입력이 already suffixed(005930.XKRX 등)로 들어와도 canonical로 복원
     */
    private String stripMarketSuffixIfAny(String symbol) {
        if (symbol == null) return null;
        // Marketstack 표기 케이스만 우선 제거 (필요 시 확장)
        return symbol.replace(".XKRX", "").replace(".XKOS", "");
    }

    /* =========================
       Candle mapping
    ========================= */

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

        if (data.getDate() == null || data.getDate().isBlank()) {
            return Optional.empty();
        }

        String raw = data.getDate();

        try {
            return Optional.of(OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DateTimeParseException ignored) { }

        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                    .toFormatter();
            return Optional.of(OffsetDateTime.parse(raw, formatter));
        } catch (DateTimeParseException ignored) { }

        try {
            LocalDate date = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return Optional.of(date.atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}