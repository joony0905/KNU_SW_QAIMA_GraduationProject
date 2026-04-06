package com.qaima.repository;

import com.qaima.domain.ShortSelling;
import com.qaima.domain.Stock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortSellingRepository extends JpaRepository<ShortSelling, Long> {

    @EntityGraph(attributePaths = "stock")
    Optional<ShortSelling> findByStockAndReportDate(Stock stock, LocalDate reportDate);

    @EntityGraph(attributePaths = "stock")
    Optional<ShortSelling> findTopByStockOrderByReportDateDesc(Stock stock);

    @EntityGraph(attributePaths = "stock")
    Optional<ShortSelling> findTopByStockAndReportDateLessThanEqualOrderByReportDateDesc(
            Stock stock,
            LocalDate reportDate
    );

    @EntityGraph(attributePaths = "stock")
    List<ShortSelling> findByStockAndReportDateBetweenOrderByReportDateDesc(
            Stock stock,
            LocalDate fromDate,
            LocalDate toDate
    );

    @EntityGraph(attributePaths = "stock")
    List<ShortSelling> findByStockOrderByReportDateDesc(
            Stock stock,
            Pageable pageable
    );
}
