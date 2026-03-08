package com.qaima.repository;

import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    @Query("""
        select wi from WatchlistItem wi
        join fetch wi.stock s
        left join fetch s.industry i
        where wi.watchlist = :watchlist
    """)
    List<WatchlistItem> findByWatchlistWithStock(@Param("watchlist") Watchlist watchlist);

    @Query("""
        select wi from WatchlistItem wi
        join fetch wi.watchlist w
        join fetch w.user u
        join fetch wi.stock s
        left join fetch s.industry i
        where wi.watchlistItemId = :watchlistItemId
    """)
    Optional<WatchlistItem> findByIdWithRelations(@Param("watchlistItemId") Long watchlistItemId);

    boolean existsByWatchlistAndStock_StockId(Watchlist watchlist, Long stockId);
}
