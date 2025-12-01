package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockApiClient {

    private final KrStockClient krClient;
    private final GlobalStockClient globalClient;


    public StockDto fetchStock(Stock stock) {
        String country = stock.getExchange().getCountry();  // 'KR' / 'US' / etc

        return switch (country) {
            case "KR", "US" -> globalClient.fetchStock(stock);
            default -> null; // 나중에 다른 국가 확장 or 예외 처리
        };
    }

    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        boolean isKoreanStock = symbol != null
                && symbol.matches("^[0-9]{6}(\\.XKRX|\\.XKOS)?$");

        if (isKoreanStock) {
            return krClient.fetchTickerMeta(symbol)
                    .doOnSubscribe(sub ->
                            log.info("[StockApiClient] {} → KIS 먼저 호출", symbol))
                    .switchIfEmpty(Mono.defer(() -> {
                        String msSymbol = symbol;
                        if (symbol.matches("^[0-9]{6}$")) {
                            msSymbol = symbol + ".XKRX";
                        }

                        log.warn("[StockApiClient] {} → KIS 결과 없음, Marketstack({}) fallback", symbol, msSymbol);
                        return globalClient.fetchTickerMeta(msSymbol);
                    }));
        } else {
            log.info("[StockApiClient] {} → 해외 종목, Marketstack 바로 호출", symbol);
            return globalClient.fetchTickerMeta(symbol);
        }
    }

    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }
}


