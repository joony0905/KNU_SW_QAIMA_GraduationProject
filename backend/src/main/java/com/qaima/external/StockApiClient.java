package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.KisTickerMetaDto;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.dto.StockMeta;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockApiClient implements StockClient {

    private final KrStockClient krClient;
    private final GlobalStockClient globalClient;

    private static final Duration KIS_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration MARKETSTACK_TIMEOUT = Duration.ofSeconds(4);

    /**
     * 종목 기본 정보 조회
     * - 국가 상관없이 일단 KIS 먼저 시도
     * - 실패/에러 시 Marketstack으로 폴백
     */
    @Override
    public Mono<StockDto> fetchStock(Stock stock) {
        // 1차: KIS
        Mono<StockDto> fromKis = krClient.fetchStock(stock)
                .onErrorResume(e -> {
                    log.warn("[StockApiClient] KIS fetchStock 실패 → empty fallback. code={}, cause={}",
                            stock.getStockCode(), e.getMessage());
                    return Mono.empty();
                });

        // 2차: Marketstack
        Mono<StockDto> fromGlobal = globalClient.fetchStock(stock)
                .onErrorResume(e -> {
                    log.warn("[StockApiClient] Global fetchStock 실패 → empty. code={}, cause={}",
                            stock.getStockCode(), e.getMessage());
                    return Mono.empty();
                });

        return fromKis
                .switchIfEmpty(fromGlobal)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("KIS/Marketstack 모두에서 종목 정보를 가져오지 못했습니다: " + stock.getStockCode())
                ));
    }

    /**
     * 티커 메타정보 조회 (ApiResponse 래핑)
     * - rawSymbol 정규화 (005930 → 005930.XKRX)
     * - 국내 심볼이면 KIS → 실패/empty 시 Marketstack 폴백
     * - 그 외는 바로 Marketstack
     */
    @Override
    public Mono<ApiResponse<StockMeta>> fetchTickerMeta(String rawSymbol) {
        String symbol = normalizeSymbolForFetch(rawSymbol);

        if (symbol == null || symbol.isBlank()) {
            return Mono.just(ApiResponse.internalError(
                    "INVALID_SYMBOL",
                    "심볼이 비어있습니다."
            ));
        }

        boolean isKorean = symbol.matches("^[0-9]{6}\\.(XKRX|XKOS)$");

        Mono<StockMeta> source;

        if (isKorean) {
            // 1차: KIS 메타
            Mono<StockMeta> fromKis =
                    krClient.fetchTickerMeta(symbol)
                            .map(kis -> toStockMetaFromKis(kis, symbol))
                            .onErrorResume(ex -> {
                                log.warn("[StockApiClient] KIS meta 실패 → empty. symbol={}, cause={}",
                                        symbol, ex.getMessage());
                                return Mono.empty();
                            });

            // 2차: Marketstack 메타
            Mono<StockMeta> fromGlobal =
                    globalClient.fetchTickerMeta(symbol)
                            .map(this::toStockMetaFromMarketstack)
                            .onErrorResume(ex -> {
                                log.warn("[StockApiClient] Marketstack meta 실패 → empty. symbol={}, cause={}",
                                        symbol, ex.getMessage());
                                return Mono.empty();
                            });

            source = fromKis.switchIfEmpty(fromGlobal);
        } else {
            // 글로벌 티커는 바로 Marketstack
            source = globalClient.fetchTickerMeta(symbol)
                    .map(this::toStockMetaFromMarketstack)
                    .onErrorResume(ex -> {
                        log.warn("[StockApiClient] Global meta 실패 → empty. symbol={}, cause={}",
                                symbol, ex.getMessage());
                        return Mono.empty();
                    });
        }

        // ApiResponse 래핑
        return source
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(
                        ApiResponse.internalError(
                                "META_NOT_FOUND",
                                "티커 메타 정보를 가져오지 못했습니다: " + symbol
                        )
                ))
                .onErrorResume(ex -> {
                    log.error("[StockApiClient] fetchTickerMeta fatal error. symbol={}, cause={}",
                            symbol, ex.getMessage(), ex);
                    return Mono.just(ApiResponse.internalError(
                            "META_INTERNAL_ERROR",
                            ex.getMessage()
                    ));
                });
    }

    @Override
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }

    /**
     * 캔들 데이터 조회
     * - KIS 먼저 시도 → 실패/에러 시 Marketstack으로 폴백
     */
    @Override
    public Mono<CandleFetchResult> fetchCandles(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String stockCode = stock.getStockCode();
        String marketDivCode = toKisMarketDivCode(stock.getExchange());

        Mono<List<PriceOhlcvDto>> fromKis = krClient
                .fetchCandles(stockCode, marketDivCode, freq, from, to)
                .timeout(KIS_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("[StockApiClient] KIS candles 실패 → empty fallback. code={}, cause={}",
                            stockCode, e.getMessage());
                    return Mono.empty();
                });

        Mono<List<PriceOhlcvDto>> fromGlobal = globalClient
                .fetchCandles(stockCode, freq, from, to)
                .timeout(MARKETSTACK_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("[StockApiClient] Marketstack candles 실패 → empty. code={}, cause={}",
                            stockCode, e.getMessage());
                    return Mono.empty();
                });

        return fromKis
                .map(list -> new CandleFetchResult(list, CandleSource.KIS))
                .switchIfEmpty(fromGlobal.map(list -> new CandleFetchResult(list, CandleSource.MARKETSTACK)))
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("KIS/Marketstack 모두에서 캔들 정보를 가져오지 못했습니다: " + stockCode)
                ));
    }

    /**
     * 심볼 정규화
     * - "005930"        -> "005930.XKRX"
     * - "005930.XKRX"   -> 그대로
     * - "005930.XKOS"   -> 그대로
     * - "AAPL" / "MSFT" -> 그대로 (대문자 + trim)
     */
    private String normalizeSymbolForFetch(String raw) {
        if (raw == null) return null;

        String clean = raw.trim().toUpperCase();

        if (clean.matches("^[0-9]{6}$")) {
            // 국내 6자리 종목코드 → 기본 XKRX
            return clean + ".XKRX";
        }
        if (clean.matches("^[0-9]{6}\\.(XKRX|XKOS)$")) {
            return clean;
        }
        // 나머지는 글로벌 심볼로 간주
        return clean;
    }

    private String toKisMarketDivCode(com.qaima.domain.Exchange exchange) {
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
                .symbol(normalizedSymbol)
                .name(kis.getName())
                .exchangeCode(normalizeExchangeCode(extractExchangeCodeFromSymbol(normalizedSymbol)))
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
                .symbol(normalizedSymbol)
                .name(data.getName())
                .exchangeCode(normalizeExchangeCode(exchangeCode))
                .countryCode(countryCode)
                .price(data.getPrice())
                .changeRate(data.getChangeRate())
                .source("MARKETSTACK")
                .build();
    }

    private String extractExchangeCodeFromSymbol(String symbol) {
        if (symbol == null) return null;
        if (symbol.endsWith(".XKOS")) {
            return "KOSDAQ";
        }
        if (symbol.endsWith(".XKRX")) {
            return "KRX";
        }
        return null;
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;

        String normalized = exchangeCode.trim().toUpperCase();
        if (normalized.startsWith("KRX ")) {
            return "KRX";
        }

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
}
