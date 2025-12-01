package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import reactor.core.publisher.Mono;

public interface StockClient {

    // 종목 + 실시간 시세 조회 (KR/US 공통)
    Mono<StockDto> fetchStock(Stock stock);

    // 티커 메타정보 (디버그용)
    Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol);

    // 전체 티커 리스트 (Marketstack 전용)
    Mono<MarketStackTickersResponse> fetchTickers();
}
