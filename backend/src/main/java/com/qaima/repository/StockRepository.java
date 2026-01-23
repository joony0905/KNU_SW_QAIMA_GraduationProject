package com.qaima.repository;

import com.qaima.domain.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.qaima.domain.Exchange;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where lower(s.stockCode) = lower(:stockCode)
    """) //TODO: Stock id 조회후 exchange 조인해서 거래상장소 확인 쿼리 예시입니다. db생성하시고 바꿀거있으면 바꿔주세요.
    List<Stock> findByStockCodeIgnoreCaseWithExchange(@Param("stockCode") String stockCode);
    Optional<Stock> findByExchangeAndStockCode(Exchange exchange, String stockCode);

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where lower(s.companyName) = lower(:companyName)
    """)
    List<Stock> findByCompanyNameWithExchangeIgnoreCase(@Param("companyName") String companyName);

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where lower(s.companyName) = lower(:companyName)
          and lower(e.code) = lower(:exchangeCode)
    """)
    List<Stock> findByCompanyNameAndExchangeCodeIgnoreCase(
            @Param("companyName") String companyName,
            @Param("exchangeCode") String exchangeCode
    );

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where lower(e.code) = lower(:exchangeCode)
          and lower(s.stockCode) = lower(:stockCode)
    """)
    Optional<Stock> findByExchangeCodeAndStockCodeIgnoreCase(
            @Param("exchangeCode") String exchangeCode,
            @Param("stockCode") String stockCode
    );

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findTop20ByCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(String keyword);

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findByCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(
            String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findByExchange_CodeIgnoreCaseAndCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(
            String exchangeCode,
            String keyword,
            Pageable pageable
    );
}
