package com.qaima.repository;

import com.qaima.domain.Watchlist;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    Optional<Watchlist> findByUser_UserIdAndName(Long userId, String name);

    @Query("""
        select w from Watchlist w
        join fetch w.user u
        where w.watchlistId = :watchlistId
    """)
    Optional<Watchlist> findByIdWithUser(@Param("watchlistId") Long watchlistId);
}
