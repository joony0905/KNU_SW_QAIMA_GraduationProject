package com.qaima.repository;

import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.qaima.domain.Exchange;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    //query에 industry 추가
    @Query("""
    select s from Stock s
    join fetch s.exchange e
    left join fetch s.industry i
    where s.stockCode = :stockCode
    """)
    Optional<Stock> findByStockCodeWithExchange(@Param("stockCode") String stockCode);
    Optional<Stock> findByExchangeAndStockCode(Exchange exchange, String stockCode);
}

