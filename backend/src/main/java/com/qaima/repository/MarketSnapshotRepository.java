package com.qaima.repository;

import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, Long> {

    Optional<MarketSnapshot> findByStockAndAsOfDate(Stock stock, LocalDate asOfDate);

    Optional<MarketSnapshot> findTopByStockOrderByAsOfDateDesc(Stock stock);

    Optional<MarketSnapshot> findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(Stock stock, LocalDate asOfDate);
}
