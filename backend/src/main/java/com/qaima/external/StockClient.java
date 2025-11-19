package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import reactor.core.publisher.Mono;

public interface StockClient {

    StockDto fetchStock(Stock stock);

    Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol);
    Mono<MarketStackTickersResponse> fetchTickers();

}
