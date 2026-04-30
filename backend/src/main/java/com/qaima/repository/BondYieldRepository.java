package com.qaima.repository;

import com.qaima.domain.BondYield;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondYieldRepository extends JpaRepository<BondYield, Long> {

    Optional<BondYield> findTopByInstrumentCodeAndCycleOrderByYieldDateDesc(
            String instrumentCode,
            String cycle
    );

    Optional<BondYield> findTopByInstrumentCodeAndCycleAndYieldDateLessThanEqualOrderByYieldDateDesc(
            String instrumentCode,
            String cycle,
            LocalDate yieldDate
    );

    Optional<BondYield> findBySourceAndInstrumentCodeAndCycleAndRawTime(
            String source,
            String instrumentCode,
            String cycle,
            String rawTime
    );

    List<BondYield> findByInstrumentCodeAndCycleAndYieldDateBetweenOrderByYieldDateAsc(
            String instrumentCode,
            String cycle,
            LocalDate from,
            LocalDate to
    );

    List<BondYield> findByInstrumentCodeAndCycleOrderByYieldDateDesc(
            String instrumentCode,
            String cycle,
            Pageable pageable
    );

    List<BondYield> findByCycleOrderByYieldDateDesc(
            String cycle,
            Pageable pageable
    );
}
