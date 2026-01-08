package com.qaima.service;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.FinancialDto;
import com.qaima.mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialReadService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final FinancialMapper financialMapper;

    /**
     * 특정 종목코드(stockCode)에 대해 Annual 기준 N년치 재무제표 조회
     */
    @Transactional(readOnly = true)
    public Mono<List<FinancialDto>> getAnnualForLastNYears(String stockCode, int years, LocalDate asOfDate) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stockCode)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stock -> {
                    int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
                    int fromYear = toYear - (years - 1);

                    return Mono.fromCallable(() -> financialRepository
                                    .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
                                            stock,
                                            PeriodType.A,
                                            fromYear,
                                            toYear
                                    ))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .map(financials -> financials.stream()
                        .map(financialMapper::toDto)
                        .toList());
    }

    /**
     * 특정 종목코드(stockCode)에 대해 Annual 기준 단일 연도 재무제표 조회
     */
    @Transactional(readOnly = true)
    public Mono<List<FinancialDto>> getAnnualForYear(String stockCode, int year) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stockCode)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stock -> Mono.fromCallable(() -> financialRepository
                                .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
                                        stock,
                                        PeriodType.A,
                                        year,
                                        year
                                ))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(financials -> financials.stream()
                        .map(financialMapper::toDto)
                        .toList());
    }
}
