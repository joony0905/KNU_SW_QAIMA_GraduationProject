package com.qaima.external;

import com.qaima.common.ApiResponse;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.mkstack.MarketStackTickersResponse;
import com.qaima.dto.stock.StockMeta;
import com.qaima.dto.stock.StockDto;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface StockClient {

    // 종목 + 실시간 시세 조회 (KR/US 공통)
    Mono<StockDto> fetchStock(Stock stock);

    // 티커 메타정보 (디버그용)
    Mono<ApiResponse<StockMeta>> fetchTickerMeta(String symbol);

    // 전체 티커 리스트 (Marketstack 전용)
    Mono<MarketStackTickersResponse> fetchTickers();

    // 캔들 데이터 조회 (KR/US 공통)
    Mono<CandleFetchResult> fetchCandles(Stock stock, Freq freq, OffsetDateTime from, OffsetDateTime to);
}
