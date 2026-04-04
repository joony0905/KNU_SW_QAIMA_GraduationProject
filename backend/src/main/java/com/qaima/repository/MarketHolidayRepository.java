package com.qaima.repository;

import com.qaima.domain.MarketHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, Long> {

    List<MarketHoliday> findByMarketAndHolidayDateBetween(String market, LocalDate start, LocalDate end);

    boolean existsByMarketAndHolidayDate(String market, LocalDate date);
}
