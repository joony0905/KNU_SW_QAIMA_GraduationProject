package com.qaima.repository;

import com.qaima.domain.StockAlias;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAliasRepository extends JpaRepository<StockAlias, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockAlias> findByNormalizedAlias(String normalizedAlias);

    List<StockAlias> findByStock_StockId(Long stockId);

    boolean existsByStock_StockIdAndNormalizedAlias(Long stockId, String normalizedAlias);

    @Query("""
            select a from StockAlias a
            join fetch a.stock s
            join fetch s.exchange e
            where upper(e.code) in :exchangeCodes
            """)
    List<StockAlias> findByStockExchangeCodes(@Param("exchangeCodes") Collection<String> exchangeCodes);
}
