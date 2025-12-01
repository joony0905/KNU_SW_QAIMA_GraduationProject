package com.qaima.external;

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

    @Override
    public Mono<StockDto> fetchStock(Stock stock) {
        String country = stock.getExchange().getCountry();

        return switch (country) {
            case "KR" -> krClient.fetchStock(stock)
                    .onErrorResume(e -> {
                        log.warn("[StockApiClient] KIS 실패 → Marketstack fallback: {}", e.getMessage());
                        return globalClient.fetchStock(stock);
                    });
            case "US" -> globalClient.fetchStock(stock);
            default -> Mono.error(new IllegalArgumentException("지원하지 않는 국가: " + country));
        };
    }

    @Override
    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        boolean isKorean = symbol != null &&
                symbol.matches("^[0-9]{6}(\\.XKRX|\\.XKOS)?$");

        if (isKorean) {
            return krClient.fetchTickerMeta(symbol)
                    .switchIfEmpty(Mono.defer(() -> {
                        String msSymbol = symbol.matches("^[0-9]{6}$")
                                ? symbol + ".XKRX"
                                : symbol;
                        return globalClient.fetchTickerMeta(msSymbol);
                    }));
        } else {
            return globalClient.fetchTickerMeta(symbol);
        }
    }

    @Override
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }
}
