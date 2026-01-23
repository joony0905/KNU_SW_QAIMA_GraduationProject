package com.qaima.repository;

import com.qaima.domain.StockAlias;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAliasRepository extends JpaRepository<StockAlias, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockAlias> findByNormalizedAlias(String normalizedAlias);
}
