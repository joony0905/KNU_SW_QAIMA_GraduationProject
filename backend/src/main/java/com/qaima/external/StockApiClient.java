package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Exchange;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.KisTickerMetaDto;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.dto.StockDto;
import com.qaima.dto.StockMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Primary // StockClient 주입 충돌 방지: "StockClient는 얘가 대표"
@RequiredArgsConstructor
@Slf4j
public class StockApiClient implements StockClient {

    private final KrStockClient krClient;         // KIS 전담
    private final GlobalStockClient globalClient; // Marketstack 전담

    private static final Duration KIS_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MARKETSTACK_TIMEOUT = Duration.ofSeconds(4);

    @Override
    public Mono<StockDto> fetchStock(Stock stock) {
        Mono<StockDto> fromKis = krClient.fetchStock(stock)
                .timeout(KIS_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] KIS fetchStock failed -> empty. code={}, cause={}",
                            stock.getStockCode(), ex.getMessage());
                    return Mono.empty();
                });

        Mono<StockDto> fromGlobal = globalClient.fetchStock(stock)
                .timeout(MARKETSTACK_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] Marketstack fetchStock failed -> empty. code={}, cause={}",
                            stock.getStockCode(), ex.getMessage());
                    return Mono.empty();
                });

        return fromKis.switchIfEmpty(fromGlobal)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("KIS/Marketstack 모두에서 종목 정보를 가져오지 못했습니다: " + stock.getStockCode())
                ));
    }

    @Override
    public Mono<ApiResponse<StockMeta>> fetchTickerMeta(String rawSymbol) {
        String symbol = normalizeSymbolForFetch(rawSymbol);

        if (symbol == null || symbol.isBlank()) {
            return Mono.just(ApiResponse.internalError("INVALID_SYMBOL", "심볼이 비어있습니다."));
        }

        boolean isKorean = symbol.matches("^[0-9]{6}\\.(XKRX|XKOS)$");

        Mono<StockMeta> fromKis = Mono.empty();
        if (isKorean) {
            fromKis = krClient.fetchTickerMeta(symbol) // Mono<KisTickerMetaDto>
                    .timeout(KIS_TIMEOUT)
                    .map(kis -> toStockMetaFromKis(kis, symbol))
                    .onErrorResume(ErrorException.class, e -> {
                        // 디코드 에러는 숨기면 안 됨(내부 버그/스키마 불일치)
                        if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);

                        // http/biz/market_closed 는 폴백 허용
                        log.warn("[StockApiClient] KIS meta fallback allowed. symbol={}, code={}, msg={}",
                                symbol, e.getErrorCode().code(), e.getMessage());
                        return Mono.empty();
                    })
                    .onErrorResume(ex -> {
                        log.warn("[StockApiClient] KIS meta failed -> empty. symbol={}, cause={}",
                                symbol, ex.getMessage());
                        return Mono.empty();
                    });
        }

        Mono<StockMeta> fromGlobal = globalClient.fetchTickerMeta(symbol) // Mono<TickerData>
                .timeout(MARKETSTACK_TIMEOUT)
                .map(this::toStockMetaFromMarketstack)
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] Marketstack meta failed -> empty. symbol={}, cause={}",
                            symbol, ex.getMessage());
                    return Mono.empty();
                });

        Mono<StockMeta> source = isKorean ? fromKis.switchIfEmpty(fromGlobal) : fromGlobal;

        return source
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(
                        ApiResponse.internalError("META_NOT_FOUND", "티커 메타 정보를 가져오지 못했습니다: " + symbol)
                ))
                .onErrorResume(ex -> {
                    log.error("[StockApiClient] fetchTickerMeta fatal error. symbol={}, cause={}",
                            symbol, ex.getMessage(), ex);
                    return Mono.just(ApiResponse.internalError("META_INTERNAL_ERROR", safe(ex.getMessage())));
                });
    }

    @Override
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }

    @Override
    public Mono<CandleFetchResult> fetchCandles(Stock stock, Freq freq, OffsetDateTime from, OffsetDateTime to) {
        String stockCode = stock.getStockCode();
        String marketDivCode = toKisMarketDivCode(stock.getExchange());

        Mono<List<PriceOhlcvDto>> fromKis = krClient
                .fetchCandles(stockCode, marketDivCode, freq, from, to)
                .timeout(KIS_TIMEOUT)
                .onErrorResume(ErrorException.class, e -> {
                    // 디코드 에러는 터뜨림
                    if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);

                    // http/biz/market_closed 는 폴백 허용
                    log.warn("[StockApiClient] KIS candles fallback allowed. code={}, ec={}, msg={}",
                            stockCode, e.getErrorCode().code(), e.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] KIS candles failed -> empty. code={}, cause={}",
                            stockCode, ex.getMessage());
                    return Mono.empty();
                });

        Mono<List<PriceOhlcvDto>> fromGlobal = globalClient
                .fetchCandles(stockCode, freq, from, to)
                .timeout(MARKETSTACK_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("[StockApiClient] Marketstack candles failed -> empty. code={}, cause={}",
                            stockCode, ex.getMessage());
                    return Mono.empty();
                });

        return fromKis
                .map(list -> new CandleFetchResult(list, CandleSource.KIS))
                .switchIfEmpty(fromGlobal.map(list -> new CandleFetchResult(list, CandleSource.MARKETSTACK)))
                .switchIfEmpty(Mono.just(new CandleFetchResult(List.of(), CandleSource.EMPTY)));
    }

    /* =========================
       helpers
       ========================= */

    private String normalizeSymbolForFetch(String raw) {
        if (raw == null) return null;

        String clean = raw.trim().toUpperCase();

        if (clean.matches("^[0-9]{6}$")) return clean + ".XKRX";
        if (clean.matches("^[0-9]{6}\\.(XKRX|XKOS)$")) return clean;

        return clean;
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null) return "J"; // 안전 기본값
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    private StockMeta toStockMetaFromKis(KisTickerMetaDto kis, String symbol) {
        String normalizedSymbol = normalizeSymbolForFetch(symbol);

        return StockMeta.builder()
                .stockCode(normalizedSymbol)
                .companyName(kis.getCompanyName())
                .exchangeCode(normalizeExchangeCode(extractExchangeCodeFromSymbol(normalizedSymbol)))
                .countryCode("KR")
                .currency("KRW")
                .price(kis.getPrice())
                .changeRate(kis.getChangeRate())
                .source("KIS")
                .build();
    }

    private StockMeta toStockMetaFromMarketstack(MarketStackTickersResponse.TickerData data) {
        MarketStackTickersResponse.StockExchange exchange = data.getStock_exchange();

        String exchangeCode = null;
        String countryCode = null;
        String normalizedSymbol = normalizeSymbolForFetch(data.getSymbol());

        if (exchange != null) {
            exchangeCode = exchange.getAcronym() != null ? exchange.getAcronym() : exchange.getMic();
            countryCode = exchange.getCountry_code();
        }

        return StockMeta.builder()
                .stockCode(normalizedSymbol)
                .companyName(data.getName())
                .exchangeCode(normalizeExchangeCode(exchangeCode))
                .countryCode(countryCode)
                .currency(null)
                .price(data.getPrice())
                .changeRate(data.getChangeRate())
                .source("MARKETSTACK")
                .build();
    }

    private String extractExchangeCodeFromSymbol(String symbol) {
        if (symbol == null) return null;
        if (symbol.endsWith(".XKOS")) return "KOSDAQ";
        if (symbol.endsWith(".XKRX")) return "KRX";
        return null;
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;

        String normalized = exchangeCode.trim().toUpperCase();
        String condensed = normalized.replace(" ", "");

        return switch (condensed) {
            case "XKRX", "KRX", "KRXSM" -> "KRX";
            case "XKOS" -> "KOSDAQ";
            case "XKON" -> "KONEX";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> normalized;
        };
    }

    private String safe(String msg) {
        return (msg == null || msg.isBlank()) ? "n/a" : msg;
    }
}
