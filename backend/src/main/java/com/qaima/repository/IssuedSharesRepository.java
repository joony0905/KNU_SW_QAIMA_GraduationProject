package com.qaima.repository;

import com.qaima.domain.IssuedShares;
import com.qaima.domain.Stock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedSharesRepository extends JpaRepository<IssuedShares, Long> {

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findByStockAndBaseDateAndShareType(
            Stock stock,
            LocalDate baseDate,
            String shareType
    );

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findTopByStockOrderByBaseDateDesc(Stock stock);

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findTopByStockAndShareTypeOrderByBaseDateDesc(
            Stock stock,
            String shareType
    );

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findTopByStockAndShareTypeAndBaseDateLessThanEqualOrderByBaseDateDesc(
            Stock stock,
            String shareType,
            LocalDate baseDate
    );

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
            Stock stock,
            String shareType,
            LocalDate baseDate
    );

    @EntityGraph(attributePaths = "stock")
    Optional<IssuedShares> findTopByStockAndBaseDateLessThanEqualOrderByBaseDateDesc(
            Stock stock,
            LocalDate baseDate
    );

    @EntityGraph(attributePaths = "stock")
    List<IssuedShares> findByStockOrderByBaseDateDesc(Stock stock);
}
