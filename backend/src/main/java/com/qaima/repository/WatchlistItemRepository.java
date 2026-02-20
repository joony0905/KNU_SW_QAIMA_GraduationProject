package com.qaima.repository;

import java.util.List;
import java.util.Optional;
import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByWatchlist(Watchlist watchlist);

    @Query("""
        select wi from WatchlistItem wi
        join fetch wi.stock s
        join fetch s.exchange e
        left join fetch s.industry i
        join fetch wi.watchlist w
        join fetch w.user u
        where w.watchlistId = :watchlistId
          and u.userId = :userId
        order by wi.createdAt desc
    """)
    List<WatchlistItem> findOwnedItemsByWatchlistIdWithStock(@Param("watchlistId") Long watchlistId,
                                                             @Param("userId") Long userId);

    @Query("""
        select wi from WatchlistItem wi
        join fetch wi.stock s
        join fetch s.exchange e
        left join fetch s.industry i
        join fetch wi.watchlist w
        join fetch w.user u
        where wi.watchlistItemId = :itemId
          and u.userId = :userId
    """)
    Optional<WatchlistItem> findOwnedByIdWithAll(@Param("itemId") Long itemId,
                                                 @Param("userId") Long userId);
}
