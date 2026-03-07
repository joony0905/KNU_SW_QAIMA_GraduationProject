package com.qaima.repository;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("""
        select f from Financial f
        join fetch f.stock s
        where f.stock = :stock
          and f.periodType = :periodType
          and f.fiscalYear between :fromYear and :toYear
        order by f.fiscalYear desc, f.periodNo desc
    """)
    List<Financial> findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
            @Param("stock") Stock stock,
            @Param("periodType") PeriodType periodType,
            @Param("fromYear") int fromYear,
            @Param("toYear") int toYear
    );

    // 최근 N개 재무제표 조회 (reportDate -> version 순으로 최신), N은 호출부에서 Pageable로 제어
    List<Financial> findByStockOrderByReportDateDescVersionDesc(
            Stock stock,
            Pageable pageable
    );

    @Query("""
        select f from Financial f
        join fetch f.stock s
        where f.stock = :stock
          and f.periodType = :periodType
          and f.periodNo = :periodNo
          and f.fiscalYear between :fromYear and :toYear
        order by f.fiscalYear desc, f.periodNo desc
    """)
    List<Financial> findByStockAndPeriodTypeAndPeriodNoAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
            @Param("stock") Stock stock,
            @Param("periodType") PeriodType periodType,
            @Param("periodNo") int periodNo,
            @Param("fromYear") int fromYear,
            @Param("toYear") int toYear
    );




}
