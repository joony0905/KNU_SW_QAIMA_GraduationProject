package com.qaima.repository;

import com.qaima.domain.ExchangeRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByPairCodeAndCycleAndRawTimeAndSource(
            String pairCode,
            String cycle,
            String rawTime,
            String source
    );

    Optional<ExchangeRate> findTopByPairCodeAndCycleAndRateDateLessThanEqualOrderByRateDateDesc(
            String pairCode,
            String cycle,
            LocalDate rateDate
    );

    List<ExchangeRate> findByPairCodeAndCycleAndRateDateBetweenOrderByRateDateAsc(
            String pairCode,
            String cycle,
            LocalDate from,
            LocalDate to
    );

    List<ExchangeRate> findByPairCodeAndCycleOrderByRateDateDesc(
            String pairCode,
            String cycle,
            Pageable pageable
    );
}
