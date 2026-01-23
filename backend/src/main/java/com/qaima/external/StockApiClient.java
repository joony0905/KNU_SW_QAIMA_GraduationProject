package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockApiClient implements StockClient {

    private final KrStockClient krClient;
    private final GlobalStockClient globalClient;

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
    public Mono<ApiResponse<MarketStackTickersResponse.TickerData>> fetchTickerMeta(String rawSymbol) {
        String symbol = normalizeSymbolForFetch(rawSymbol);

        if (symbol == null || symbol.isBlank()) {
            return Mono.just(ApiResponse.internalError(
                    "INVALID_SYMBOL",
                    "심볼이 비어있습니다."
            ));
        }

        boolean isKorean = symbol.matches("^[0-9]{6}\\.(XKRX|XKOS)$");

        Mono<MarketStackTickersResponse.TickerData> source;

        if (isKorean) {
            // 1차: KIS 메타
            Mono<MarketStackTickersResponse.TickerData> fromKis =
                    krClient.fetchTickerMeta(symbol)
                            .onErrorResume(ex -> {
                                log.warn("[StockApiClient] KIS meta 실패 → empty. symbol={}, cause={}",
                                        symbol, ex.getMessage());
                                return Mono.empty();
                            });

            // 2차: Marketstack 메타
            Mono<MarketStackTickersResponse.TickerData> fromGlobal =
                    globalClient.fetchTickerMeta(symbol)
                            .onErrorResume(ex -> {
                                log.warn("[StockApiClient] Marketstack meta 실패 → empty. symbol={}, cause={}",
                                        symbol, ex.getMessage());
                                return Mono.empty();
                            });

            source = fromKis.switchIfEmpty(fromGlobal);
        } else {
            // 글로벌 티커는 바로 Marketstack
            source = globalClient.fetchTickerMeta(symbol)
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
                    return Mono.error(
                            new IllegalStateException(
                                    "META_INTERNAL_ERROR: ..."));
                });
    }

    @Override
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
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
}
