package com.qaima.repository;

import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.qaima.domain.Exchange;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where s.stockCode = :stockCode
    """) //TODO: Stock id 조회후 exchange 조인해서 거래상장소 확인 쿼리 예시입니다. db생성하시고 바꿀거있으면 바꿔주세요.
    Optional<Stock> findByStockCodeWithExchange(@Param("stockCode") String stockCode);
    Optional<Stock> findByExchangeAndStockCode(Exchange exchange, String stockCode);
}

