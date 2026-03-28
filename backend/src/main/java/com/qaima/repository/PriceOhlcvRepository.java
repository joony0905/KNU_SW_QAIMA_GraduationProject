package com.qaima.repository;

import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.PriceOhlcvId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface PriceOhlcvRepository extends JpaRepository<PriceOhlcv, PriceOhlcvId> {
    @Query("""
    select p
    from PriceOhlcv p
    join fetch p.stock s
    where s.stockCode in :stockCodes
      and p.id.freq = :freq
      and p.id.ts >= :from
      and p.id.ts < :to
    order by s.stockCode asc, p.id.ts asc
    """)
    List<PriceOhlcv> findRangeBulk(
            @Param("stockCodes") List<String> stockCodes,
            @Param("freq") Freq freq,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
    @Query("""
    select p
    from PriceOhlcv p
    where p.stock.stockCode = :stockCode
      and p.id.freq = :freq
      and p.id.ts >= :from
      and p.id.ts < :to
    order by p.id.ts
    """)
    List<PriceOhlcv> findRange(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    );

        @Query("""
        select p
        from PriceOhlcv p
        where p.stock.stockCode = :stockCode
          and p.id.freq = :freq
          and p.id.ts < :to
        order by p.id.ts desc
    """)
    List<PriceOhlcv> findBefore(
            String stockCode,
            Freq freq,
            OffsetDateTime to,
            Pageable pageable
    );
}
