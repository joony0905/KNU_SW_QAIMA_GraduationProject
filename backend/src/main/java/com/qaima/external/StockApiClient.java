package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CandleSource;
import com.qaima.domain.Exchange;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.kis.KisTickerMetaDto;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import com.qaima.dto.stock.StockMeta;
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
    public Mono<ApiResponse<StockMeta>> fetchTickerMeta(String rawStockCode) {
        String stockCode = normalizeCodeForFetch(rawStockCode);

        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just(ApiResponse.internalError(
                    "INVALID_STOCK_CODE",
                    "[fetchTickerMeta] 종목코드가 비어있습니다."
            ));
        }

        // normalizeCodeForFetch가 국내면 6자리 또는 6자리.(XKRX|XKOS) 형태를 만들어줌
        boolean isKorean = stockCode.matches("^[0-9]{6}\\.(XKRX|XKOS)$");

        Mono<StockMeta> fromKis = Mono.empty();
        if (isKorean) {
            fromKis = krClient.fetchTickerMeta(stockCode) // Mono<KisTickerMetaDto>
                    .timeout(KIS_TIMEOUT)
                    .map(kis -> toStockMetaFromKis(kis, stockCode))
                    .onErrorResume(ErrorException.class, e -> {
                        // 디코드 에러는 숨기면 안 됨(내부 버그/스키마 불일치)
                        if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) return Mono.error(e);

                        // http/biz/market_closed 는 폴백 허용
                        log.warn("[StockApiClient] KIS meta fallback allowed. stockCode={}, ec={}, msg={}",
                                stockCode, e.getErrorCode().code(), e.getMessage());
                        return Mono.empty();
                    })
                    .onErrorResume(ex -> {
                        log.warn("[StockApiClient] KIS meta failed -> empty. stockCode={}, cause={}",
                                stockCode, ex.getMessage());
                        return Mono.empty();
                    });
        }

        // 국내 kis 실패시 폴백
        Mono<StockMeta> fromGlobal =
                globalClient.fetchTickerMeta(stockCode)
                        .timeout(MARKETSTACK_TIMEOUT)
                        .map(this::toStockMetaFromMarketstack)
                        .onErrorResume(ex -> {
                            log.warn("[StockApiClient] Marketstack meta failed -> empty. stockCode={}, cause={}",
                                    stockCode, ex.getMessage());
                            return Mono.empty();
                        });
        
        // 국내: KIS 우선, 비면(허용된 케이스) 글로벌 폴백
        // 해외: 글로벌만
        Mono<StockMeta> source = isKorean
                ? fromKis.switchIfEmpty(fromGlobal) // 국내 KIS 실패 시 글로벌 폴백
                : fromGlobal;

        return source
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.just(
                        ApiResponse.internalError(
                                "META_NOT_FOUND",
                                "[fetchTickerMeta] 티커 메타 정보를 가져오지 못했습니다: " + stockCode
                        )
                ))
                .onErrorResume(ex -> {
                    log.error("[StockApiClient] fetchTickerMeta fatal error. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(ApiResponse.internalError(
                            "META_INTERNAL_ERROR",
                            safe(ex.getMessage())
                    ));
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

    private String normalizeCodeForFetch(String raw) {
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

    private StockMeta toStockMetaFromKis(KisTickerMetaDto kis, String StockCode) {
        String normalizedStockCode = normalizeCodeForFetch(StockCode);

        return StockMeta.builder()
                .stockCode(normalizedStockCode)
                .companyName(kis.getCompanyName())
                .exchangeCode(kis.getExchangeCode())
                .countryCode("KR")
                .currency(kis.getCurrency())
                .price(kis.getPrice())
                .changeRate(kis.getChangeRate())
                .source("KIS")
                .build();
    }

    private StockMeta toStockMetaFromMarketstack(MarketStackTickersResponse.TickerData data) {
        MarketStackTickersResponse.StockExchange exchange = data.getStock_exchange();

        String exchangeCode = null;
        String countryCode = null;
        String normalizedStockCode = normalizeCodeForFetch(data.getSymbol()); //symbol = StockCode

        if (exchange != null) {
            exchangeCode = exchange.getAcronym() != null ? exchange.getAcronym() : exchange.getMic();
            countryCode = exchange.getCountry_code();
        }

        return StockMeta.builder()
                .stockCode(normalizedStockCode)
                .companyName(data.getName())
                .exchangeCode(normalizeExchangeCode(exchangeCode))
                .countryCode(countryCode)
                .currency(null)
                .price(data.getPrice())
                .changeRate(data.getChangeRate())
                .source("MARKETSTACK")
                .build();
    }


    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;

        String c = exchangeCode.trim().toUpperCase().replace(" ", "");

        return switch (c) {
            case "KOSPI" -> "KOSPI";
            case "KOSDAQ" -> "KOSDAQ";
            case "KONEX" -> "KONEX";
            case "KRX", "XKRX" -> "KRX";
            case "NASDAQ", "XNAS" -> "NASDAQ";
            case "NYSE", "XNYS" -> "NYSE";
            default -> c; // throw 금지
        };
    }

    private String safe(String msg) {
        return (msg == null || msg.isBlank()) ? "n/a" : msg;
    }
}
