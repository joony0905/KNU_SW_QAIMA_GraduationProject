package com.qaima.repository;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.domain.IndustryIndexOhlcvId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndustryIndexOhlcvRepository
        extends JpaRepository<IndustryIndexOhlcv, IndustryIndexOhlcvId> {

    @Query("""
    select o
    from IndustryIndexOhlcv o
    where o.industryIndex.indexId = :indexId
      and o.id.freq = :freq
    order by o.id.ts desc
    """)
    List<IndustryIndexOhlcv> findRecent(
            @Param("indexId") Long indexId,
            @Param("freq") Freq freq,
            Pageable pageable
    );
}
