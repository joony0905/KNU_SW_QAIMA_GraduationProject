package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.repository.FinancialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final FinancialRepository financialRepository;
    private final PlatformTransactionManager transactionManager;


    /**
     * 한국투자증권 주식현재가(KisStatResponse.Output)를 기반으로
     * 해당 종목의 TTM 스냅샷을 financial에 upsert.
     *
     * - 기준일(asOfDate) 기준 fiscalYear 설정
     * - 스냅샷 특성상 fiscalQuarter는 null 유지(호환용)
     * - periodType = TTM 고정
     * - periodNo = 0 고정 (새 유니크 키의 핵심)
     */


    public Mono<List<Financial>> getLastNYearsAnnualByStock(Stock stock, int years, LocalDate asOfDate) {
        return Blocking.call(() -> {
            int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
            int fromYear = toYear - (years - 1);

            return financialRepository
                    .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                            stock, PeriodType.A, fromYear, toYear
                    );
        });
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}


