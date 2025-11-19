package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GlobalStockClient implements StockClient {

    @Qualifier("marketstackClient")
    private final WebClient marketstackClient;

    @Value("${marketstack.access-key}")
    private String accessKey;

    //기존: Stock 엔티티 기반 fetch
    @Override
    public StockDto fetchStock(Stock stock) {
        // TODO: 나중에 실제 Marketstack 시세 API로 변경
        // 현재는 티커 메타 조회만 먼저 붙이니까 일단 더미로
        return null;
    }

    // 단일 티커 조회
    @Override
    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        return marketstackClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/{symbol}")
                        .queryParam("access_key", accessKey)
                        .build(symbol)
                )
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class);
    }

    // 다중 티커 조회
    public Mono<MarketStackTickersResponse> fetchTickers() {
        return this.marketstackClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers") // ('단일'이 아닌 '목록' 주소)
                        .queryParam("access_key", this.accessKey)
                        .queryParam("limit", 100) // (테스트용, 100개만)
                        //.queryParam("exchange", "XKRX,XNAS") // (한국, 나스닥)
                        .build()
                )
                .retrieve() // (요청 실행)
                .bodyToMono(MarketStackTickersResponse.class); // (응답을 DTO로 변환)
    }
}
