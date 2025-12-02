package com.qaima.repository;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialRepository extends JpaRepository<Financial, Long> {

    // KIS upsert에서 사용
    Optional<Financial> findByStockAndFiscalYearAndFiscalQuarterAndPeriodType(
            Stock stock,
            int fiscalYear,
            Integer fiscalQuarter,
            PeriodType periodType
    );

    // 5개년 Annual 재무제표 조회
    List<Financial> findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
            Stock stock,
            PeriodType periodType,
            int fromYear,
            int toYear
    );
}
