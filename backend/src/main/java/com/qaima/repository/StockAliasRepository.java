package com.qaima.repository;

import com.qaima.domain.StockAlias;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAliasRepository extends JpaRepository<StockAlias, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockAlias> findByNormalizedAlias(String normalizedAlias);

    List<StockAlias> findByStock_StockId(Long stockId);
}
