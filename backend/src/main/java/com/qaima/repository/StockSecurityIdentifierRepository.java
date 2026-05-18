package com.qaima.repository;

import com.qaima.domain.StockSecurityIdentifier;
import com.qaima.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSecurityIdentifierRepository extends JpaRepository<StockSecurityIdentifier, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockSecurityIdentifier> findByIdentifierTypeAndIdentifierValueInAndActiveTrue(
            String identifierType,
            Collection<String> identifierValues
    );

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockSecurityIdentifier> findByIdentifierTypeAndActiveTrue(String identifierType);

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<StockSecurityIdentifier> findByIdentifierTypeAndIdentifierValueAndActiveTrue(
            String identifierType,
            String identifierValue
    );

    Optional<StockSecurityIdentifier> findByStockAndIdentifierTypeAndIdentifierValue(
            Stock stock,
            String identifierType,
            String identifierValue
    );
}
