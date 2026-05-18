package com.qaima.repository;

import com.qaima.domain.Stock;
import com.qaima.domain.StockInstitutionalHoldingQuarterly;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockInstitutionalHoldingQuarterlyRepository
        extends JpaRepository<StockInstitutionalHoldingQuarterly, Long> {

    @EntityGraph(attributePaths = "stock")
    Optional<StockInstitutionalHoldingQuarterly> findByStockAndReportPeriodAndSource(
            Stock stock,
            LocalDate reportPeriod,
            String source
    );

    @EntityGraph(attributePaths = "stock")
    Optional<StockInstitutionalHoldingQuarterly> findTopByStockAndReportPeriodLessThanAndSourceOrderByReportPeriodDesc(
            Stock stock,
            LocalDate reportPeriod,
            String source
    );

    @EntityGraph(attributePaths = "stock")
    List<StockInstitutionalHoldingQuarterly> findByStockAndSourceOrderByReportPeriodDesc(
            Stock stock,
            String source,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "stock")
    @Query("""
        select q
        from StockInstitutionalHoldingQuarterly q
        where q.stock.stockId in :stockIds
            and q.source = :source
    """)
    List<StockInstitutionalHoldingQuarterly> findByStockIdsAndSource(
            @Param("stockIds") Collection<Long> stockIds,
            @Param("source") String source
    );
}
