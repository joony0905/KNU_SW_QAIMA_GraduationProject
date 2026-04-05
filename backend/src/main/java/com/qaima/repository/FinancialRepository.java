package com.qaima.repository;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FinancialRepository extends JpaRepository<Financial, Long> {

    /**
     * 동일 기간(stock + fiscalYear + periodType + periodNo)의 단일 스냅샷 조회
     * importer / upsert 기준 조회
     */
    Optional<Financial> findByStockAndFiscalYearAndPeriodNoAndPeriodType(
            Stock stock,
            int fiscalYear,
            int periodNo,
            PeriodType periodType
    );

    /**
     * 연간(A) 또는 분기/반기 타입별 기간 조회
     * 예: 최근 5개년 Annual 재무제표
     */
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

    /**
     * 최근 N개 스냅샷 조회
     * 같은 기간당 1행만 존재하므로 reportDate desc, version desc 정렬로 최신 우선 조회
     * version은 tie-breaker 성격
     */
    List<Financial> findByStockOrderByReportDateDescVersionDesc(
            Stock stock,
            Pageable pageable
    );

    /**
     * 특정 periodNo 기준 시계열 조회
     * 예:
     * - Q + periodNo=1 => 매년 1분기만 비교
     * - H + periodNo=2 => 매년 하반기만 비교
     * - A + periodNo=1 => 사실상 연간 단일값
     */
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