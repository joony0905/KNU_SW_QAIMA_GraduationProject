package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StockApiClient {

    //private final KrMarketStockClient krClient; -> kis사용시 활성화 고려
    private final GlobalStockClient globalClient;

    public StockDto fetchStock(Stock stock) {
        String country = stock.getExchange().getCountry();  // 'KR' / 'US' / etc

        return switch (country) {
            case "KR", "US" -> globalClient.fetchStock(stock);
            default -> null; // 나중에 다른 국가 확장 or 예외 처리
        };
    }

    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        // 지금은 모든 국가 Marketstack(Single Provider)
        return globalClient.fetchTickerMeta(symbol);
    }

    public Mono<MarketStackTickersResponse> fetchTickers() {
        return globalClient.fetchTickers();
    }

}
