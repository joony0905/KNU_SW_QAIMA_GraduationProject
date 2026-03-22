package com.qaima.repository;

import com.qaima.domain.BaseRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseRateRepository extends JpaRepository<BaseRate, Long> {

    Optional<BaseRate> findTopByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
            String statCode,
            String itemCode,
            String cycle
    );

    Optional<BaseRate> findTopByStatCodeAndItemCodeAndCycleAndBaseDateLessThanEqualOrderByBaseDateDesc(
            String statCode,
            String itemCode,
            String cycle,
            LocalDate baseDate
    );

    Optional<BaseRate> findByStatCodeAndItemCodeAndCycleAndRawTime(
            String statCode,
            String itemCode,
            String cycle,
            String rawTime
    );

    List<BaseRate> findByStatCodeAndItemCodeAndCycleAndBaseDateBetweenOrderByBaseDateAsc(
            String statCode,
            String itemCode,
            String cycle,
            LocalDate from,
            LocalDate to
    );
}
