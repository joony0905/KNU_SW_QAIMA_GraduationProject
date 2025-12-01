package com.qaima.api.stock;

import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import com.qaima.external.StockApiClient;
import com.qaima.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


// Marketstack 티커 API 테스트용 엔드포인트
// 예시:
//  - 전체:  /api/v1/stocks/marketstack/tickers
//  - 코스닥: /api/v1/stocks/marketstack/tickers?exchange=XKOS
//  - 나스닥: /api/v1/stocks/marketstack/tickers?exchange=XNAS&limit=50
//  - 검색:  /api/v1/stocks/marketstack/tickers?search=samsung
// 삼성전자: /api/v1/stocks/marketstack/ticker/005930.XKRX
// 애플: /api/v1/stocks/marketstack/ticker/AAPL

/**
 * 종목 관련 API
 * - /api/stocks/{stockId} : DB + 외부 시세 합쳐서 반환
 * - /api/stocks/debug/ticker-meta : 외부 시세만 직접 확인 (KIS → 실패 시 Marketstack)
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockApiClient stockApiClient;

    /**
     * 내부 stockId 기준으로 종목 + 실시간 시세 조회
     * 예: GET /api/stocks/1
     */
    @GetMapping("/{stockId}")
    public Mono<StockDto> getStock(@PathVariable Long stockId) {
        return stockService.getStockWithRealtime(stockId);
    }

    /**
     * 심볼(티커) 기준으로 외부 시세 메타 조회 (디버그용)
     * 예:
     *  - /api/stocks/debug/ticker-meta?symbol=005930
     *  - /api/stocks/debug/ticker-meta?symbol=AAPL
     */
    @GetMapping("/debug/ticker-meta")
    public Mono<MarketStackTickersResponse.TickerData> getTickerMeta(
            @RequestParam String symbol
    ) {
        return stockApiClient.fetchTickerMeta(symbol);
    }
}
