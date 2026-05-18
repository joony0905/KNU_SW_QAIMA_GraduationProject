package com.qaima.repository;

import com.qaima.domain.Exchange;
import com.qaima.domain.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("""
    select s from Stock s
    join fetch s.exchange e
    left join fetch s.industry i
    left join fetch i.sector sec
    where s.stockId = :stockId
    """)
    Optional<Stock> findByIdWithExchangeAndIndustry(@Param("stockId") Long stockId);

    @Query("""
    select s from Stock s
    join fetch s.exchange e
    left join fetch s.industry i
    left join fetch i.sector sec
    where s.stockCode = :stockCode
    """)
    Optional<Stock> findByStockCodeWithExchangeAndIndustry(@Param("stockCode") String stockCode);

    @Query("""
    select s from Stock s
    join fetch s.exchange e
    left join fetch s.industry i
    where s.stockCode = :stockCode
    """)
    Optional<Stock> findByStockCodeWithExchange(@Param("stockCode") String stockCode);

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where lower(s.stockCode) = lower(:stockCode)
    """)
    List<Stock> findByStockCodeIgnoreCaseWithExchange(@Param("stockCode") String stockCode);

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

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(String exchangeCode);

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findAllByOrderByStockCodeAsc();

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findByDartCorpCodeIsNotNullOrderByStockCodeAsc();

    @EntityGraph(attributePaths = "exchange")
    List<Stock> findBySecCikIsNotNullOrderByStockCodeAsc();

    long countBySecCikIsNotNull();

    @Query("""
        select s from Stock s
        join fetch s.exchange e
        where s.secCik is not null
        order by
          case when s.secIssuedSharesSyncedAt is null then 0 else 1 end asc,
          s.secIssuedSharesSyncedAt asc,
          s.stockCode asc
    """)
    List<Stock> findSecIssuedSharesSyncTargets(Pageable pageable);

    @Query(value = """
        SELECT s.stock_code
        FROM stock s
        WHERE s.stock_code IS NOT NULL
          AND s.stock_code <> ''
        ORDER BY RAND()
        LIMIT :limit
        """, nativeQuery = true)
    List<String> findRandomStockCodes(@Param("limit") int limit);

    Optional<Stock> findByExchangeAndStockCode(Exchange exchange, String stockCode);
    List<Stock> findAllByIndustryIndustryId(Long industryId);
}
