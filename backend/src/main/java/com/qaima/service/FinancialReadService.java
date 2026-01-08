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
    public List<FinancialDto> getAnnualForLastNYears(String stockCode, int years, LocalDate asOfDate) {
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode));

        int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
        int fromYear = toYear - (years - 1);

        List<Financial> financials = financialRepository
                .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
                        stock,
                        PeriodType.A,
                        fromYear,
                        toYear
                );

        return financials.stream()
                .map(financialMapper::toDto)
                .toList();
    }

    /**
     * 특정 종목코드(stockCode)에 대해 Annual 기준 단일 연도 재무제표 조회
     */
    @Transactional(readOnly = true)
    public List<FinancialDto> getAnnualForYear(String stockCode, int year) {
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode));

        List<Financial> financials = financialRepository
                .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
                        stock,
                        PeriodType.A,
                        year,
                        year
                );

        return financials.stream()
                .map(financialMapper::toDto)
                .toList();
    }
}
