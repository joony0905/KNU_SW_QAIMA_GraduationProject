package com.qaima.repository;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface FinancialRepository extends JpaRepository<Financial, Long> {

    // KIS upsert에서 사용
    Optional<Financial> findByStockAndFiscalYearAndPeriodNoAndPeriodType(
            Stock stock,
            int fiscalYear,
            int periodNo,
            PeriodType periodType
    );


    // 5개년 Annual 재무제표 조회
    @EntityGraph(attributePaths = "stock")
    List<Financial> findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
            Stock stock,
            PeriodType periodType,
            int fromYear,
            int toYear
    );

    // 최근 N개 재무제표 조회 (reportDate -> version 순으로 최신), N은 호출부에서 Pageable로 제어
    @EntityGraph(attributePaths = "stock")
    List<Financial> findByStockOrderByReportDateDescVersionDesc(
            Stock stock,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "stock")
    List<Financial> findByStockAndPeriodTypeAndPeriodNoAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
            Stock stock,
            PeriodType periodType,
            int periodNo,
            int fromYear,
            int toYear
    );

    @EntityGraph(attributePaths = "stock")
    List<Financial> findByStockAndPeriodTypeOrderByFiscalYearDescVersionDesc(
            Stock stock,
            PeriodType periodType,
            org.springframework.data.domain.Pageable pageable
    );

    Optional<Financial> findTopByStockAndPeriodTypeOrderByFiscalYearDescPeriodNoDescVersionDesc(
            Stock stock,
            PeriodType periodType
    );





}
