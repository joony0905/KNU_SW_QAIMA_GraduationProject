package com.qaima.repository;

import com.qaima.domain.Stock;
import com.qaima.domain.StockInvestorFlow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockInvestorFlowRepository extends JpaRepository<StockInvestorFlow, Long> {

    Optional<StockInvestorFlow> findByStockAndTradeDateAndSource(
            Stock stock,
            LocalDate tradeDate,
            String source
    );

    Optional<StockInvestorFlow> findTopByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
            Stock stock,
            LocalDate tradeDate
    );

    List<StockInvestorFlow> findByStockAndTradeDateBetweenOrderByTradeDateAsc(
            Stock stock,
            LocalDate from,
            LocalDate to
    );

    List<StockInvestorFlow> findByStockOrderByTradeDateDesc(
            Stock stock,
            Pageable pageable
    );
}
