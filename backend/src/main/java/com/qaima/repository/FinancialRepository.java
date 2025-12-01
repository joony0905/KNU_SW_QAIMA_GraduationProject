package com.qaima.repository;

import com.qaima.domain.Financial;
import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialRepository extends JpaRepository<Financial, Long> {

    // 최근 N개 재무제표 조회 (reportDate + version 기준)
    List<Financial> findTopByStockOrderByReportDateDescVersionDesc(Stock stock);
}
