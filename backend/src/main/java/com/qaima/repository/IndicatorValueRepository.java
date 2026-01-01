package com.qaima.repository;

import com.qaima.domain.Freq;
import com.qaima.domain.IndicatorValue;
import com.qaima.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

// 기능1이 대부분 구현됐다 하면 정밀작업으로 슛
// IndicatorValue 엔티티에서 ts/freq 필드 이름 맞춰서 사용
public interface IndicatorValueRepository extends JpaRepository<IndicatorValue, Long> {

    List<IndicatorValue> findByStockAndFreqAndTsBetweenOrderByTs(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
