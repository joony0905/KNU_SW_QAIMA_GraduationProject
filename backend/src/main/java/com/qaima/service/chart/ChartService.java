package com.qaima.service.chart;

import com.qaima.domain.Freq;
import com.qaima.service.candle.CandleLoadResult;
import com.qaima.service.candle.CandleLoadService;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ChartService {

    /**
     * 원칙:
     * - Service는 데이터 로딩 결과만 반환 (source/empty 포함)
     * - ApiResponse / warning 판단은 Controller 책임
     */

    private final StockService stockService;
    private final CandleLoadService candleLoadService;

    public Mono<CandleLoadResult> getCandles(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return stockService.getOrCreateStockByCode(stockCode)
                .flatMap(stock -> candleLoadService.load(stock, freq, from, to));
    }

    // 줌아웃시 과거 차트 불러오기
    public Mono<CandleLoadResult> getCandlesBefore(
            String stockCode,
            Freq freq,
            OffsetDateTime to,
            int limit
    ) {
        return stockService.getOrCreateStockByCode(stockCode)
                .flatMap(stock -> candleLoadService.loadBefore(stock, freq, to, limit));
    }
}
