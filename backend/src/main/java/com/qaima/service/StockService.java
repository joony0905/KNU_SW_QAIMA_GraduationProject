package com.qaima.service;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import com.qaima.external.StockApiClient;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockApiClient stockApiClient;
    private final StockRepository stockRepository;

    public StockDto getStock(String stockCode) {
        //StockService에서 DB로 Stock, Exchange 조회해서 넘긴다
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                .orElse(null);

        if (stock == null) {
            // TODO: 여기서 없는 종목이면 최초 등록 로직 고려
            return null;
        }

        // 1) DB 기반으로 적절한 외부 API 라우팅 + 시세 가져오기
        StockDto dto = stockApiClient.fetchStock(stock);
        if (dto == null) {
            return null;
        }

        // 2) TODO: 여기서 indicator, 캐시, etc. 붙이기
        return dto;
    }

    public Mono<MarketStackTickersResponse.TickerData> getTickerMeta(String symbol) {
        return stockApiClient.fetchTickerMeta(symbol);
    }
}
