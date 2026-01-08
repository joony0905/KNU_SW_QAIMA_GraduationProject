package com.qaima.service;

import com.qaima.domain.Freq;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ChartService {

    private final StockService stockService;
    private final CandleLoadService candleLoadService;

    /**
     * 원칙:
     * - Service는 데이터 로딩 결과만 반환 (source/empty 포함)
     * - ApiResponse / warning 판단은 Controller 책임
     */
    public Mono<CandleLoadResult> getCandles(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return stockService.getOrCreateStockByCode(stockCode)
                .flatMap(stock -> candleLoadService.load(stock, freq, from, to));
    }
}
