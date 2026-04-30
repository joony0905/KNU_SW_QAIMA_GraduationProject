package com.qaima.repository;

import com.qaima.domain.MarketInvestorFlow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInvestorFlowRepository extends JpaRepository<MarketInvestorFlow, Long> {

    Optional<MarketInvestorFlow> findByMarketCodeAndIndustryCodeAndTradeDateAndSource(
            String marketCode,
            String industryCode,
            LocalDate tradeDate,
            String source
    );

    Optional<MarketInvestorFlow> findTopByMarketCodeAndIndustryCodeAndTradeDateLessThanEqualOrderByTradeDateDesc(
            String marketCode,
            String industryCode,
            LocalDate tradeDate
    );

    List<MarketInvestorFlow> findByMarketCodeAndIndustryCodeAndTradeDateBetweenOrderByTradeDateAsc(
            String marketCode,
            String industryCode,
            LocalDate from,
            LocalDate to
    );

    List<MarketInvestorFlow> findByMarketCodeAndIndustryCodeOrderByTradeDateDesc(
            String marketCode,
            String industryCode,
            Pageable pageable
    );
}
