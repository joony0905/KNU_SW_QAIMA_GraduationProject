package com.qaima.repository;

import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.PriceOhlcvId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface PriceOhlcvRepository extends JpaRepository<PriceOhlcv, PriceOhlcvId> {

    @Query("""
        select p
        from PriceOhlcv p
        where p.stock.stockCode = :stockCode
          and p.id.freq = :freq
          and p.id.ts between :from and :to
        order by p.id.ts
        """) // 복합키 PK 레스고
    List<PriceOhlcv> findByStockCodeAndFreqAndTsBetween(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
