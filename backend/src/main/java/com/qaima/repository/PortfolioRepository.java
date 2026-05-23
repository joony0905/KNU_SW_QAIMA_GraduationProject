package com.qaima.repository;

import com.qaima.domain.Portfolio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    @Query("""
        select distinct p from Portfolio p
        left join fetch p.holdings h
        where p.user.userId = :userId
        order by h.position asc
    """)
    Optional<Portfolio> findByUserIdWithHoldings(@Param("userId") Long userId);
}
