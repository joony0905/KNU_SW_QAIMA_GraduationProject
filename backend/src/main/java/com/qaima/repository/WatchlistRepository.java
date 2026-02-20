package com.qaima.repository;

import com.qaima.domain.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @Query("""
        select w from Watchlist w
        join fetch w.user u
        where w.watchlistId = :watchlistId
          and u.userId = :userId
    """)
    Optional<Watchlist> findOwnedByIdWithUser(@Param("watchlistId") Long watchlistId,
                                              @Param("userId") Long userId);

    List<Watchlist> findByUser_UserIdOrderByWatchlistIdAsc(Long userId);
}
